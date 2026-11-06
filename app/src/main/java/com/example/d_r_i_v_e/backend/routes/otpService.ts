import "dotenv/config";
import crypto from "crypto";
import { createClient } from "@supabase/supabase-js";

export type OtpPurpose = "signup" | "reset";

const OTP_TTL_MINUTES = 10;
const RESEND_COOLDOWN_SECONDS = 60;

// Local client factory to prevent any module-load or scope reference errors
const getSupabase = () => {
  const url = process.env.SUPABASE_URL || "";
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_KEY || "";
  return createClient(url, key);
};

function hashCode(email: string, purpose: OtpPurpose, code: string): string {
  return crypto
    .createHash("sha256")
    .update(`${email}:${purpose}:${code}:${process.env.OTP_SECRET ?? ""}`)
    .digest("hex");
}

function generateCode(): string {
  return crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
}

async function sendEmail(to: string, subject: string, html: string): Promise<boolean> {
  try {
    const apiKey = process.env.BREVO_API_KEY;
    const senderEmail = process.env.BREVO_SENDER_EMAIL ?? "drive.devteam@gmail.com";
    const senderName = process.env.BREVO_SENDER_NAME ?? "D.R.I.V.E.";

    console.log(`[sendEmail] Sending Brevo email to: ${to}`);

    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": apiKey!,
        "content-type": "application/json",
        accept: "application/json",
      },
      body: JSON.stringify({
        sender: { name: senderName, email: senderEmail },
        to: [{ email: to }],
        subject,
        htmlContent: html,
      }),
    });

    const responseText = await res.text();
    if (!res.ok) {
      console.error("[sendEmail] Brevo API HTTP Error:", res.status, responseText);
      return false;
    }

    console.log("[sendEmail] Brevo email sent successfully!");
    return true;
  } catch (e) {
    console.error("[sendEmail] Exception during Brevo fetch:", e);
    return false;
  }
}

export async function createAndSendOtp(
  email: string,
  purpose: OtpPurpose
): Promise<{ ok: boolean; message: string; retryAfter?: number }> {
  const supabase = getSupabase();
  console.log(`[createAndSendOtp] Starting OTP generation for email: ${email}, purpose: ${purpose}`);

  // Cooldown Check
  const { data: last, error: cooldownError } = await supabase
    .from("verification_codes")
    .select("created_at")
    .eq("identifier", email)
    .eq("verification_type", purpose)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (cooldownError) {
    console.error("[createAndSendOtp] Supabase Cooldown Query Error:", cooldownError);
  }

  if (last && last.created_at) {
    const elapsed = (Date.now() - new Date(last.created_at).getTime()) / 1000;
    if (elapsed < RESEND_COOLDOWN_SECONDS) {
      const retryAfter = Math.ceil(RESEND_COOLDOWN_SECONDS - elapsed);
      return { ok: false, message: `Please wait ${retryAfter}s before requesting a new code.`, retryAfter };
    }
  }

  // Invalidate Old Unused Codes
  await supabase
    .from("verification_codes")
    .update({ used: true })
    .eq("identifier", email)
    .eq("verification_type", purpose)
    .eq("used", false);

  const code = generateCode();
  const expirationTimestamp = new Date(Date.now() + OTP_TTL_MINUTES * 60_000).toISOString();

  // Insert OTP into `verification_codes`
  const { error: insertError } = await supabase
    .from("verification_codes")
    .insert([
      {
        identifier: email,
        code: hashCode(email, purpose, code),
        verification_type: purpose,
        expires_at: expirationTimestamp,
        attempts: 0,
        used: false
      }
    ]);

  if (insertError) {
    console.error("[createAndSendOtp] Supabase Insert Error:", insertError);
    return { ok: false, message: "Failed to save verification code in database." };
  }

  const subject = purpose === "signup" ? "Verify your D.R.I.V.E. account" : "Reset your D.R.I.V.E. password";
  const html = `
    <div style="font-family:Arial,sans-serif;max-width:420px;margin:auto">
      <h2>D.R.I.V.E.</h2>
      <p>Use this code: <b>${code}</b></p>
      <p>Expires in ${OTP_TTL_MINUTES} minutes.</p>
    </div>`;

  const sent = await sendEmail(email, subject, html);
  if (!sent) return { ok: false, message: "We couldn't send the email via Brevo." };

  return { ok: true, message: "Code sent." };
}

export async function checkCode(
  email: string,
  code: string,
  purpose: OtpPurpose,
  consume: boolean
): Promise<{ ok: boolean; status: number; message: string }> {
  const supabase = getSupabase();

  const { data: row, error } = await supabase
    .from("verification_codes")
    .select("id, code, expires_at, attempts")
    .eq("identifier", email)
    .eq("verification_type", purpose)
    .eq("used", false)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) {
    console.error("Check OTP error:", error);
    return { ok: false, status: 500, message: "Server error. Please try again." };
  }
  if (!row) return { ok: false, status: 400, message: "No active code. Please request a new one." };
  if (new Date(row.expires_at).getTime() < Date.now())
    return { ok: false, status: 400, message: "This code has expired. Please request a new one." };

  const maxAttempts = 5;
  if (row.attempts >= maxAttempts)
    return { ok: false, status: 429, message: "Too many wrong attempts. Please request a new code." };

  const expected = Buffer.from(row.code, "hex");
  const actual = Buffer.from(hashCode(email, purpose, code), "hex");
  const match = expected.length === actual.length && crypto.timingSafeEqual(expected, actual);

  if (!match) {
    await supabase
      .from("verification_codes")
      .update({ attempts: (row.attempts || 0) + 1 })
      .eq("id", row.id);
    const left = maxAttempts - ((row.attempts || 0) + 1);
    return {
      ok: false,
      status: 400,
      message: left > 0 ? `Incorrect code. ${left} attempt(s) left.` : "Too many wrong attempts.",
    };
  }

  if (consume) {
    await supabase.from("verification_codes").update({ used: true }).eq("id", row.id);
  }
  return { ok: true, status: 200, message: "Code verified." };
}