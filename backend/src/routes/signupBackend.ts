import { Router, Request, Response } from 'express';
import bcrypt from 'bcrypt';
import { supabase } from './supabaseclient.js';
import { createAndSendOtp } from './otpService.js';
import { signAuthToken } from '../lib/jwt.js';

const router: Router = Router();

const isPHPhoneNumber = (phone: string): boolean => {
    return /^(09|\+639)\d{9}$/.test(phone);
};

const isValidEmail = (email: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
};

const isValidPassword = (password: string): boolean => {
    if (password.length < 8) return false;
    const hasLowercase = /[a-z]/.test(password);
    const hasUppercase = /[A-Z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasSpecial = /[^a-zA-Z0-9]/.test(password);
    return hasLowercase && hasUppercase && hasNumber && hasSpecial;
};

/**
 * @route   POST /api/auth/signup
 * @desc    Register a new user in Supabase and trigger OTP email
 * @access  Public
 */
router.post('/signup', async (req: Request, res: Response): Promise<Response> => {
    try {
        const {
            fullName,
            email,
            phoneNumber,
            password,
            emergencyName,
            emergencyPhone,
            vehicleType,
            plateNumber
        } = req.body;

        // Input Validation
        if (!fullName || typeof fullName !== 'string' || !fullName.trim()) {
            return res.status(400).json({ success: false, message: 'Full name is required.' });
        }

        if (!email || typeof email !== 'string' || !isValidEmail(email.trim())) {
            return res.status(400).json({ success: false, message: 'Invalid email address format.' });
        }

        const trimmedPhone = typeof phoneNumber === 'string' ? phoneNumber.trim() : '';
        if (!trimmedPhone || !isPHPhoneNumber(trimmedPhone)) {
            return res.status(400).json({
                success: false,
                message: 'Invalid Philippine phone number format. Must start with 09 or +639 followed by 9 digits.'
            });
        }

        if (!password || typeof password !== 'string' || !isValidPassword(password)) {
            return res.status(400).json({
                success: false,
                message: 'Password must be at least 8 characters long and contain at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character.'
            });
        }

        const normalizedEmail = email.trim().toLowerCase();

        // 2. Reject duplicate email or phone number in Supabase (HTTP 409)
        const { data: existingUser, error: checkError } = await supabase
            .from('users')
            .select('email, phone_number')
            .or(`email.eq.${normalizedEmail},phone_number.eq.${trimmedPhone}`)
            .maybeSingle();

        if (checkError) {
            console.error('Supabase query error checking existing user:', checkError);
            return res.status(500).json({
                success: false,
                message: 'Database query error: ' + checkError.message
            });
        }

        if (existingUser) {
            if (existingUser.email?.toLowerCase() === normalizedEmail) {
                return res.status(409).json({
                    success: false,
                    message: 'Email is already registered'
                });
            }
            if (existingUser.phone_number === trimmedPhone) {
                return res.status(409).json({
                    success: false,
                    message: 'Phone number is already registered'
                });
            }
            return res.status(409).json({
                success: false,
                message: 'Email or phone number is already registered'
            });
        }

        // 3. Hash password using bcrypt
        const hashedPassword = await bcrypt.hash(password, 10);

        // 4. Insert user into Supabase `users` table
        const insertPayload = {
            full_name: fullName.trim(),
            email: normalizedEmail,
            phone_number: trimmedPhone,
            password: hashedPassword,
            vehicle_type: (vehicleType && String(vehicleType).trim()) ? String(vehicleType).trim() : 'N/A',
            emergency_name: (emergencyName && String(emergencyName).trim()) ? String(emergencyName).trim() : 'N/A',
            emergency_phone: (emergencyPhone && String(emergencyPhone).trim()) ? String(emergencyPhone).trim() : 'N/A',
            plate_number: (plateNumber && String(plateNumber).trim()) ? String(plateNumber).trim() : 'N/A'
        };

        const { error: insertError } = await supabase
            .from('users')
            .insert([insertPayload]);

        if (insertError) {
            console.error('Supabase Insert Error:', insertError);
            return res.status(500).json({
                success: false,
                message: 'Database insert error: ' + insertError.message
            });
        }

        // 5. Trigger OTP creation and Brevo email delivery
        console.log(`[Signup] User ${normalizedEmail} saved to Supabase. Calling createAndSendOtp...`);
        const otpResult = await createAndSendOtp(normalizedEmail, 'signup');
        console.log(`[Signup] createAndSendOtp output for ${normalizedEmail}:`, otpResult);

        // 6. Return success JSON
        return res.status(200).json({
            success: true,
            message: otpResult.ok
                ? 'Account registered successfully. Verification code sent to your email.'
                : `Account registered, but OTP email could not be sent: ${otpResult.message}`
        });

    } catch (err: any) {
        console.error('Signup exception error:', err);
        return res.status(500).json({
            success: false,
            message: 'Server error: ' + (err.message || 'An unexpected error occurred')
        });
    }
});

/**
 * @route   POST /api/auth/login
 * @desc    Authenticate user login via Supabase and bcrypt (includes hardcoded test account)
 * @access  Public
 */
router.post('/login', async (req: Request, res: Response): Promise<Response> => {
    try {
        const { email, password } = req.body;

        if (!email || !password) {
            return res.status(400).json({ success: false, message: 'Email and password are required.' });
        }

        const normalizedEmail = String(email).trim().toLowerCase();
        const inputPassword = String(password).trim();

        // --- HARDCODED TEST ACCOUNT FOR INSTANT TESTING ---
        if (
            (normalizedEmail === 'driver@vigil.com' || normalizedEmail === 'test@vigil.com') &&
            inputPassword === 'Driver@123'
        ) {
            console.log(`[Login] Hardcoded test user logged in successfully: ${normalizedEmail}`);
            return res.status(200).json({
                success: true,
                message: 'Login successful',
                token: signAuthToken('0', normalizedEmail),
                user: {
                    id: '0',
                    fullName: 'Test Driver',
                    email: normalizedEmail,
                    phoneNumber: '09123456789',
                    vehicleType: 'Sedan',
                    plateNumber: 'ABC 123'
                }
            });
        }

        // 1. Hanapin ang user sa Supabase gamit ang email
        const { data: user, error: queryError } = await supabase
            .from('users')
            .select('*')
            .eq('email', normalizedEmail)
            .maybeSingle();

        if (queryError) {
            console.error('Supabase login query error:', queryError);
            return res.status(500).json({ success: false, message: 'Database error occurred.' });
        }

        if (!user) {
            return res.status(401).json({ success: false, message: 'Invalid email or password.' });
        }

        // 2. I-compare ang password gamit ang bcrypt.compare
        const isPasswordValid = await bcrypt.compare(inputPassword, user.password);

        if (!isPasswordValid) {
            return res.status(401).json({ success: false, message: 'Invalid email or password.' });
        }

        // 3. Matagumpay na login
        return res.status(200).json({
            success: true,
            message: 'Login successful',
            token: signAuthToken(user.id, user.email),
            user: {
                id: String(user.id),
                fullName: user.full_name,
                email: user.email,
                phoneNumber: user.phone_number,
                vehicleType: user.vehicle_type,
                plateNumber: user.plate_number,
                emergencyName: user.emergency_name,
                emergencyPhone: user.emergency_phone,
                isVerified: Boolean(user.is_verified)
            }
        });

    } catch (err: any) {
        console.error('Login exception error:', err);
        return res.status(500).json({
            success: false,
            message: 'Server error: ' + (err.message || 'An unexpected error occurred')
        });
    }
});

export default router;
