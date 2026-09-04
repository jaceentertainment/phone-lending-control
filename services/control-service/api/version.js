const RELEASES = {
  'host:dev': {
    latestVersionName: '0.5.4-dev',
    latestVersionCode: 8,
    apkUrl: '',
    sha256: '',
    signingCertSha256: 'e7da706d3a4692b6603b311d0856fe557815ee9db59d9399b04910875c67352f',
  },
  'rental:field': {
    latestVersionName: '0.5.4-field',
    latestVersionCode: 10,
    apkUrl: '',
    sha256: '',
    signingCertSha256: '8b46873ee7d9fec4cecc2faa03694e8001e1ad42accf862257222f74174936c9',
  },
};

module.exports = (request, response) => {
  response.setHeader('Cache-Control', 'public, max-age=60, s-maxage=60');
  if (request.method !== 'GET') {
    response.setHeader('Allow', 'GET');
    return response.status(405).json({ ok: false, error: 'method_not_allowed' });
  }

  const role = String(request.query.role || '').toLowerCase();
  const channel = String(request.query.channel || '').toLowerCase();
  const currentVersionCode = Number(request.query.versionCode || 0);
  const release = RELEASES[`${role}:${channel}`];
  if (!release) return response.status(404).json({ ok: false, error: 'unknown_release_channel' });

  const artifactReady = Boolean(release.apkUrl && release.sha256 && release.signingCertSha256);
  return response.status(200).json({
    schema: 1,
    role,
    channel,
    latestVersionName: release.latestVersionName,
    latestVersionCode: release.latestVersionCode,
    apkUrl: release.apkUrl,
    sha256: release.sha256,
    signingCertSha256: release.signingCertSha256,
    artifactReady,
    updateAvailable: artifactReady && currentVersionCode > 0 && currentVersionCode < release.latestVersionCode,
  });
};
