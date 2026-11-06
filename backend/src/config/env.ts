import dotenv from 'dotenv';

dotenv.config();

function required(name: string): string {
    const value = process.env[name]?.trim();
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

function optional(name: string, fallback: string): string {
    const value = process.env[name]?.trim();
    return value || fallback;
}

function jwtRole(token: string): string | null {
    try {
        const payload = token.split('.')[1];
        if (!payload) return null;
        const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
        const json = JSON.parse(Buffer.from(padded, 'base64').toString('utf8')) as { role?: string };
        return json.role ?? null;
    } catch {
        return null;
    }
}

const supabaseUrl = required('SUPABASE_URL');
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY?.trim()
    || process.env.SUPABASE_KEY?.trim()
    || '';

if (!supabaseKey) {
    throw new Error('Missing SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_KEY).');
}

const supabaseKeyRole = jwtRole(supabaseKey);

export const env = {
    port: Number(process.env.PORT) || 5000,
    supabaseUrl,
    supabaseKey,
    supabaseKeyRole,
    jwtSecret: optional('JWT_SECRET', optional('OTP_SECRET', 'change-me-in-production')),
    jwtExpiresIn: optional('JWT_EXPIRES_IN', '7d'),
    otpSecret: optional('OTP_SECRET', ''),
    brevoApiKey: process.env.BREVO_API_KEY ?? '',
    brevoSenderEmail: optional('BREVO_SENDER_EMAIL', 'drive.devteam@gmail.com'),
    brevoSenderName: optional('BREVO_SENDER_NAME', 'D.R.I.V.E.'),
    semaphoreApiKey: process.env.SEMAPHORE_API_KEY ?? '',
    semaphoreSenderName: process.env.SEMAPHORE_SENDER_NAME ?? '',
    semaphoreApiUrl: optional('SEMAPHORE_API_URL', 'https://api.semaphore.co/api/v4/messages'),
};

if (env.supabaseKeyRole === 'anon') {
    console.warn(
        '[env] SUPABASE_SERVICE_ROLE_KEY is an anon key. ' +
        'Use the service_role key from Supabase → Project Settings → API. ' +
        'Anon keys are blocked by RLS and expose the project if the table is public.'
    );
}

if (!process.env.JWT_SECRET) {
    console.warn('[env] JWT_SECRET is not set. Add a long random secret to backend/.env.');
}
