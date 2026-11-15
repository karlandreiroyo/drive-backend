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

/**
 * Drowsiness / important system-alert email. Detected events are uploaded from
 * the phone, so they may also cover incidents that were stored offline and
 * flushed once a connection was available. Delivered through Brevo to the
 * account email on file, with no credentials or SMTP details on the client.
 */
export async function sendEmergencyIncidentEmail(
    to: string,
    driverName: string | null,
    incidents: EmergencyIncident[]
): Promise<boolean> {
    const apiKey = env.brevoApiKey;
    if (!apiKey || incidents.length === 0) return false;

    const name = driverName?.trim() || 'A D.R.I.V.E. driver';
    const count = incidents.length;
    const kind = batchKind(incidents);
    const kindWord = kind === 'drowsiness' ? 'drowsiness' : kind === 'alert' ? 'system' : 'safety';
    const heading = kind === 'drowsiness'
        ? 'Drowsiness detected while driving'
        : kind === 'alert'
            ? 'System alert while driving'
            : 'Drowsiness and system alerts detected';
    const subject = `D.R.I.V.E. ${kindWord} alert — ${count} detected event${count === 1 ? '' : 's'}`;

    const incidentRows = incidents
        .map((incident) => {
            const percent = Math.round(incident.confidence * 100);
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .map((value) => escapeHtml(value))
                .join(' · ');
            const trip = (incident.tripLabel ?? '').trim();
            return `<tr>
  <td style="padding:10px 0;border-bottom:1px solid #232A44;font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#FFFFFF;">
    <div style="font-size:11px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:${incident.category === 'drowsiness' ? '#FF8A8A' : '#FFC46B'};margin-bottom:2px;">${categoryLabel(incident.category)}</div>
    <strong>${escapeHtml(incident.incidentType)}</strong>
    <span style="color:#FF8A8A;"> · ${percent}% confidence</span>
    <div style="font-size:12px;color:#A8B0CC;margin-top:4px;">${escapeHtml(incident.description)}</div>
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
                ${escapeHtml(name)} was flagged by the D.R.I.V.E. on-device safety monitor. The detected event${count === 1 ? '' : 's'} are listed below with the alert type, time, and trip details.
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 32px 0 32px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                ${incidentRows}
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:20px 32px 28px 32px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:18px;color:#6E7694;">
              If the driver is safe, no action is needed. Otherwise, please contact them and make sure they are able to rest before continuing to drive.
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
        `Detected event${count === 1 ? '' : 's'}:`,
        '',
        ...incidents.map((incident) => {
            const percent = Math.round(incident.confidence * 100);
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .join(' at ');
            const trip = (incident.tripLabel ?? '').trim();
            const lines = [
                `- [${categoryLabel(incident.category)}] ${incident.incidentType} · ${percent}% confidence (${incident.description})`,
            ];
            if (when) lines.push(`  Detected ${when}`);
            if (trip) lines.push(`  Trip: ${trip}`);
            return lines.join('\n');
        }),
        '',
        'If the driver is safe, no action is needed. Otherwise, please contact them and make sure they rest before continuing to drive.',
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
            return false;
        }
        console.log(`[emergencyEmail] Sent ${count} ${kindWord} alert incident(s) to ${to}`);
        return true;
    } catch (error) {
        console.error('[emergencyEmail] Brevo request failed:', error);
        return false;
    }
}
