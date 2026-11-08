import { Router } from 'express';
import bcrypt from 'bcrypt';
import { supabase } from '../lib/supabase.js';
import { env } from '../config/env.js';
import { createAndSendOtp, checkCode, isSmsConfigured, OtpPurpose } from './otpService.js';
import { passwordError, resolveIdentifier, ResolvedIdentifier } from '../lib/validators.js';
import { publicUser, signResetToken, verifyResetToken } from '../lib/jwt.js';
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

// PostgREST passes the operand of `ilike` straight to SQL LIKE, so wildcards
// present in the address itself must be escaped to keep the match literal.
const escapeLikePattern = (value: string) => value.replace(/[\\%_]/g, (m) => `\\${m}`);

async function findUserByIdentifier(
    resolved: ResolvedIdentifier
): Promise<{ user: UserRow | null; error: string | null }> {
    if (resolved.kind === 'email') {
        const { data, error } = await supabase
            .from('users')
            .select('*')
            .ilike('email', escapeLikePattern(resolved.identifier))
            .limit(2);
        if (error) return { user: null, error: error.message };
        if (!data || data.length === 0) {
            return { user: null, error: null };
        }
        if (data.length > 1) {
            console.warn(`[users] Multiple rows match email ${resolved.identifier}; using id ${data[0].id}.`);
        }
        return { user: data[0] as UserRow, error: null };
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
            // Canonical form of what was verified, so the client never has to
            // guess how the user typed the address.
            identifier: resolved.identifier,
            // Proof of verification for the reset flow; the client sends this
            // back to /reset-password instead of the identifier + code.
            resetToken: purpose === 'reset' ? signResetToken(resolved.identifier) : undefined,
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
        const newPassword = String(req.body.newPassword ?? '');
        const resetToken = String(req.body.resetToken ?? '').trim();
        const code = String(req.body.code ?? '').trim();

        console.log(
            `[reset-password] payload: identifier="${readIdentifier(req.body)}", ` +
            `codeLength=${code.length}, hasResetToken=${Boolean(resetToken)}, ` +
            `passwordLength=${newPassword.length}`
        );

        const pwErr = passwordError(newPassword);
        if (pwErr) {
            return res.status(400).json({ success: false, code: 'WEAK_PASSWORD', message: pwErr });
        }

        let resolved: ResolvedIdentifier | null = null;

        if (resetToken) {
            // Preferred path: the OTP was already verified and signed into a
            // short-lived token, so no verification_codes lookup can fail here.
            try {
                resolved = resolveIdentifier(verifyResetToken(resetToken).identifier);
            } catch {
                console.warn('[reset-password] Reset token is invalid or expired.');
                return res.status(401).json({
                    success: false,
                    code: 'RESET_TOKEN_EXPIRED',
                    message: 'Your reset session has expired. Please verify a new code.',
                });
            }
            if (!resolved) {
                return res.status(400).json({
                    success: false,
                    code: 'INVALID_IDENTIFIER',
                    message: 'The verified email or phone number is not valid.',
                });
            }
            console.log(`[reset-password] Using verified reset token (${resolved.kind}).`);
        } else {
            // Legacy path for older app builds that only send identifier + code.
            resolved = resolveIdentifier(readIdentifier(req.body));
            if (!resolved || !/^\d{6}$/.test(code)) {
                console.warn(
                    `[reset-password] Missing reset credentials: ` +
                    `identifier="${readIdentifier(req.body)}", codeLength=${code.length}.`
                );
                return res.status(400).json({
                    success: false,
                    code: 'MISSING_RESET_CREDENTIALS',
                    message: 'Your verification expired. Please request a new code.',
                });
            }

            const result = await checkCode(resolved.identifier, code, 'reset', true);
            if (!result.ok) {
                return res.status(result.status).json({
                    success: false,
                    code: 'CODE_REJECTED',
                    message: result.message,
                });
            }
        }

        console.log(
            `[reset-password] Resolved ${resolved.kind} "${resolved.identifier}" ` +
            `via ${resetToken ? 'reset token' : 'identifier + code'}.`
        );

        const { user, error: lookupError } = await findUserByIdentifier(resolved);
        if (lookupError) {
            console.error('[reset-password] User lookup failed:', lookupError);
            return res.status(500).json({
                success: false,
                code: 'USER_LOOKUP_FAILED',
                message: 'Could not read the account. Please try again.',
            });
        }
        if (!user) {
            console.warn(`[reset-password] No user row matches ${resolved.kind} "${resolved.identifier}".`);
            return res.status(404).json({
                success: false,
                code: 'ACCOUNT_NOT_FOUND',
                message: 'No account found for that email or phone number.',
            });
        }

        console.log(`[reset-password] Updating password for user ${user.id} (${user.email}).`);

        const hashed = await bcrypt.hash(newPassword, 10);
        const { data, error } = await supabase
            .from('users')
            .update({ password: hashed })
            .eq('id', user.id)
            .select('id');

        if (error) {
            console.error(`[reset-password] Update failed for user ${user.id}:`, error);
            return res.status(500).json({
                success: false,
                code: 'DB_UPDATE_ERROR',
                message: 'Could not update the password. Please try again.',
            });
        }
        if (!data || data.length === 0) {
            // `select('id')` after an update can come back empty when the
            // RETURNING clause is filtered by RLS even though the write landed,
            // so confirm against the stored hash before reporting failure.
            const { data: saved } = await supabase
                .from('users')
                .select('password')
                .eq('id', user.id)
                .maybeSingle();

            if (saved?.password === hashed) {
                return res.json({ success: true, message: 'Password updated. You can now log in.' });
            }

            // The row was just read successfully and Supabase raised no error,
            // so zero affected rows means the request ran without write
            // permission (anon key or a restrictive RLS policy), not that the
            // account is missing.
            console.error(
                `[reset-password] UPDATE affected 0 rows for user ${user.id}. ` +
                `Supabase key role in use: ${env.supabaseKeyRole ?? 'unknown (non-JWT key)'}. ` +
                'Set SUPABASE_SERVICE_ROLE_KEY to the service_role key.'
            );
            return res.status(500).json({
                success: false,
                code: 'DB_WRITE_BLOCKED',
                message:
                    'The server cannot save password changes right now. ' +
                    'Please contact support if this keeps happening.',
            });
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
