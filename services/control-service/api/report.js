const MAX_BODY_CHARS = 16_384;
const MAX_EVENTS = 50;
const MAX_TEXT = 320;

function clean(value) {
  const s = String(value ?? '').replace(/[\r\n]+/g, ' ').trim();
  return s.slice(0, MAX_TEXT);
}

function allowEvent(value) {
  if (!value || typeof value !== 'object') return null;
  return {
    at: Number.isFinite(Number(value.at)) ? Number(value.at) : 0,
    stage: clean(value.stage),
    detail: clean(value.detail),
  };
}

module.exports = (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  if (request.method !== 'POST') {
    response.setHeader('Allow', 'POST');
    return response.status(405).json({ ok: false, error: 'method_not_allowed' });
  }

  let body = request.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch { body = null; }
  }
  if (!body || typeof body !== 'object') {
    return response.status(400).json({ ok: false, error: 'invalid_json' });
  }
  if (JSON.stringify(body).length > MAX_BODY_CHARS) {
    return response.status(413).json({ ok: false, error: 'report_too_large' });
  }

  const events = Array.isArray(body.events)
    ? body.events.slice(-MAX_EVENTS).map(allowEvent).filter(Boolean)
    : [];

  const report = {
    schema: Number(body.schema) || 1,
    role: clean(body.role),
    deviceId: clean(body.deviceId),
    reason: clean(body.reason),
    detail: clean(body.detail),
    sentAtEpochMs: Number(body.sentAtEpochMs) || 0,
    receivedAt: new Date().toISOString(),
    androidSdk: Number(body.androidSdk) || 0,
    manufacturer: clean(body.manufacturer),
    model: clean(body.model),
    packageName: clean(body.packageName),
    versionName: clean(body.versionName),
    versionCode: Number(body.versionCode) || 0,
    errorType: clean(body.errorType),
    frames: Array.isArray(body.frames) ? body.frames.slice(0, 6).map(clean) : [],
    events,
  };

  if (!['host', 'rental'].includes(report.role)) {
    return response.status(400).json({ ok: false, error: 'invalid_role' });
  }

  console.log('PL_DIAG ' + JSON.stringify(report));
  return response.status(202).json({ ok: true });
};
