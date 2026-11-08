import jwt from 'jsonwebtoken';
import { env } from '../config/env.js';

export type AuthTokenPayload = {
    sub: string;
    email: string;
};

export function signAuthToken(userId: string | number, email: string): string {
    return jwt.sign(
        { sub: String(userId), email } satisfies AuthTokenPayload,
        env.jwtSecret,
        { expiresIn: env.jwtExpiresIn as jwt.SignOptions['expiresIn'] }
    );
}

export function verifyAuthToken(token: string): AuthTokenPayload {
    const decoded = jwt.verify(token, env.jwtSecret);
    if (typeof decoded === 'string' || !decoded.sub || typeof decoded.email !== 'string') {
        throw new Error('Invalid token payload.');
    }
    return { sub: String(decoded.sub), email: decoded.email };
}

export type ResetTokenPayload = {
    identifier: string;
    purpose: 'reset';
};

// Short-lived proof that the OTP was verified, so /reset-password does not
// have to re-read (and possibly miss) the verification_codes row.
export function signResetToken(identifier: string): string {
    return jwt.sign(
        { identifier, purpose: 'reset' } satisfies ResetTokenPayload,
        env.jwtSecret,
        { expiresIn: '15m' }
    );
}

export function verifyResetToken(token: string): ResetTokenPayload {
    const decoded = jwt.verify(token, env.jwtSecret);
    if (
        typeof decoded === 'string' ||
        typeof decoded.identifier !== 'string' ||
        decoded.purpose !== 'reset'
    ) {
        throw new Error('Invalid reset token.');
    }
    return { identifier: decoded.identifier, purpose: 'reset' };
}

export function publicUser(user: Record<string, unknown>) {
    return {
        id: user.id != null ? String(user.id) : null,
        fullName: user.full_name ?? null,
        email: user.email ?? null,
        phoneNumber: user.phone_number ?? null,
        vehicleType: user.vehicle_type ?? null,
        plateNumber: user.plate_number ?? null,
        emergencyName: user.emergency_name ?? null,
        emergencyPhone: user.emergency_phone ?? null,
        isVerified: Boolean(user.is_verified),
    };
}
