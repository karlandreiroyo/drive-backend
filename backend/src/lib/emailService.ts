import { env } from '../config/env.js';

export type EmergencyIncident = {
    incidentType: string;
    confidence: number;
    description: string;
    time: string;
};

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
 * Emergency fallback for critical incidents that were detected offline and
 * uploaded later. Delivered through Brevo to the account email on file.
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
    const subject = `D.R.I.V.E. emergency alert — ${count} critical incident${count === 1 ? '' : 's'}`;

    const incidentRows = incidents
        .map((incident) => {
            const percent = Math.round(incident.confidence * 100);
            return `<tr>
  <td style="padding:10px 0;border-bottom:1px solid #232A44;font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#FFFFFF;">
    <strong>${escapeHtml(incident.incidentType)}</strong>
    <span style="color:#FF8A8A;"> · ${percent}% confidence</span>
    <div style="font-size:12px;color:#A8B0CC;margin-top:4px;">${escapeHtml(incident.description)}</div>
    <div style="font-size:12px;color:#8B93B8;margin-top:2px;">Detected around ${escapeHtml(incident.time)}</div>
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
              <h1 style="margin:0;font-size:20px;line-height:26px;color:#FF8A8A;font-weight:bold;">Critical driving incident detected</h1>
              <p style="margin:10px 0 0 0;font-size:14px;line-height:21px;color:#A8B0CC;">
                ${escapeHtml(name)} was flagged by the on-device safety monitor. These incidents were stored offline and synced once a connection was available.
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
        'D.R.I.V.E. Emergency Alert',
        '',
        'Critical driving incident detected.',
        `${name} was flagged by the on-device safety monitor.`,
        'These incidents were stored offline and synced once a connection was available.',
        '',
        ...incidents.map((incident) => {
            const percent = Math.round(incident.confidence * 100);
            return `- ${incident.incidentType} · ${percent}% confidence at ${incident.time} (${incident.description})`;
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
        console.log(`[emergencyEmail] Sent ${count} critical incident(s) to ${to}`);
        return true;
    } catch (error) {
        console.error('[emergencyEmail] Brevo request failed:', error);
        return false;
    }
}
