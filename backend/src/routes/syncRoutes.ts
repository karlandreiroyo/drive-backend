import { Router, Response } from 'express';
import { supabase } from '../lib/supabase.js';
import { requireAuth, AuthenticatedRequest } from '../middleware/requireAuth.js';
import {
    EmergencyIncident,
    isEmergencyEmailConfigured,
    sendEmergencyIncidentEmail,
} from '../lib/emailService.js';

const router: Router = Router();

const MAX_TRIPS = 100;
const MAX_INCIDENTS = 300;
const MAX_DELETES = 100;
const MAX_DESCRIPTION = 160;
const MAX_SHORT_TEXT = 64;
const MAX_LABEL = 120;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CRITICAL_CONFIDENCE = 0.75;
const CRITICAL_TYPES = ['microsleep', 'drowsiness', 'sleep', 'fatigue', 'eye'];

type TripRow = {
    id: string;
    user_id: string;
    start_time: string;
    end_time: string | null;
    status: string;
    origin: string;
    destination: string;
    created_at: string;
};

type IncidentRow = {
    id: string;
    trip_id: string;
    user_id: string;
    description: string;
    time: string;
    incident_type: string;
    confidence: number;
    device_timestamp: number;
    is_synced: boolean;
    created_at: string;
};

function clampText(value: unknown, max: number): string {
    return String(value ?? '').trim().slice(0, max);
}

function readTimestamp(value: unknown): string | null {
    const raw = typeof value === 'string' ? value.trim() : '';
    if (!raw) return null;
    const parsed = new Date(raw);
    return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

function readTrip(raw: unknown): Omit<TripRow, 'user_id'> | null {
    const row = (raw ?? {}) as Record<string, unknown>;
    const id = String(row.id ?? '');
    if (!UUID_PATTERN.test(id)) return null;

    const startTime = readTimestamp(row.start_time);
    if (!startTime) return null;

    return {
        id,
        start_time: startTime,
        end_time: readTimestamp(row.end_time),
        status: clampText(row.status, 32) || 'COMPLETED',
        origin: clampText(row.origin, MAX_LABEL) || 'Current location',
        destination: clampText(row.destination, MAX_LABEL) || 'Destination',
        created_at: readTimestamp(row.created_at) ?? startTime,
    };
}

function readIncident(raw: unknown): Omit<IncidentRow, 'user_id'> | null {
    const row = (raw ?? {}) as Record<string, unknown>;
    const id = String(row.id ?? '');
    const tripId = String(row.trip_id ?? '');
    if (!UUID_PATTERN.test(id) || !UUID_PATTERN.test(tripId)) return null;

    const confidenceRaw = Number(row.confidence);
    const confidence = Number.isFinite(confidenceRaw)
        ? Math.min(Math.max(confidenceRaw, 0), 1)
        : 0;

    const timestampRaw = Number(row.device_timestamp);
    const deviceTimestamp = Number.isSafeInteger(timestampRaw) && timestampRaw > 0
        ? timestampRaw
        : Date.now();

    return {
        id,
        trip_id: tripId,
        description: clampText(row.description, MAX_DESCRIPTION) || 'Detection',
        time: clampText(row.time, 40),
        incident_type: clampText(row.incident_type, MAX_SHORT_TEXT) || 'Detection',
        confidence,
        device_timestamp: deviceTimestamp,
        is_synced: true,
        created_at: readTimestamp(row.created_at) ?? new Date(deviceTimestamp).toISOString(),
    };
}

function isCritical(incidentType: string, confidence: number): boolean {
    if (confidence >= CRITICAL_CONFIDENCE) return true;
    const normalized = incidentType.toLowerCase();
    return CRITICAL_TYPES.some((type) => normalized.includes(type));
}

router.post('/sync', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
        // Ownership always comes from the token, never from the payload.
        const userId = String(req.auth?.userId ?? '').trim();
        if (!userId) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const body = (req.body ?? {}) as Record<string, unknown>;
        const rawTrips = Array.isArray(body.trips) ? body.trips.slice(0, MAX_TRIPS) : [];
        const rawIncidents = Array.isArray(body.incidents) ? body.incidents.slice(0, MAX_INCIDENTS) : [];
        const rawDeletes = Array.isArray(body.deleteTripIds) ? body.deleteTripIds.slice(0, MAX_DELETES) : [];

        const deleteIds = rawDeletes
            .map((value) => String(value))
            .filter((id) => UUID_PATTERN.test(id));
        const deleteSet = new Set(deleteIds);

        if (deleteIds.length > 0) {
            const { error: incidentDeleteError } = await supabase
                .from('trip_incidents')
                .delete()
                .in('trip_id', deleteIds)
                .eq('user_id', userId);

            if (incidentDeleteError) {
                console.error('[sync] Incident delete error:', incidentDeleteError);
                return res.status(500).json({ success: false, message: 'Failed to delete the requested trips.' });
            }

            const { error: tripDeleteError } = await supabase
                .from('trips')
                .delete()
                .in('id', deleteIds)
                .eq('user_id', userId);

            if (tripDeleteError) {
                console.error('[sync] Trip delete error:', tripDeleteError);
                return res.status(500).json({ success: false, message: 'Failed to delete the requested trips.' });
            }
        }

        const tripRows: TripRow[] = rawTrips
            .map(readTrip)
            .filter((row): row is Omit<TripRow, 'user_id'> => row !== null && !deleteSet.has(row.id))
            .map((row) => ({ ...row, user_id: userId }));

        if (tripRows.length > 0) {
            const { error: tripError } = await supabase
                .from('trips')
                .upsert(tripRows, { onConflict: 'id' });

            if (tripError) {
                console.error('[sync] Trip upsert error:', tripError);
                return res.status(500).json({ success: false, message: 'Failed to sync offline trips.' });
            }
        }

        // Incidents can arrive after their trip was synced in an earlier batch,
        // so accept any trip this user owns, not just the ones in this payload.
        const candidateIncidents = rawIncidents
            .map(readIncident)
            .filter((row): row is Omit<IncidentRow, 'user_id'> => row !== null);

        const lookupTripIds = [...new Set([
            ...tripRows.map((row) => row.id),
            ...candidateIncidents.map((row) => row.trip_id),
        ])];

        const allowedTripIds = new Set<string>();
        if (lookupTripIds.length > 0) {
            const { data: ownedTrips, error: ownedError } = await supabase
                .from('trips')
                .select('id')
                .eq('user_id', userId)
                .in('id', lookupTripIds);

            if (ownedError) {
                console.error('[sync] Trip ownership lookup error:', ownedError);
                return res.status(500).json({ success: false, message: 'Failed to verify the offline trips.' });
            }

            (ownedTrips ?? []).forEach((row: { id: string }) => allowedTripIds.add(String(row.id)));
        }

        const incidentRows: IncidentRow[] = candidateIncidents
            .filter((row) => allowedTripIds.has(row.trip_id))
            .map((row) => ({ ...row, user_id: userId }));

        if (incidentRows.length > 0) {
            const { error: incidentError } = await supabase
                .from('trip_incidents')
                .upsert(incidentRows, { onConflict: 'id' });

            if (incidentError) {
                console.error('[sync] Incident upsert error:', incidentError);
                return res.status(500).json({ success: false, message: 'Failed to sync the offline incidents.' });
            }
        }

        const emailSentIds = await dispatchEmergencyEmail(userId);

        return res.json({
            success: true,
            message: 'Offline data synced.',
            tripsSynced: tripRows.map((row) => row.id),
            incidentsSynced: incidentRows.map((row) => row.id),
            emailSentIds,
            deletedTripIds: deleteIds,
        });
    } catch (err: unknown) {
        console.error('[sync] Unexpected error:', err);
        return res.status(500).json({ success: false, message: 'Server error while syncing offline data.' });
    }
});

/**
 * Picks up every critical incident that has not triggered an email yet, so a
 * Brevo outage on one sync is retried on the next worker run.
 */
async function dispatchEmergencyEmail(userId: string): Promise<string[]> {
    if (!isEmergencyEmailConfigured()) return [];

    const criticalTypes = CRITICAL_TYPES
        .map((type) => `incident_type.ilike.*${type}*`)
        .join(',');

    const { data: pending, error } = await supabase
        .from('trip_incidents')
        .select('id,incident_type,confidence,description,time')
        .eq('user_id', userId)
        .eq('email_sent', false)
        .or(`confidence.gte.${CRITICAL_CONFIDENCE},${criticalTypes}`)
        .order('device_timestamp', { ascending: true })
        .limit(100);

    if (error) {
        console.error('[sync] Pending email lookup error:', error);
        return [];
    }

    const critical = (pending ?? []).filter((row: Record<string, unknown>) =>
        isCritical(String(row.incident_type ?? ''), Number(row.confidence ?? 0))
    );

    if (critical.length === 0) return [];

    const { data: userRow, error: userError } = await supabase
        .from('users')
        .select('email,full_name')
        .eq('id', userId)
        .maybeSingle();

    if (userError) {
        console.error('[sync] Emergency recipient lookup error:', userError);
        return [];
    }

    const recipient = String(userRow?.email ?? '').trim();
    if (!recipient) {
        console.warn('[sync] No account email on file; emergency email skipped.');
        return [];
    }

    const incidents: EmergencyIncident[] = critical.map((row: Record<string, unknown>) => ({
        incidentType: String(row.incident_type ?? 'Detection'),
        confidence: Number(row.confidence ?? 0),
        description: String(row.description ?? ''),
        time: String(row.time ?? ''),
    }));

    const sent = await sendEmergencyIncidentEmail(recipient, userRow?.full_name ?? null, incidents);
    if (!sent) return [];

    const emailSentIds = critical.map((row: Record<string, unknown>) => String(row.id));
    const { error: markError } = await supabase
        .from('trip_incidents')
        .update({ email_sent: true })
        .in('id', emailSentIds);

    if (markError) {
        console.error('[sync] Failed to mark incidents as emailed:', markError);
        return [];
    }

    return emailSentIds;
}

export default router;
