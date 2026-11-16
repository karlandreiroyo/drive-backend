import { env } from '../config/env.js';

/** Which notification a detected event belongs to: drowsiness or a system alert. */
export type AlertCategory = 'drowsiness' | 'alert';

export type EmergencyIncident = {
    incidentType: string;
    confidence: number;
    description: string;
    time: string;
    date?: string;
    tripLabel?: string;
    category?: AlertCategory;
};

/** Philippine emergency hotlines shown at the bottom of every alert email. */
const HOTLINES = [
    { label: 'National Emergency (Police, Fire, Ambulance)', number: '911' },
    { label: 'Philippine Red Cross (Ambulance / Medical)', number: '143' },
    { label: 'Philippine National Police', number: '117 / (02) 8722-0650' },
    { label: 'Bureau of Fire Protection (NCR)', number: '(02) 8426-0219 / (02) 8426-0246' },
    { label: 'MMDA (Metro Manila)', number: '136' },
];

/** At most one alert email per recipient in this window, to avoid spam-like bursts. */
const COOLDOWN_MS = 5 * 60 * 1000;
const lastSentAt = new Map<string, number>();

const SAFETY_NOTE =
    'This is an automated safety notification. False alerts can occasionally happen due to road conditions or lighting. ' +
    'If the driver is safe, no action is needed. Otherwise, please check on them and ensure they get proper rest before continuing their trip.';

function categoryLabel(category: AlertCategory | undefined): string {
    return category === 'drowsiness' ? 'Drowsiness Alert' : 'Safety Alert';
}

/** Overall tone of a batch: drowsiness-only, alert-only, or a mix of both. */
function batchKind(incidents: EmergencyIncident[]): AlertCategory | 'mixed' {
    const hasDrowsiness = incidents.some((incident) => incident.category === 'drowsiness');
    const hasAlert = incidents.some((incident) => incident.category !== 'drowsiness');
    if (hasDrowsiness && hasAlert) return 'mixed';
    return hasDrowsiness ? 'drowsiness' : 'alert';
}

export function isEmergencyEmailConfigured(): boolean {
    return Boolean(env.brevoApiKey);
}

function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&' + 'amp;')
        .replace(/</g, '&' + 'lt;')
        .replace(/>/g, '&' + 'gt;')
        .replace(/"/g, '&' + 'quot;')
        .replace(/'/g, '&' + '#39;');
}

/** Low / Medium / High wording. */
function severityLabel(confidence: number): string {
    if (confidence >= 0.8) return 'High';
    if (confidence >= 0.5) return 'Medium';
    return 'Low';
}

function severityColors(confidence: number): { bg: string; text: string; border: string } {
    if (confidence >= 0.8) {
        return { bg: '#FEE2E2', text: '#B91C1C', border: '#FCA5A5' };
    }
    if (confidence >= 0.5) {
        return { bg: '#FEF3C7', text: '#B45309', border: '#FCD34D' };
    }
    return { bg: '#E0F2FE', text: '#0369A1', border: '#7DD3FC' };
}

function hotlinesHtml(): string {
    const rows = HOTLINES.map(
        (h) => `<tr>
  <td style="padding:8px 12px 8px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#334155;border-bottom:1px solid #FECACA;">
    ${escapeHtml(h.label)}
  </td>
  <td align="right" style="padding:8px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:16px;line-height:22px;color:#991B1B;font-weight:bold;white-space:nowrap;border-bottom:1px solid #FECACA;">
    ${escapeHtml(h.number)}
  </td>
</tr>`
    ).join('');

    return `<tr>
  <td style="padding:8px 36px 20px 36px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #FECACA;border-radius:14px;background-color:#FEF2F2;">
      <tr>
        <td style="padding:20px 24px 10px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:17px;font-weight:bold;color:#991B1B;">
          Emergency Hotlines (If the driver needs help)
        </td>
      </tr>
      <tr>
        <td style="padding:0 24px 18px 24px;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
            ${rows}
          </table>
        </td>
      </tr>
    </table>
  </td>
</tr>`;
}

function hotlinesText(): string {
    return ['EMERGENCY HOTLINES (Philippines)', ...HOTLINES.map((h) => `${h.label}: ${h.number}`)].join('\n');
}

export async function sendEmergencyIncidentEmail(
    to: string,
    driverName: string | null,
    incidents: EmergencyIncident[]
): Promise<boolean> {
    const apiKey = env.brevoApiKey;
    if (!apiKey || incidents.length === 0) return false;

    const key = to.trim().toLowerCase();
    const last = lastSentAt.get(key) ?? 0;
    if (Date.now() - last < COOLDOWN_MS) {
        console.log(`[emergencyEmail] Cooldown active for ${to}; ${incidents.length} incident(s) left pending.`);
        return false;
    }

    // Reserve the cooldown slot now so a parallel sync can't send a duplicate.
    lastSentAt.set(key, Date.now());

    const rawName = driverName?.trim() || 'A D.R.I.V.E. driver';
    const name = rawName.charAt(0).toUpperCase() + rawName.slice(1);
    const count = incidents.length;
    const plural = count === 1 ? '' : 's';
    const kind = batchKind(incidents);
    const kindWord = kind === 'drowsiness' ? 'drowsiness' : kind === 'alert' ? 'system' : 'safety';

    const heading = kind === 'drowsiness'
        ? 'Drowsiness alert while driving'
        : kind === 'alert'
            ? 'System alert while driving'
            : 'Safety alerts while driving';

    const subject = kind === 'drowsiness'
        ? 'D.R.I.V.E. Safety Alert: Driver Attention Needed'
        : 'D.R.I.V.E. Safety Alert: Driver Check-In Recommended';

    // Friendly, non-technical summary (hides raw camera/system detection specifics)
    const intro = count === 1
        ? `Please check in on <strong>${escapeHtml(name)}</strong> when safe. Below are the time and trip details of the alert.`
        : `Please check in on <strong>${escapeHtml(name)}</strong> when safe. Below are the time and trip details of the alerts.`;

    const incidentRows = incidents
        .map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .map((value) => escapeHtml(value))
                .join(' • ');
            const trip = (incident.tripLabel ?? '').trim();
            const sev = severityLabel(incident.confidence);
            const sevStyle = severityColors(incident.confidence);
            const isDrowsiness = incident.category === 'drowsiness';

            return `<tr>
  <td style="padding:16px 20px;background-color:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tr>
        <td style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:17px;font-weight:bold;color:${isDrowsiness ? '#DC2626' : '#D97706'};">
          ${categoryLabel(incident.category)}
        </td>
        <td align="right">
          <span style="display:inline-block;padding:4px 12px;border-radius:999px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:13px;font-weight:bold;background-color:${sevStyle.bg};color:${sevStyle.text};border:1px solid ${sevStyle.border};">
            Priority: ${sev}
          </span>
        </td>
      </tr>
    </table>
    ${when ? `<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#334155;margin-top:8px;"><strong>Time:</strong> ${when}</div>` : ''}
    ${trip ? `<div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#334155;margin-top:4px;"><strong>Trip:</strong> ${escapeHtml(trip)}</div>` : ''}
  </td>
</tr>
<tr><td style="height:12px;"></td></tr>`;
        })
        .join('');

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(subject)}</title>
</head>
<body style="margin:0;padding:0;background-color:#F1F5F9;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F1F5F9;">
    <tr>
      <td align="center" style="padding:36px 16px;">
        <!-- Mas malapad na container (640px) para malaki at madaling basahin -->
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:640px;background-color:#FFFFFF;border:1px solid #E2E8F0;border-radius:20px;overflow:hidden;box-shadow:0 4px 12px rgba(15,23,42,0.06);">
          <!-- Top Accent Bar -->
          <tr>
            <td style="height:6px;background-color:#DC2626;"></td>
          </tr>
          <!-- Brand Header -->
          <tr>
            <td style="padding:32px 36px 8px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
              <span style="display:inline-block;padding:6px 14px;background-color:#0F172A;color:#FFFFFF;font-size:16px;font-weight:bold;letter-spacing:2px;border-radius:8px;">
                D.R.I.V.E.
              </span>
            </td>
          </tr>
          <!-- Main Heading & Intro -->
          <tr>
            <td style="padding:16px 36px 8px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
              <h1 style="margin:0;font-size:26px;line-height:34px;color:#DC2626;font-weight:800;">
                ${heading}
              </h1>
              <p style="margin:14px 0 0 0;font-size:16px;line-height:25px;color:#475569;">
                ${intro}
              </p>
            </td>
          </tr>
          <!-- Alert Cards (Walang raw detection description) -->
          <tr>
            <td style="padding:20px 36px 12px 36px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                ${incidentRows}
              </table>
            </td>
          </tr>
          <!-- Emergency Hotlines -->
          ${hotlinesHtml()}
          <!-- Footer Note -->
          <tr>
            <td style="padding:12px 36px 36px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:14px;line-height:22px;color:#64748B;">
              ${escapeHtml(SAFETY_NOTE)}
              <br><br>
              <strong style="color:#334155;">D.R.I.V.E. Safety Team</strong>
            </td>
          </tr>
        </table>
        <div style="max-width:640px;margin-top:18px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:13px;line-height:18px;color:#94A3B8;text-align:center;">
          This is an automated safety message. Please do not reply.
        </div>
      </td>
    </tr>
  </table>
</body>
</html>`;

    const text = [
        `D.R.I.V.E. ${kindWord.charAt(0).toUpperCase()}${kindWord.slice(1)} Alert`,
        '',
        `${heading}.`,
        `Please check in on ${name} when safe.`,
        `Alert detail${plural}:`,
        '',
        ...incidents.map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .join(' at ');
            const trip = (incident.tripLabel ?? '').trim();
            const lines = [
                `[${categoryLabel(incident.category)}] Priority: ${severityLabel(incident.confidence)}`,
            ];
            if (when) lines.push(`  Time: ${when}`);
            if (trip) lines.push(`  Trip: ${trip}`);
            return lines.join('\n');
        }),
        '',
        hotlinesText(),
        '',
        SAFETY_NOTE,
        '',
        'D.R.I.V.E. Safety Team',
    ].join('\n');

    try {
        const res = await fetch('https://api.brevo.com/v3/smtp/email', {
            method: 'POST',
            headers: {
                'api-key': apiKey,
                'content-type': 'application/json',
                accept: 'application/json',
            },
            body: JSON.stringify({
                sender: {
                    name: env.brevoSenderName,
                    email: env.brevoSenderEmail,
                },
                to: [{ email: to }],
                subject,
                htmlContent: html,
                textContent: text,
            }),
        });

        const responseText = await res.text();
        if (!res.ok) {
            console.error('[emergencyEmail] Brevo HTTP error:', res.status, responseText);
            lastSentAt.delete(key);
            return false;
        }
        console.log(`[emergencyEmail] Sent ${count} ${kindWord} alert incident(s) to ${to}`);
        return true;
    } catch (error) {
        console.error('[emergencyEmail] Brevo request failed:', error);
        lastSentAt.delete(key);
        return false;
    }
}