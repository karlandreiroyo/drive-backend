import crypto from "crypto";
import { supabase } from "../lib/supabase.js";
import { env } from "../config/env.js";

export type OtpPurpose = "signup" | "reset";
export type OtpChannel = "email" | "sms";

const OTP_TTL_MINUTES = 10;
const RESEND_COOLDOWN_SECONDS = 60;

function hashCode(identifier: string, purpose: OtpPurpose, code: string): string {
  return crypto
    .createHash("sha256")
    .update(`${identifier}:${purpose}:${code}:${env.otpSecret}`)
    .digest("hex");
}

function generateCode(): string {
  return crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
}

async function sendEmail(to: string, subject: string, html: string): Promise<boolean> {
  try {
    const apiKey = env.brevoApiKey;
    const senderEmail = env.brevoSenderEmail;
    const senderName = env.brevoSenderName;

    console.log(`[sendEmail] Sending Brevo email to: ${to}`);
    console.log(`[sendEmail] Brevo API Key present: ${!!apiKey}, Sender: ${senderEmail}`);

    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": apiKey!,
        "content-type": "application/json",
        accept: "application/json",
      },
      body: JSON.stringify({
        sender: {
          name: senderName,
          email: senderEmail,
        },
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

    console.log("[sendEmail] Brevo email sent successfully! Response:", responseText);
    return true;
  } catch (e) {
    console.error("[sendEmail] Exception during Brevo fetch:", e);
    return false;
  }
}

export function isSmsConfigured(): boolean {
  return Boolean(env.semaphoreApiKey);
}

async function sendSms(to: string, message: string): Promise<boolean> {
  const apiKey = env.semaphoreApiKey;
  if (!apiKey) {
    console.error("[sendSms] SEMAPHORE_API_KEY is not configured.");
    return false;
  }

  const number = to.startsWith("+63") ? `0${to.slice(3)}` : to;
  const body = new URLSearchParams({
    apikey: apiKey,
    number,
    message,
  });
  if (env.semaphoreSenderName) body.set("sendername", env.semaphoreSenderName);

  try {
    console.log(`[sendSms] Sending Semaphore SMS to: ${number}`);
    const res = await fetch(env.semaphoreApiUrl, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        accept: "application/json",
      },
      body,
    });

    const responseText = await res.text();
    if (!res.ok) {
      console.error("[sendSms] Semaphore API HTTP Error:", res.status, responseText);
      return false;
    }

    console.log("[sendSms] Semaphore SMS sent successfully!", responseText);
    return true;
  } catch (e) {
    console.error("[sendSms] Exception during Semaphore fetch:", e);
    return false;
  }
}

export async function createAndSendOtp(
  identifier: string,
  purpose: OtpPurpose,
  channel: OtpChannel = "email"
): Promise<{ ok: boolean; message: string; retryAfter?: number }> {
  console.log(`[createAndSendOtp] Starting OTP generation for ${channel} identifier: ${identifier}, purpose: ${purpose}`);

  // 1. Cooldown Check
  const { data: last, error: cooldownError } = await supabase
    .from("verification_codes")
    .select("created_at")
    .eq("identifier", identifier)
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
      console.warn(`[createAndSendOtp] Cooldown active for ${identifier}. Retry after ${retryAfter}s`);
      return { ok: false, message: `Please wait ${retryAfter}s before requesting a new code.`, retryAfter };
    }
  }

  // 2. Invalidate Old Unused Codes
  const { error: updateError } = await supabase
    .from("verification_codes")
    .update({ used: true })
    .eq("identifier", identifier)
    .eq("verification_type", purpose)
    .eq("used", false);

  if (updateError) {
    console.error("[createAndSendOtp] Supabase Invalidate Old Codes Error:", updateError);
  }

  const code = generateCode();
  const expirationTimestamp = new Date(Date.now() + OTP_TTL_MINUTES * 60_000).toISOString();

  console.log(`[createAndSendOtp] Generated OTP for ${identifier} (expires at ${expirationTimestamp})`);

  // 3. Insert OTP into `verification_codes`
  const { error: insertError } = await supabase
    .from("verification_codes")
    .insert([
      {
        identifier,
        code: hashCode(identifier, purpose, code),
        verification_type: purpose,
        expires_at: expirationTimestamp,
        attempts: 0,
        used: false
      }
    ]);

  if (insertError) {
    console.error("[createAndSendOtp] Supabase Insert verification_codes Error:", insertError);
    return { ok: false, message: "Failed to save verification code in database." };
  }

  console.log(`[createAndSendOtp] Verification code stored successfully in Supabase for ${identifier}`);

  // 4. Deliver the code through the requested channel
  let sent = false;
  if (channel === "sms") {
    const sms = `D.R.I.V.E. ${purpose === "signup" ? "verification" : "password reset"} code: ${code}. Expires in ${OTP_TTL_MINUTES} minutes.`;
    console.log(`[createAndSendOtp] Invoking sendSms for ${identifier}...`);
    sent = await sendSms(identifier, sms);
  } else {
    const subject =
      purpose === "signup" ? "Verify your D.R.I.V.E. account" : "Reset your D.R.I.V.E. password";
    const html = `
    <div style="font-family:Arial,sans-serif;max-width:420px;margin:auto">
      <h2>D.R.I.V.E.</h2>
      <p>${purpose === "signup" ? "Use this code to verify your account:" : "Use this code to reset your password:"}</p>
      <p style="font-size:32px;font-weight:bold;letter-spacing:6px">${code}</p>
      <p>This code expires in ${OTP_TTL_MINUTES} minutes. If you didn't request it, ignore this email.</p>
    </div>`;

    console.log(`[createAndSendOtp] Invoking sendEmail via Brevo API for ${identifier}...`);
    sent = await sendEmail(identifier, subject, html);
  }

  if (!sent) {
    console.error(`[createAndSendOtp] ${channel} delivery returned false for ${identifier}`);
    return {
      ok: false,
      message: channel === "sms"
        ? "We couldn't send the SMS. Please try again."
        : "We couldn't send the email via Brevo. Please try again.",
    };
  }

  console.log(`[createAndSendOtp] OTP process complete for ${identifier}!`);
  return { ok: true, message: channel === "sms" ? "Code sent via SMS." : "Code sent via email." };
}

export async function checkCode(
  identifier: string,
  code: string,
  purpose: OtpPurpose,
  consume: boolean
): Promise<{ ok: boolean; status: number; message: string }> {
  const { data: row, error } = await supabase
    .from("verification_codes")
    .select("id, code, expires_at, attempts")
    .eq("identifier", identifier)
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
  const actual = Buffer.from(hashCode(identifier, purpose, code), "hex");
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
      message: left > 0 ? `Incorrect code. ${left} attempt(s) left.` : "Too many wrong attempts. Please request a new code.",
    };
  }

  if (consume) {
    await supabase.from("verification_codes").update({ used: true }).eq("id", row.id);
  }
  return { ok: true, status: 200, message: "Code verified." };
}
