import { Router } from 'express';
import bcrypt from 'bcrypt';
import { supabase } from '../lib/supabase.js';
import { createAndSendOtp, checkCode, isSmsConfigured, OtpPurpose } from './otpService.js';
import { passwordError, resolveIdentifier, ResolvedIdentifier } from '../lib/validators.js';
import { publicUser } from '../lib/jwt.js';
import { requireAuth, AuthenticatedRequest } from '../middleware/requireAuth.js';

const router = Router();

const isPurpose = (p: unknown): p is OtpPurpose => p === 'signup' || p === 'reset';

type UserRow = {
    id: string | number;
    email: string;
    phone_number?: string | null;
    is_verified?: boolean;
};

function readIdentifier(body: Record<string, unknown>): string {
    const raw = body.identifier ?? body.email ?? body.phoneNumber ?? '';
    return String(raw).trim();
}

async function findUserByIdentifier(
    resolved: ResolvedIdentifier
): Promise<{ user: UserRow | null; error: string | null }> {
    if (resolved.kind === 'email') {
        const { data, error } = await supabase
            .from('users')
            .select('*')
            .eq('email', resolved.identifier)
            .maybeSingle();
        if (error) return { user: null, error: error.message };
        return { user: (data as UserRow | null) ?? null, error: null };
    }

    const { data, error } = await supabase
        .from('users')
        .select('*')
        .in('phone_number', resolved.lookupValues)
        .limit(2);
    if (error) return { user: null, error: error.message };
    if (!data || data.length === 0) return { user: null, error: null };
    return { user: data[0] as UserRow, error: null };
}

router.post('/verify-otp', async (req, res) => {
    try {
        const resolved = resolveIdentifier(readIdentifier(req.body));
        const code = String(req.body.code ?? '').trim();
        const purpose = req.body.purpose;

        if (!resolved || !/^\d{6}$/.test(code) || !isPurpose(purpose)) {
            return res.status(400).json({ success: false, message: 'Invalid request.' });
        }

        if (purpose === 'signup' && resolved.kind === 'phone') {
            return res.status(400).json({
                success: false,
                message: 'Signup codes are sent to your email address.',
            });
        }

        const result = await checkCode(resolved.identifier, code, purpose, purpose === 'signup');
        if (!result.ok) return res.status(result.status).json({ success: false, message: result.message });

        if (purpose === 'signup') {
            const { data, error } = await supabase
                .from('users')
                .update({ is_verified: true })
                .eq('email', resolved.identifier)
                .select('id');

            if (error) {
                console.error('Verify user error:', error);
                return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
            }

            if (!data || data.length === 0) {
                console.warn(`[verify-otp] No user found for email ${resolved.identifier}`);
            }
        }

        return res.json({
            success: true,
            message: purpose === 'signup' ? 'Email verified successfully!' : 'Code verified.',
        });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.post('/resend-otp', async (req, res) => {
    try {
        const resolved = resolveIdentifier(readIdentifier(req.body));
        const purpose = req.body.purpose;

        if (!resolved || !isPurpose(purpose)) {
            return res.status(400).json({ success: false, message: 'Invalid request.' });
        }

        if (purpose === 'signup' && resolved.kind === 'phone') {
            return res.status(400).json({
                success: false,
                message: 'Signup codes are sent to your email address.',
            });
        }

        if (resolved.kind === 'phone' && !isSmsConfigured()) {
            return res.status(503).json({
                success: false,
                message: 'SMS delivery is not configured. Please use your email instead.',
            });
        }

        const { user } = await findUserByIdentifier(resolved);
        const generic = { success: true, message: 'If eligible, a new code has been sent.' };
        if (!user) return res.json(generic);
        if (purpose === 'signup' && user.is_verified) return res.json(generic);

        const channel = resolved.kind === 'phone' ? 'sms' : 'email';
        const r = await createAndSendOtp(resolved.identifier, purpose, channel);
        if (!r.ok) {
            return res.status(r.retryAfter ? 429 : 500).json({ success: false, message: r.message });
        }
        return res.json({ success: true, message: r.message });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.post('/forgot-password', async (req, res) => {
    try {
        const raw = readIdentifier(req.body);
        if (!raw) {
            return res.status(400).json({ success: false, message: 'Enter your email or phone number.' });
        }

        const resolved = resolveIdentifier(raw);
        if (!resolved) {
            return res.status(400).json({
                success: false,
                message: 'Enter a valid email address or PH mobile number (e.g. 09171234567).',
            });
        }

        if (resolved.kind === 'phone' && !isSmsConfigured()) {
            return res.status(503).json({
                success: false,
                message: 'SMS password reset is not available right now. Please use your email instead.',
            });
        }

        const { user, error } = await findUserByIdentifier(resolved);
        if (error) {
            console.error('Forgot password lookup error:', error);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }

        const channel = resolved.kind === 'phone' ? 'sms' : 'email';
        const genericMessage = `If that ${resolved.kind === 'phone' ? 'number' : 'email'} is registered, a code has been sent.`;

        if (!user) return res.json({ success: true, message: genericMessage });

        const r = await createAndSendOtp(resolved.identifier, 'reset', channel);
        if (!r.ok) {
            if (r.retryAfter) {
                return res.status(429).json({ success: false, message: r.message });
            }
            console.error('Forgot password OTP error:', r.message);
            return res.status(500).json({ success: false, message: r.message });
        }

        return res.json({ success: true, message: genericMessage });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.post('/reset-password', async (req, res) => {
    try {
        const resolved = resolveIdentifier(readIdentifier(req.body));
        const code = String(req.body.code ?? '').trim();
        const newPassword = String(req.body.newPassword ?? '');

        if (!resolved || !/^\d{6}$/.test(code)) {
            return res.status(400).json({ success: false, message: 'Invalid request.' });
        }

        const pwErr = passwordError(newPassword);
        if (pwErr) return res.status(400).json({ success: false, message: pwErr });

        const result = await checkCode(resolved.identifier, code, 'reset', true);
        if (!result.ok) return res.status(result.status).json({ success: false, message: result.message });

        const { user, error: lookupError } = await findUserByIdentifier(resolved);
        if (lookupError) {
            console.error('Reset password lookup error:', lookupError);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }
        if (!user) {
            return res.status(400).json({
                success: false,
                message: 'No account found for that email or phone number.',
            });
        }

        const hashed = await bcrypt.hash(newPassword, 10);
        const { data, error } = await supabase
            .from('users')
            .update({ password: hashed })
            .eq('id', user.id)
            .select('id');

        if (error) {
            console.error('Reset password error:', error);
            return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
        }
        if (!data || data.length === 0) {
            return res.status(404).json({ success: false, message: 'No account found for that email or phone number.' });
        }

        return res.json({ success: true, message: 'Password updated. You can now log in.' });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

router.get('/me', requireAuth, async (req: AuthenticatedRequest, res) => {
    try {
        const email = req.auth?.email;
        if (!email) {
            return res.status(401).json({ success: false, message: 'Authentication required.' });
        }

        const { data: user, error } = await supabase
            .from('users')
            .select('*')
            .eq('email', email)
            .maybeSingle();

        if (error) {
            console.error('Profile query error:', error);
            return res.status(500).json({ success: false, message: 'Database error occurred.' });
        }
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found.' });
        }

        return res.json({ success: true, user: publicUser(user) });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'Server error. Please try again.' });
    }
});

export default router;
