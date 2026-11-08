import { Router, Request, Response } from 'express';
import { supabase } from '../lib/supabase.js';
import { requireAuth, AuthenticatedRequest } from '../middleware/requireAuth.js';

const router: Router = Router();

const DETECTION_TYPE_MAX_LENGTH = 64;
const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

router.get('/detections', requireAuth, async (req: AuthenticatedRequest, res: Response): Promise<Response> => {
    try {
        const userId = Number(req.auth?.userId);
        if (!Number.isSafeInteger(userId)) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const sinceRaw = Number(req.query.since);
        const limitRaw = Number(req.query.limit);
        const limit = Number.isSafeInteger(limitRaw)
            ? Math.min(Math.max(limitRaw, 1), MAX_LIMIT)
            : DEFAULT_LIMIT;

        let query = supabase
            .from('detections')
            .select('id,detection_type,confidence,device_timestamp,created_at')
            .eq('user_id', userId);

        if (Number.isSafeInteger(sinceRaw) && sinceRaw > 0) {
            query = query.gte('device_timestamp', sinceRaw);
        }

        const { data, error } = await query
            .order('device_timestamp', { ascending: false })
            .limit(limit);

        if (error) {
            console.error('Detections fetch error:', error);
            return res.status(500).json({
                success: false,
                message: 'Failed to load detections. Please try again.',
            });
        }

        return res.json({
            success: true,
            count: data?.length ?? 0,
            detections: data ?? [],
        });
    } catch (err: unknown) {
        console.error('Detections fetch exception:', err);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.post('/detections', async (req: Request, res: Response): Promise<Response> => {
    try {
        const userId = Number(req.body.userId);
        const detectionType = String(req.body.detectionType ?? '').trim();
        const confidence = Number(req.body.confidence);
        const deviceTimestamp = Number(req.body.deviceTimestamp);

        if (!Number.isSafeInteger(userId)) {
            return res.status(400).json({ success: false, message: 'A valid userId is required.' });
        }
        if (!detectionType) {
            return res.status(400).json({ success: false, message: 'detectionType is required.' });
        }
        if (detectionType.length > DETECTION_TYPE_MAX_LENGTH) {
            return res.status(400).json({
                success: false,
                message: `detectionType must be ${DETECTION_TYPE_MAX_LENGTH} characters or less.`,
            });
        }
        if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1) {
            return res.status(400).json({ success: false, message: 'confidence must be between 0 and 1.' });
        }

        const { data: user, error: userError } = await supabase
            .from('users')
            .select('id')
            .eq('id', userId)
            .maybeSingle();

        if (userError) {
            console.error('Detections user lookup error:', userError);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found.' });
        }

        const { data, error } = await supabase
            .from('detections')
            .insert([{
                user_id: userId,
                detection_type: detectionType,
                confidence,
                device_timestamp: Number.isSafeInteger(deviceTimestamp) ? deviceTimestamp : null,
            }])
            .select('id, created_at');

        if (error) {
            console.error('Detections insert error:', error);
            return res.status(500).json({
                success: false,
                message: 'Failed to log the detection. Please try again.',
            });
        }

        return res.status(201).json({
            success: true,
            message: 'Detection logged.',
            detection: data?.[0] ?? null,
        });
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'An unexpected error occurred';
        console.error('Detections exception error:', err);
        return res.status(500).json({ success: false, message: 'Server error: ' + message });
    }
});

export default router;
