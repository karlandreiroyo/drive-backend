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
    'This is an automated estimate and false alerts can occur from glare, low light, or camera movement. ' +
    'If the driver is safe, no action is needed. Otherwise, please contact them and make sure they are able to rest before continuing to drive.';

function categoryLabel(category: AlertCategory | undefined): string {
    return category === 'drowsiness' ? 'Drowsiness alert' : 'System alert';
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
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** The description often repeats the confidence ("Motion · 51% confidence"); hide that duplicate. */
function usefulDescription(description: string): string {
    const value = (description ?? '').trim();
    return /confidence/i.test(value) ? '' : value;
}

/**
 * Plain-language label for the email. The on-device monitor measures overall
 * frame motion (not eyes or head directly), so the wording stays honest about that.
 */
function describeIncident(incident: EmergencyIncident): string {
    const type = (incident.incidentType ?? '').trim().toLowerCase();

    if (type === 'drowsiness') return 'Possible drowsiness (driver appeared still for several seconds)';
    if (type === 'motion') return 'Sudden movement detected in the camera view';

    const fallback = (incident.incidentType ?? '').trim();
    return fallback ? `${fallback} detected` : 'Driver alert detected';
}

/** Low / Medium / High wording. Adjust the cutoffs to match the app's Trip History. */
function severityLabel(confidence: number): string {
    if (confidence >= 0.8) return 'High';
    if (confidence >= 0.5) return 'Medium';
    return 'Low';
}

function hotlinesHtml(): string {
    const rows = HOTLINES.map(
        (h) => `<tr>
  <td style="padding:5px 12px 5px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#A8B0CC;">${escapeHtml(h.label)}</td>
  <td style="padding:5px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#FFFFFF;font-weight:bold;white-space:nowrap;">${escapeHtml(h.number)}</td>
</tr>`
    ).join('');
    return `<tr>
  <td style="padding:0 32px 8px 32px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #5A1F1F;border-radius:10px;background-color:#1B1020;">
      <tr>
        <td style="padding:14px 16px 6px 16px;font-family:Arial,Helvetica,sans-serif;font-size:13px;font-weight:bold;color:#FF8A8A;">
          If the driver may be in danger, call:
        </td>
      </tr>
      <tr>
        <td style="padding:0 16px 12px 16px;">
          <table role="presentation" cellpadding="0" cellspacing="0" border="0">${rows}</table>
        </td>
      </tr>
    </table>
  </td>
</tr>`;
}

function hotlinesText(): string {
    return ['EMERGENCY HOTLINES (Philippines)', ...HOTLINES.map((h) => `- ${h.label}: ${h.number}`)].join('\n');
}

/**
 * Drowsiness / important system-alert email. Detected events are uploaded from
 * the phone, so they may also cover incidents that were stored offline and
 * flushed once a connection was available. Delivered through Brevo to the
 * account email on file, with no credentials or SMTP details on the client.
 *
 * Returns true only when an email was actually sent. When the recipient is in
 * the cooldown window it returns false WITHOUT sending, so callers must only
 * mark incidents as email_sent on true; the skipped ones are then included in
 * the next email.
 */
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

    const name = driverName?.trim() || 'A D.R.I.V.E. driver';
    const count = incidents.length;
    const plural = count === 1 ? '' : 's';
    const kind = batchKind(incidents);
    const kindWord = kind === 'drowsiness' ? 'drowsiness' : kind === 'alert' ? 'system' : 'safety';
    const heading = kind === 'drowsiness'
        ? 'Drowsiness detected while driving'
        : kind === 'alert'
            ? 'System alert while driving'
            : 'Drowsiness and system alerts detected';
    const subject = kind === 'drowsiness'
        ? 'D.R.I.V.E. Safety Alert: Drowsiness detected'
        : kind === 'alert'
            ? 'D.R.I.V.E. Safety Alert: Driver activity detected'
            : 'D.R.I.V.E. Safety Alert: Drowsiness and activity detected';
    const intro = count === 1
        ? 'The detected event is listed below with the alert type, time, and trip details.'
        : 'The detected events are listed below with the alert type, time, and trip details.';

    const incidentRows = incidents
        .map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .map((value) => escapeHtml(value))
                .join(' · ');
            const trip = (incident.tripLabel ?? '').trim();
            const detail = usefulDescription(incident.description);
            return `<tr>
  <td style="padding:10px 0;border-bottom:1px solid #232A44;font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#FFFFFF;">
    <div style="font-size:11px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:${incident.category === 'drowsiness' ? '#FF8A8A' : '#FFC46B'};margin-bottom:2px;">${categoryLabel(incident.category)}</div>
    <strong>${escapeHtml(describeIncident(incident))}</strong>
    <span style="color:#FF8A8A;"> · Severity: ${severityLabel(incident.confidence)}</span>
    ${detail ? `<div style="font-size:12px;color:#A8B0CC;margin-top:4px;">${escapeHtml(detail)}</div>` : ''}
    ${when ? `<div style="font-size:12px;color:#8B93B8;margin-top:2px;">Detected ${when}</div>` : ''}
    ${trip ? `<div style="font-size:12px;color:#8B93B8;margin-top:2px;">Trip: ${escapeHtml(trip)}</div>` : ''}
  </td>
</tr>`;
        })
        .join('');

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(subject)}</title>
</head>
<body style="margin:0;padding:0;background-color:#0B0D17;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#0B0D17;">
    <tr>
      <td align="center" style="padding:40px 16px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background-color:#141828;border:1px solid #5A1F1F;border-radius:16px;">
          <tr>
            <td style="padding:28px 32px 8px 32px;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;color:#FFFFFF;letter-spacing:1px;">
              D.R.I.V.E.
            </td>
          </tr>
          <tr>
            <td style="padding:8px 32px 0 32px;font-family:Arial,Helvetica,sans-serif;">
              <h1 style="margin:0;font-size:20px;line-height:26px;color:#FF8A8A;font-weight:bold;">${heading}</h1>
              <p style="margin:10px 0 0 0;font-size:14px;line-height:21px;color:#A8B0CC;">
                ${escapeHtml(name)} was flagged by the D.R.I.V.E. on-device safety monitor. ${intro}
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 32px 16px 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                ${incidentRows}
              </table>
            </td>
          </tr>
          ${hotlinesHtml()}
          <tr>
            <td style="padding:12px 32px 28px 32px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:18px;color:#6E7694;">
              ${escapeHtml(SAFETY_NOTE)}
              <br><br>
              D.R.I.V.E. Safety Team
            </td>
          </tr>
        </table>
        <div style="max-width:520px;margin-top:16px;font-family:Arial,Helvetica,sans-serif;font-size:11px;line-height:16px;color:#4C5470;text-align:center;">
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
        `${name} was flagged by the D.R.I.V.E. on-device safety monitor.`,
        `Detected event${plural}:`,
        '',
        ...incidents.map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .join(' at ');
            const trip = (incident.tripLabel ?? '').trim();
            const detail = usefulDescription(incident.description);
            const lines = [
                `- [${categoryLabel(incident.category)}] ${describeIncident(incident)} · Severity: ${severityLabel(incident.confidence)}${detail ? ` (${detail})` : ''}`,
            ];
            if (when) lines.push(`  Detected ${when}`);
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