import { NextFunction, Request, Response } from 'express';
import { verifyAuthToken } from '../lib/jwt.js';

export type AuthenticatedRequest = Request & {
    auth?: { userId: string; email: string };
};

export function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
    const header = req.headers.authorization;
    const token = header?.startsWith('Bearer ') ? header.slice(7) : null;

    if (!token) {
        res.status(401).json({ success: false, code: 'TOKEN_MISSING', message: 'Authentication required.' });
        return;
    }

    try {
        const payload = verifyAuthToken(token);
        req.auth = { userId: payload.sub, email: payload.email };
        next();
    } catch (err: unknown) {
        // Log only the verification reason, never the token itself.
        const reason = err instanceof Error ? err.message : 'unknown error';
        console.warn(`[auth] Rejected bearer token: ${reason}`);
        res.status(401).json({
            success: false,
            code: 'SESSION_EXPIRED',
            message: 'Session expired. Please log in again.',
        });
    }
}
