# PhoneLending Control Service — Phase 1

Minimal, non-authoritative Vercel service for the v0.5.4 qualification lane.

Endpoints:
- `POST /api/report` — receives a strict allowlist of sanitized engineering diagnostics and writes one `PL_DIAG` line to Vercel runtime logs.
- `GET /api/version?role=rental&channel=field&versionCode=10` — read-only update discovery manifest.

This service has no endpoint that can start/end/extend a rental, mutate canonical state, unlock a phone, or issue recovery authorization. Server-assisted recovery remains contract-only and deferred.

Deploy this directory as the Vercel project root. After deployment, set the generated HTTPS origin in both Android `ControlServiceConfig.java` files before producing a field APK.
