import express, { Application, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { env } from './config/env.js';
import { supabase } from './lib/supabase.js';
import signupRouter from './routes/signupBackend.js';
import otpRouter from './routes/otpRoutes.js';
import reportsRouter from './routes/reportsRoutes.js';

const app: Application = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

// Express Request Logger Middleware
app.use((req: Request, _res: Response, next: NextFunction) => {
    console.log(`[Express Log] ${req.method} ${req.url}`);
    next();
});

const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 80,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, message: 'Too many requests. Please try again later.' },
});

app.get('/', (_req: Request, res: Response) => {
    res.json({
        success: true,
        message: 'Vigil Backend Service is running',
    });
});

app.get('/api/health', async (_req: Request, res: Response) => {
    const { error } = await supabase.from('users').select('id').limit(1);
    res.status(error ? 503 : 200).json({
        success: !error,
        service: 'vigil-backend',
        database: error ? 'unreachable' : 'ok',
        supabaseKeyRole: env.supabaseKeyRole,
    });
});

app.use('/api/auth', authLimiter, signupRouter);
app.use('/api/auth', authLimiter, otpRouter);
app.use('/api', authLimiter, reportsRouter);

app.use((_req: Request, res: Response) => {
    res.status(404).json({
        success: false,
        message: 'Endpoint not found.',
    });
});

app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
    console.error('Unhandled Server Error:', err);
    res.status(500).json({
        success: false,
        message: 'Internal server error occurred.',
    });
});

app.listen(env.port, '0.0.0.0', () => {
    console.log(`Vigil Backend Server listening on http://0.0.0.0:${env.port}`);
});
