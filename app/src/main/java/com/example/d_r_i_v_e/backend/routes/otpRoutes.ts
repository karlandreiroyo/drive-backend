import { Router } from "express";
import bcrypt from "bcrypt";
import { createClient } from "@supabase/supabase-js";
import { createAndSendOtp, checkCode, OtpPurpose } from "./otpService.js";
import dotenv from "dotenv";

dotenv.config();

const router = Router();

// Helper to get a fresh client instance on demand
const getSupabase = () => {
  const url = process.env.SUPABASE_URL || "";
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_KEY || "";
  return createClient(url, key);
};

const norm = (v: unknown) => String(v ?? "").trim().toLowerCase();
const isPurpose = (p: unknown): p is OtpPurpose => p === "signup" || p === "reset";

function passwordError(pw: string): string | null {
  if (pw.length < 8) return "Password must be at least 8 characters.";
  if (!/[a-z]/.test(pw)) return "Password needs at least 1 lowercase letter.";
  if (!/[A-Z]/.test(pw)) return "Password needs at least 1 uppercase letter.";
  if (!/\d/.test(pw)) return "Password needs at least 1 number.";
  if (!/[^A-Za-z0-9]/.test(pw)) return "Password needs at least 1 special character.";
  return null;
}

router.post("/verify-otp", async (req, res) => {
  const supabase = getSupabase(); // <--- LITERALLY THE FIRST LINE. It cannot fail now.
  try {
    const email = norm(req.body.email);
    const code = String(req.body.code ?? "").trim();
    const purpose = req.body.purpose;

    if (!email || !/^\d{6}$/.test(code) || !isPurpose(purpose))
      return res.status(400).json({ success: false, message: "Invalid request." });

    const result = await checkCode(email, code, purpose, purpose === "signup");
    if (!result.ok) return res.status(result.status).json({ success: false, message: result.message });

    if (purpose === "signup") {
      const { error } = await supabase.from("users").update({ is_verified: true }).eq("email", email);
      if (error) {
        console.error("Verify user error:", error);
        return res.status(500).json({ success: false, message: "Server error. Please try again." });
      }
    }
    return res.json({
      success: true,
      message: purpose === "signup" ? "Email verified successfully!" : "Code verified.",
    });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ success: false, message: "Server error. Please try again." });
  }
});

router.post("/resend-otp", async (req, res) => {
  try {
    const supabase = getSupabase(); // Defined safely at the top
    const email = norm(req.body.email);
    const purpose = req.body.purpose;

    if (!email || !isPurpose(purpose))
      return res.status(400).json({ success: false, message: "Invalid request." });

    const { data: user } = await supabase
      .from("users")
      .select("id, is_verified")
      .eq("email", email)
      .maybeSingle();

    const generic = { success: true, message: "If eligible, a new code has been sent." };
    if (!user) return res.json(generic);
    if (purpose === "signup" && user.is_verified) return res.json(generic);

    const r = await createAndSendOtp(email, purpose);
    if (!r.ok) return res.status(r.retryAfter ? 429 : 500).json({ success: false, message: r.message });
    return res.json({ success: true, message: "A new code has been sent to your email." });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ success: false, message: "Server error. Please try again." });
  }
});

router.post("/forgot-password", async (req, res) => {
  try {
    const supabase = getSupabase(); // Defined safely at the top
    const email = norm(req.body.email);
    if (!email) return res.status(400).json({ success: false, message: "Email is required." });

    const { data: user } = await supabase.from("users").select("id").eq("email", email).maybeSingle();
    if (user) await createAndSendOtp(email, "reset");

    return res.json({ success: true, message: "If that email is registered, a code has been sent." });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ success: false, message: "Server error. Please try again." });
  }
});

router.post("/reset-password", async (req, res) => {
  try {
    const supabase = getSupabase(); // Defined safely at the top
    const email = norm(req.body.email);
    const code = String(req.body.code ?? "").trim();
    const newPassword = String(req.body.newPassword ?? "");

    if (!email || !/^\d{6}$/.test(code))
      return res.status(400).json({ success: false, message: "Invalid request." });

    const pwErr = passwordError(newPassword);
    if (pwErr) return res.status(400).json({ success: false, message: pwErr });

    const result = await checkCode(email, code, "reset", true);
    if (!result.ok) return res.status(result.status).json({ success: false, message: result.message });

    const hashed = await bcrypt.hash(newPassword, 10);
    const { error } = await supabase.from("users").update({ password: hashed }).eq("email", email);
    if (error) {
      console.error("Reset password error:", error);
      return res.status(500).json({ success: false, message: "Server error. Please try again." });
    }
    return res.json({ success: true, message: "Password updated. You can now log in." });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ success: false, message: "Server error. Please try again." });
  }
});

export default router;