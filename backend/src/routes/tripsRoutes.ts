import { Router, Response } from 'express';
import { supabase } from '../lib/supabase.js';
import { requireAuth, AuthenticatedRequest } from '../middleware/requireAuth.js';

const router: Router = Router();

const MAX_TRIPS = 50;
const MAX_INCIDENTS = 200;
const MAX_LABEL_LENGTH = 120;
const MAX_DESCRIPTION_LENGTH = 160;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type IncidentInput = { description: string; time: string };

function authUserId(req: AuthenticatedRequest): number | null {
    const userId = Number(req.auth?.userId);
    return Number.isSafeInteger(userId) ? userId : null;
}

function readIncidents(raw: unknown): IncidentInput[] {
    if (!Array.isArray(raw)) return [];
    return raw
        .slice(0, MAX_INCIDENTS)
        .map((item) => {
            const row = (item ?? {}) as Record<string, unknown>;
            return {
                description: String(row.description ?? '').trim().slice(0, MAX_DESCRIPTION_LENGTH),
                time: String(row.time ?? '').trim().slice(0, 40),
            };
        })
        .filter((item) => item.description.length > 0);
}

router.get('/trips', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
        const userId = authUserId(req);
        if (userId === null) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const { data, error } = await supabase
            .from('trips')
            .select('id,date_label,total_duration,created_at,trip_incidents(id,description,time,created_at)')
            .eq('user_id', userId)
            .order('created_at', { ascending: false })
            .limit(MAX_TRIPS);

        if (error) {
            console.error('Trips fetch error:', error);
            return res.status(500).json({
                success: false,
                message: 'Failed to load trips. Please try again.',
            });
        }

        return res.json({ success: true, trips: data ?? [] });
    } catch (err: unknown) {
        console.error('Trips fetch exception:', err);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.post('/trips', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
        const userId = authUserId(req);
        if (userId === null) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const dateLabel = String(req.body.dateLabel ?? '').trim().slice(0, MAX_LABEL_LENGTH);
        const totalDuration = String(req.body.totalDuration ?? '').trim().slice(0, MAX_LABEL_LENGTH);
        const incidents = readIncidents(req.body.incidents);

        if (!dateLabel || !totalDuration) {
            return res.status(400).json({
                success: false,
                message: 'dateLabel and totalDuration are required.',
            });
        }

        const { data: tripRows, error: tripError } = await supabase
            .from('trips')
            .insert([{ user_id: userId, date_label: dateLabel, total_duration: totalDuration }])
            .select('id');

        if (tripError || !tripRows?.[0]?.id) {
            console.error('Trip insert error:', tripError);
            return res.status(500).json({
                success: false,
                message: 'Failed to save the trip. Please try again.',
            });
        }

        const tripId = tripRows[0].id as string;

        if (incidents.length > 0) {
            const { error: incidentError } = await supabase
                .from('trip_incidents')
                .insert(incidents.map((incident) => ({
                    trip_id: tripId,
                    description: incident.description,
                    time: incident.time,
                })));

            if (incidentError) {
                console.error('Trip incidents insert error:', incidentError);
                await supabase.from('trips').delete().eq('id', tripId);
                return res.status(500).json({
                    success: false,
                    message: 'Failed to save the trip incidents. Please try again.',
                });
            }
        }

        return res.status(201).json({
            success: true,
            message: 'Trip saved.',
            trip: { id: tripId, dateLabel, totalDuration, incidentCount: incidents.length },
        });
    } catch (err: unknown) {
        console.error('Trip insert exception:', err);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.delete('/trips/:id', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
        const userId = authUserId(req);
        if (userId === null) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const tripId = String(req.params.id ?? '');
        if (!UUID_PATTERN.test(tripId)) {
            return res.status(400).json({ success: false, message: 'Invalid trip id.' });
        }

        const { data: owned, error: lookupError } = await supabase
            .from('trips')
            .select('id')
            .eq('id', tripId)
            .eq('user_id', userId)
            .maybeSingle();

        if (lookupError) {
            console.error('Trip lookup error:', lookupError);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }
        if (!owned) {
            return res.status(404).json({ success: false, message: 'Trip not found.' });
        }

        const { error: incidentError } = await supabase
            .from('trip_incidents')
            .delete()
            .eq('trip_id', tripId);

        if (incidentError) {
            console.error('Trip incidents delete error:', incidentError);
            return res.status(500).json({ success: false, message: 'Failed to delete the trip.' });
        }

        const { error: tripError } = await supabase
            .from('trips')
            .delete()
            .eq('id', tripId)
            .eq('user_id', userId);

        if (tripError) {
            console.error('Trip delete error:', tripError);
            return res.status(500).json({ success: false, message: 'Failed to delete the trip.' });
        }

        return res.json({ success: true, message: 'Trip deleted.' });
    } catch (err: unknown) {
        console.error('Trip delete exception:', err);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

export default router;
