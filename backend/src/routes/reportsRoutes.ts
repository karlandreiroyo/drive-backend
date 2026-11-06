import { Router, Request, Response } from 'express';
import { supabase } from '../lib/supabase.js';

const router: Router = Router();

const SUBJECT_MAX_LENGTH = 200;
const DESCRIPTION_MAX_LENGTH = 5000;

router.post('/reports', async (req: Request, res: Response): Promise<Response> => {
    try {
        const userId = Number(req.body.userId);
        const subject = String(req.body.subject ?? '').trim();
        const description = String(req.body.description ?? '').trim();

        if (!Number.isSafeInteger(userId)) {
            return res.status(400).json({ success: false, message: 'A valid userId is required.' });
        }
        if (!subject) {
            return res.status(400).json({ success: false, message: 'Subject is required.' });
        }
        if (!description) {
            return res.status(400).json({ success: false, message: 'Description is required.' });
        }
        if (subject.length > SUBJECT_MAX_LENGTH) {
            return res.status(400).json({
                success: false,
                message: `Subject must be ${SUBJECT_MAX_LENGTH} characters or less.`,
            });
        }
        if (description.length > DESCRIPTION_MAX_LENGTH) {
            return res.status(400).json({
                success: false,
                message: `Description must be ${DESCRIPTION_MAX_LENGTH} characters or less.`,
            });
        }

        const { data: user, error: userError } = await supabase
            .from('users')
            .select('id')
            .eq('id', userId)
            .maybeSingle();

        if (userError) {
            console.error('Reports user lookup error:', userError);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found.' });
        }

        const { data, error } = await supabase
            .from('reports')
            .insert([{ user_id: userId, subject, description }])
            .select('id, status, created_at');

        if (error) {
            console.error('Reports insert error:', error);
            return res.status(500).json({
                success: false,
                message: 'Failed to submit the report. Please try again.',
            });
        }

        return res.status(201).json({
            success: true,
            message: 'Report submitted successfully.',
            report: data?.[0] ?? null,
        });
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'An unexpected error occurred';
        console.error('Reports exception error:', err);
        return res.status(500).json({ success: false, message: 'Server error: ' + message });
    }
});

export default router;
