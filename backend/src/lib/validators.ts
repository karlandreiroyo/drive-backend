export const isPHPhoneNumber = (phone: string): boolean => /^(09|\+639)\d{9}$/.test(phone);

export const isValidEmail = (email: string): boolean =>
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

export function passwordError(pw: string): string | null {
    if (pw.length < 8) return 'Password must be at least 8 characters.';
    if (!/[a-z]/.test(pw)) return 'Password needs at least 1 lowercase letter.';
    if (!/[A-Z]/.test(pw)) return 'Password needs at least 1 uppercase letter.';
    if (!/\d/.test(pw)) return 'Password needs at least 1 number.';
    if (!/[^A-Za-z0-9]/.test(pw)) return 'Password needs at least 1 special character.';
    return null;
}

export const isValidPassword = (password: string): boolean => passwordError(password) === null;

export function normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
}

export function normalizePhone(phone: string): string {
    return phone.trim().replace(/[\s()-]/g, '');
}

export function canonicalPhone(phone: string): string {
    const normalized = normalizePhone(phone);
    if (normalized.startsWith('+639')) {
        return '0' + normalized.slice(3);
    }
    return normalized;
}

export function phoneLookupValues(phone: string): string[] {
    const normalized = normalizePhone(phone);
    const values = new Set<string>([normalized]);
    if (normalized.startsWith('09') && normalized.length === 11) {
        values.add('+63' + normalized.slice(1));
    }
    if (normalized.startsWith('+639') && normalized.length === 13) {
        values.add('0' + normalized.slice(3));
    }
    return [...values];
}

export type IdentifierKind = 'email' | 'phone';

export interface ResolvedIdentifier {
    kind: IdentifierKind;
    identifier: string;
    lookupValues: string[];
}

export function resolveIdentifier(input: string): ResolvedIdentifier | null {
    const trimmed = input.trim();
    if (!trimmed) return null;

    if (trimmed.includes('@')) {
        if (!isValidEmail(trimmed)) return null;
        const email = normalizeEmail(trimmed);
        return { kind: 'email', identifier: email, lookupValues: [email] };
    }

    const phone = canonicalPhone(trimmed);
    if (!isPHPhoneNumber(phone)) return null;
    return { kind: 'phone', identifier: phone, lookupValues: phoneLookupValues(phone) };
}
