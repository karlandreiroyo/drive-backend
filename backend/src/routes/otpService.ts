import crypto from "crypto";
import { supabase } from "../lib/supabase.js";
import { env } from "../config/env.js";

export type OtpPurpose = "signup" | "reset";
export type OtpChannel = "email" | "sms";

// Strict TTL for both signup and password-reset codes.
const OTP_TTL_MINUTES = 10;
const RESEND_COOLDOWN_SECONDS = 60;

type EmailContent = {
  subject: string;
  html: string;
  text: string;
};

export function buildOtpEmail(purpose: OtpPurpose, code: string): EmailContent {
  const isReset = purpose === "reset";
  const title = isReset ? "Password reset code" : "Verify your account";
  const lead = isReset
    ? "We received a request to reset your D.R.I.V.E. password. Enter the code below in the app to continue."
    : "Welcome to D.R.I.V.E. Enter the code below in the app to verify your account.";
  const subject = isReset
    ? "Your D.R.I.V.E. password reset code"
    : "Your D.R.I.V.E. verification code";

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="x-apple-disable-message-reformatting">
  <title>${subject}</title>
</head>
<body style="margin:0;padding:0;background-color:#0B0D17;-webkit-text-size-adjust:100%;">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
    Your D.R.I.V.E. code is ${code}. It expires in ${OTP_TTL_MINUTES} minutes.
  </div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#0B0D17;">
    <tr>
      <td align="center" style="padding:40px 16px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;background-color:#141828;border:1px solid #232A44;border-radius:16px;">
          <tr>
            <td style="padding:28px 32px 8px 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td style="font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;color:#FFFFFF;letter-spacing:1px;">
                    D.R.I.V.E.
                  </td>
                  <td align="right" style="font-family:Arial,Helvetica,sans-serif;font-size:11px;font-weight:bold;color:#8B93B8;letter-spacing:2px;text-transform:uppercase;">
                    Security
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:12px 32px 0 32px;font-family:Arial,Helvetica,sans-serif;">
              <h1 style="margin:0;font-size:20px;line-height:26px;color:#FFFFFF;font-weight:bold;">${title}</h1>
              <p style="margin:10px 0 0 0;font-size:14px;line-height:21px;color:#A8B0CC;">${lead}</p>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 32px 8px 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#1E2440;border:1px dashed #4A57A8;border-radius:12px;">
                <tr>
                  <td align="center" style="padding:22px 12px;font-family:'Courier New',Courier,monospace;font-size:36px;line-height:40px;font-weight:bold;color:#FFFFFF;letter-spacing:10px;">
                    ${code}
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 32px 0 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#2E2416;border-left:3px solid #F5A623;border-radius:6px;">
                <tr>
                  <td style="padding:12px 14px;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:19px;color:#FFD08A;">
                    This code expires in <strong>${OTP_TTL_MINUTES} minutes</strong>. After that, you will need to request a new one.
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:20px 32px 0 32px;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:20px;color:#8B93B8;">
              For your security, never share this code with anyone. D.R.I.V.E. will never ask you for it by phone, chat, or email.
            </td>
          </tr>
          <tr>
            <td style="padding:24px 32px 28px 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-top:1px solid #232A44;">
                <tr>
                  <td style="padding-top:18px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:18px;color:#6E7694;">
                    If you didn't request this code, you can safely ignore this email. Your account remains secure.
                    <br><br>
                    D.R.I.V.E. Security Team
                  </td>
                </tr>
              </table>
            </td>
          </tr>
        </table>
        <div style="max-width:480px;margin-top:16px;font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:16px;color:#4C5470;text-align:center;">
          This is an automated message. Please do not reply.
        </div>
      </td>
    </tr>
  </table>
</body>
</html>`;

  const text = [
    "D.R.I.V.E. Security",
    "",
    title,
    "",
    lead,
    "",
    `Code: ${code}`,
    "",
    `This code expires in ${OTP_TTL_MINUTES} minutes.`,
    "Never share this code with anyone.",
    "",
    "If you didn't request this code, you can safely ignore this email.",
    "D.R.I.V.E. Security Team",
  ].join("\n");

  return { subject, html, text };
}

function hashCode(identifier: string, purpose: OtpPurpose, code: string): string {
  return crypto
    .createHash("sha256")
    .update(`${identifier}:${purpose}:${code}:${env.otpSecret}`)
    .digest("hex");
}

function generateCode(): string {
  return crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
}

async function sendEmail(to: string, subject: string, html: string, text: string): Promise<boolean> {
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
        textContent: text,
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
    const email = buildOtpEmail(purpose, code);

    console.log(`[createAndSendOtp] Invoking sendEmail via Brevo API for ${identifier}...`);
    sent = await sendEmail(identifier, email.subject, email.html, email.text);
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
  if (!row) {
    // Distinguish "already consumed" from "never issued" so the client shows
    // an accurate message instead of a generic failure.
    const { data: latest } = await supabase
      .from("verification_codes")
      .select("used, expires_at")
      .eq("identifier", identifier)
      .eq("verification_type", purpose)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (latest?.used) {
      return { ok: false, status: 400, message: "This code has already been used. Please request a new one." };
    }
    return { ok: false, status: 400, message: "No active code. Please request a new one." };
  }
  if (new Date(row.expires_at).getTime() < Date.now()) {
    // Invalidate the expired row so the same code can never be reused and the
    // next request starts from a clean slate.
    const { error: expireError } = await supabase
      .from("verification_codes")
      .update({ used: true })
      .eq("id", row.id);

    if (expireError) {
      console.error("Failed to invalidate expired OTP:", expireError);
    }
    return { ok: false, status: 400, message: "This code has expired. Please request a new one." };
  }

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
