# PhoneLending Phase-1 Connectivity / Diagnostics / Update-Check Addendum v0.5.4

Status: ACTIVE MILESTONE CONTRACT
Project: PhoneLending Control
Applies to: v0.5.4 Phase-1 connectivity field build only

## 1. Purpose

This addendum authorizes the next bounded qualification milestone after v0.5.3. Its goal is to make the already-protected pairing/timer lane physically testable and diagnosable without introducing production locking.

Authorized scope is limited to:
1. QR/listener readiness and local endpoint ordering;
2. Android Keystore/TLS identity compatibility required by the existing pinned TLS transport;
3. sanitized, non-authoritative development diagnostics to the PhoneLending control service; and
4. read-only update discovery plumbing.

Server-assisted Recovery remains governed by the Control Service contract but is NOT implemented by this milestone.

## 2. Relationship to v0.5.3

The v0.5.4 field build continues the v0.5.3 timer/pairing qualification semantics, including the explicit separation between canonical desired state and unqualified physical enforcement. The Stage-A overlay prerequisite remains bypassed only for this governed qualification lane.

The six canonical states, Rental timer/state authority, signed Host↔Rental command protocol, package IDs, and signing identities remain unchanged.

## 3. QR / Discovery Rule

A scanned pairing QR is the primary bootstrap artifact. Rental should render it as soon as a secure listener and direct LAN endpoint are usable. Asynchronous NSD/mDNS registration MUST NOT block QR presentation.

Host MUST try direct endpoint hints carried by the scanned QR before waiting on NSD. NSD remains a resilience/fallback mechanism for discovery and later address changes.

The UI MUST NOT state “Waiting for Host” before a usable QR is actually visible.

## 4. TLS Identity Compatibility

The existing pinned Rental TLS identity remains the trust model. The Android Keystore key used by Rental must be authorized for the operations required by Android TLS server authentication on supported devices.

An unpaired v0.5.3 qualification phone may rotate a legacy TLS-incompatible Rental identity during upgrade because no Host trust has been established. An already-paired identity MUST NOT be silently rotated by an update. If an already-paired legacy identity is incompatible, PhoneLending must require an explicit governed re-pair/recovery path rather than fabricating continuity.

No certificate-authority trust or public Internet PKI is substituted for the QR-pinned Rental identity.

## 5. Diagnostics Boundary

Diagnostics is optional and non-authoritative. It may upload sanitized engineering metadata/events under the active Control Service contract. Upload failure must never block pairing, timer, expiry, boot reconciliation, or future recovery.

The Phase-1 implementation should use a bounded local event buffer and upload only explicit reports/failures. It must not upload pairing tokens, QR payloads, private keys, PINs, renter content, or arbitrary memory/process data.

## 6. Update-Check Boundary

This milestone authorizes update DISCOVERY only. The app may query a service manifest and remember whether a newer approved version is advertised.

This milestone does NOT authorize silent installation, APK signer bypass, automatic rollback, update-time canonical-state mutation, or server authority over the Rental timer. Download/install behavior remains a later separately qualified step.

## 7. Acceptance Gate

Phase 1 passes only if physical testing demonstrates:
- Rental reaches a usable QR promptly after listener startup;
- “Waiting for Host” is shown only once Host can actually scan a usable QR;
- Host tries the QR endpoint without an unnecessary NSD-first delay;
- TLS handshake succeeds with the QR-pinned Rental identity on the target test phone;
- pairing completes and signed Host START reaches Rental;
- Rental owns and displays the countdown;
- diagnostic service outage does not affect the above;
- a forced pairing/TLS failure creates a sanitized diagnostic report when the service is available; and
- update-check failure is non-blocking.

Physical lock enforcement remains NOT QUALIFIED after this milestone.
