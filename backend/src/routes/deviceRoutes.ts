import { Router, Request, Response } from 'express';
import { env } from '../config/env.js';
import { requireAuth, AuthenticatedRequest } from '../middleware/requireAuth.js';

const router: Router = Router();

// A heartbeat is ephemeral ("is the node online right now?"), so the latest
// status per user lives in memory rather than a database table. If the server
// restarts the map is empty and the app shows DISCONNECTED until the next
// heartbeat (~2 s) arrives, which is the correct behaviour.
interface DeviceStatus {
    userId: string;
    esp32Connected: boolean;
    alertActive: boolean;
    lastDetectionType: string | null;
    lastConfidence: number;
    firmware: string | null;
    lastSeen: number;
}

const latestByUser = new Map<string, DeviceStatus>();

// The Pi heartbeats every ~2 s; treat the node as offline after ~3 missed beats.
const STALE_AFTER_MS = 7000;
const FIELD_MAX_LENGTH = 64;

// Pi -> backend. Protected by a shared secret (DEVICE_API_KEY) so arbitrary
// callers cannot spoof a driver's hardware status. Keyed by the user's UUID.
router.post('/device/heartbeat', (req: Request, res: Response): Response => {
    if (!env.deviceApiKey) {
        return res.status(503).json({
            success: false,
            message: 'Device bridge is not configured (DEVICE_API_KEY missing).',
        });
    }

    const providedKey = req.headers['x-device-key'];
    if (providedKey !== env.deviceApiKey) {
        return res.status(401).json({ success: false, message: 'Invalid device key.' });
    }

    const userId = String(req.body.userId ?? '').trim();
    if (!userId) {
        return res.status(400).json({ success: false, message: 'userId is required.' });
    }

    const esp32Connected = req.body.esp32Connected === true;
    const alertActive = req.body.alertActive === true;

    const rawType = String(req.body.lastDetectionType ?? '').trim();
    const lastDetectionType = rawType ? rawType.slice(0, FIELD_MAX_LENGTH) : null;

    const rawConfidence = Number(req.body.lastConfidence);
    const lastConfidence = Number.isFinite(rawConfidence)
        ? Math.min(Math.max(rawConfidence, 0), 1)
        : 0;

    const rawFirmware = String(req.body.firmware ?? '').trim();
    const firmware = rawFirmware ? rawFirmware.slice(0, FIELD_MAX_LENGTH) : null;

    latestByUser.set(userId, {
        userId,
        esp32Connected,
        alertActive,
        lastDetectionType,
        lastConfidence,
        firmware,
        lastSeen: Date.now(),
    });

    return res.json({ success: true });
});

// Phone -> backend. The logged-in driver reads only their own node's status.
router.get('/device/status', requireAuth, (req: AuthenticatedRequest, res: Response): Response => {
    const userId = String(req.auth?.userId ?? '').trim();
    if (!userId) {
        return res.status(401).json({ success: false, message: 'Authentication required.' });
    }

    const entry = latestByUser.get(userId);
    const online = !!entry && Date.now() - entry.lastSeen <= STALE_AFTER_MS;

    return res.json({
        success: true,
        online,
        device: online && entry
            ? {
                esp32Connected: entry.esp32Connected,
                alertActive: entry.alertActive,
                lastDetectionType: entry.lastDetectionType,
                lastConfidence: entry.lastConfidence,
                firmware: entry.firmware,
                lastSeen: entry.lastSeen,
            }
            : null,
    });
});

export default router;