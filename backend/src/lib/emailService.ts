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
    'This is an automated safety notification. False alerts can occasionally happen due to rough roads, sudden stops, or lighting changes. ' +
    'If the driver confirms they are safe, no further action is needed. Otherwise, please advise them to pull over and rest before continuing their trip.';

function categoryLabel(category: AlertCategory | undefined): string {
    return category === 'drowsiness' ? 'Drowsiness Alert' : 'System Alert';
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

/** Safe HTML escaping so copy-pasting never breaks quotes or entities. */
function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&' + 'amp;')
        .replace(/</g, '&' + 'lt;')
        .replace(/>/g, '&' + 'gt;')
        .replace(/"/g, '&' + 'quot;')
        .replace(/'/g, '&' + '#39;');
}

/**
 * Detailed, user-friendly warning explanation instead of vague system labels.
 */
function describeIncident(incident: EmergencyIncident): { headline: string; guidance: string } {
    const type = (incident.incidentType ?? '').trim().toLowerCase();

    if (type === 'drowsiness' || incident.category === 'drowsiness') {
        return {
            headline: 'Possible Driver Drowsiness or Fatigue',
            guidance:
                'The driver showed signs of prolonged stillness or reduced alertness while on the road. Please check in on them to make sure they are awake and able to take a safe rest stop.',
        };
    }

    if (type === 'motion') {
        return {
            headline: 'Abrupt or Unusual Activity During Drive',
            guidance:
                'A sudden or sharp shift was recorded during the trip, which may happen during hard braking, swerving, or bumpy road conditions. Please reach out to confirm the driver is safe.',
        };
    }

    return {
        headline: 'Unusual Driving Activity Flagged',
        guidance:
            'The safety monitor noticed an irregular event during the trip that may require attention. Please contact the driver when safe to verify their condition.',
    };
}

/** Low / Medium / High wording. */
function severityLabel(confidence: number): string {
    if (confidence >= 0.8) return 'High';
    if (confidence >= 0.5) return 'Medium';
    return 'Low';
}

function severityBadgeStyle(confidence: number): string {
    if (confidence >= 0.8) {
        return 'background-color:#3B151E;color:#FF8A8A;border:1px solid #7F2433;';
    }
    if (confidence >= 0.5) {
        return 'background-color:#382812;color:#FFD07B;border:1px solid #75511E;';
    }
    return 'background-color:#132B3A;color:#7DD3FC;border:1px solid #225473;';
}

function hotlinesHtml(): string {
    const rows = HOTLINES.map(
        (h) => `<tr>
  <td style="padding:8px 14px 8px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#CBD5E1;border-bottom:1px solid #3A1D28;">
    ${escapeHtml(h.label)}
  </td>
  <td align="right" style="padding:8px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#FFFFFF;font-weight:bold;white-space:nowrap;border-bottom:1px solid #3A1D28;">
    ${escapeHtml(h.number)}
  </td>
</tr>`
    ).join('');

    return `<tr>
  <td style="padding:8px 36px 20px 36px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #69232B;border-radius:14px;background-color:#1E111B;">
      <tr>
        <td style="padding:20px 24px 10px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:16px;font-weight:bold;color:#FF8A8A;">
          If the driver may be in danger, call:
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

    const intro = count === 1
        ? `<strong style="color:#FFFFFF;">${escapeHtml(name)}</strong> triggered a safety check while on the road. Review the details below and reach out to confirm they are safe.`
        : `<strong style="color:#FFFFFF;">${escapeHtml(name)}</strong> triggered multiple safety checks while on the road. Review the details below and reach out to confirm they are safe.`;

    const incidentRows = incidents
        .map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .map((value) => escapeHtml(value))
                .join(' • ');
            const trip = (incident.tripLabel ?? '').trim();
            const sev = severityLabel(incident.confidence);
            const isDrowsiness = incident.category === 'drowsiness';
            const info = describeIncident(incident);

            return `<tr>
  <td style="padding:20px 22px;background-color:#1A2035;border:1px solid #2B3452;border-radius:12px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
      <tr>
        <td style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:13px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:${isDrowsiness ? '#FF8A8A' : '#FFD07B'};">
          ${categoryLabel(incident.category)}
        </td>
        <td align="right">
          <span style="display:inline-block;padding:4px 12px;border-radius:999px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:13px;font-weight:bold;${severityBadgeStyle(incident.confidence)}">
            Severity: ${sev}
          </span>
        </td>
      </tr>
    </table>
    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:18px;line-height:24px;font-weight:bold;color:#FFFFFF;margin-top:8px;">
      ${escapeHtml(info.headline)}
    </div>
    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:15px;line-height:22px;color:#CBD5E1;margin-top:6px;">
      ${escapeHtml(info.guidance)}
    </div>
    <div style="margin-top:12px;padding-top:10px;border-top:1px solid #2B3452;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:14px;line-height:21px;color:#94A3B8;">
      ${when ? `<div><strong style="color:#CBD5E1;">Detected:</strong> ${when}</div>` : ''}
      ${trip ? `<div style="margin-top:2px;"><strong style="color:#CBD5E1;">Trip:</strong> ${escapeHtml(trip)}</div>` : ''}
    </div>
  </td>
</tr>
<tr><td style="height:14px;"></td></tr>`;
        })
        .join('');

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(subject)}</title>
</head>
<body style="margin:0;padding:0;background-color:#0B0E18;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#0B0E18;">
    <tr>
      <td align="center" style="padding:40px 16px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:640px;background-color:#14192B;border:1px solid #69232B;border-radius:18px;">
          <tr>
            <td style="padding:32px 36px 8px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:24px;font-weight:bold;color:#FFFFFF;letter-spacing:1.5px;">
              D.R.I.V.E.
            </td>
          </tr>
          <tr>
            <td style="padding:10px 36px 0 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
              <h1 style="margin:0;font-size:26px;line-height:34px;color:#FF8A8A;font-weight:bold;">
                ${heading}
              </h1>
              <p style="margin:14px 0 0 0;font-size:16px;line-height:25px;color:#CBD5E1;">
                ${intro}
              </p>
            </td>
          </tr>
          <tr>
            <td style="padding:22px 36px 12px 36px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                ${incidentRows}
              </table>
            </td>
          </tr>
          ${hotlinesHtml()}
          <tr>
            <td style="padding:12px 36px 34px 36px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:14px;line-height:22px;color:#94A3B8;">
              ${escapeHtml(SAFETY_NOTE)}
              <br><br>
              <strong style="color:#CBD5E1;">D.R.I.V.E. Safety Team</strong>
            </td>
          </tr>
        </table>
        <div style="max-width:640px;margin-top:18px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;font-size:13px;line-height:18px;color:#64748B;text-align:center;">
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
        `${name} triggered a safety check while on the road. Review the details below and reach out to confirm they are safe.`,
        `Alert detail${plural}:`,
        '',
        ...incidents.map((incident) => {
            const when = [incident.date, incident.time]
                .map((value) => (value ?? '').trim())
                .filter((value) => value.length > 0)
                .join(' at ');
            const trip = (incident.tripLabel ?? '').trim();
            const info = describeIncident(incident);
            const lines = [
                `[${categoryLabel(incident.category)}] ${info.headline} (Severity: ${severityLabel(incident.confidence)})`,
                `  ${info.guidance}`,
            ];
            if (when) lines.push(`  Detected: ${when}`);
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