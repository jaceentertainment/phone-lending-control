# PhoneLending Control Service — Diagnostics, Updates & Recovery Contract v1.0.0

Status: ACTIVE SUPPLEMENTAL DESIGN / SECURITY CONTRACT
Project: PhoneLending Control
Applies to: current and future PhoneLending control-service work

Implementation status at establishment:
- Diagnostics: AUTHORIZED FOR DEVELOPMENT IMPLEMENTATION / QUALIFICATION
- Updates: AUTHORIZED FOR DEVELOPMENT IMPLEMENTATION / QUALIFICATION
- Server-assisted Recovery: CONTRACTUALLY DEFINED, IMPLEMENTATION DEFERRED UNTIL STRONGER ENFORCEMENT / RECOVERY QUALIFICATION

## 1. Purpose

This contract defines the security and authority boundary for the PhoneLending network control service that may support:

1. sanitized engineering diagnostics;
2. controlled application-update discovery/distribution; and
3. future owner/technician recovery for edge-case lockouts.

It supplements the Master Production Contract, Contract Governance & Controlled Evolution Contract, AI Engineering Decision Contract, Recovery-First Enforcement Contract, and active milestone contracts. It does not supersede their P0/P1 invariants.

The governing principle is:

> The service may observe, distribute approved software, and authorize narrowly scoped recovery; it does not become a third rental-state authority.

## 2. Protected Invariants

The following remain unchanged and outrank service convenience:

1. Host and Rental remain two separate Android applications.
2. Rental remains authoritative for canonical session state, remaining time, expiry, and local enforcement decisions.
3. The canonical state set remains exactly `UNPROVISIONED`, `AVAILABLE_LOCKED`, `ACTIVE`, `EXPIRED_LOCKED`, `ADMIN_MAINTENANCE`, and `RECOVERY_LOCKED`.
4. Loss of Internet, diagnostics service, update service, recovery service, GitHub, Vercel, or any other cloud dependency must never grant extra rental time or prevent local expiry.
5. The service must not directly create `ACTIVE`, extend paid time, erase expiry, or independently mutate canonical state.
6. All state-changing Rental behavior still passes through the central Rental state/recovery controller.
7. No arbitrary remote shell, remote arbitrary-code execution, renter surveillance, credential capture, or private-content collection is authorized.
8. Package IDs and app-signing identity continuity remain protected.
9. Recovery-First remains mandatory before stronger enforcement or removal of independent recovery avenues.

## 3. Service Trust-Domain Separation

Diagnostics, Updates, and Recovery MUST be treated as separate trust domains even if initially hosted by one provider/project.

A single credential or secret MUST NOT silently grant authority over all three planes.

At minimum:

- diagnostics-ingest credentials authorize only submission/processing of diagnostic reports;
- update-service credentials authorize only publication/delivery of update metadata/artifacts and MUST NOT contain PhoneLending APK-signing private keys;
- future recovery authorization uses a separate asymmetric recovery-signing trust root and separate operator authorization boundary;
- compromise of diagnostics must not enable update publication or recovery;
- compromise of update hosting must not enable creation of a valid PhoneLending APK without the independently protected app-signing key;
- compromise of ordinary Vercel/GitHub operational credentials must not automatically become a fleet-wide recovery/unlock capability.

Production recovery signing should use an isolated key service, HSM/KMS, or equivalently separated signing boundary rather than reusing ordinary web-service secrets.

## 4. Diagnostics Plane Contract

Diagnostics is P2/P3 engineering infrastructure and is non-authoritative.

### 4.1 Allowed purpose
Diagnostics may record and upload bounded engineering facts needed to reproduce failures, including:
- app role/version/build/channel;
- pseudonymous device/install identifier;
- Android/API/OEM/model metadata;
- canonical state and separately reported enforcement/qualification health;
- protocol stage and sanitized error codes/exceptions;
- listener/NSD/TLS/update/recovery lifecycle events;
- timing/latency measurements;
- correlation/attempt IDs;
- update/recovery result metadata.

### 4.2 Forbidden diagnostic content
Diagnostics MUST NOT intentionally upload:
- pairing tokens or QR secrets;
- private keys or signing-key material;
- Host PINs/passwords/credentials;
- renter passwords, messages, photos, files, keystrokes, contact content, app content, or unrelated private data;
- unrestricted raw memory/process dumps;
- arbitrary command payloads or signatures when a sanitized result is sufficient;
- GitHub/Vercel/server credentials.

### 4.3 Failure behavior
- Diagnostic upload is best-effort and must never block START/STATUS/expiry, boot reconciliation, enforcement, or owner recovery.
- Failure to upload diagnostics is itself a local diagnostic event, not a rental-state failure.
- A bounded local ring buffer is preferred over unbounded continuous telemetry.
- Automatic uploads should be failure/qualification oriented; production retention/privacy policy must be explicitly defined before renter deployment.
- Diagnostic reports may be signed/authenticated for provenance, but diagnostic identity MUST NOT confer command, update, or recovery authority.

## 5. Update Plane Contract

The update service may advertise and distribute approved Host/Rental releases. The server is not the source of app identity; Android package identity and PhoneLending release authorization remain independently verified.

### 5.1 Update manifest
An update manifest should bind at minimum:
- app role/package ID;
- release channel;
- version name and monotonic versionCode;
- protocol compatibility/minimum supported version where applicable;
- APK/artifact URL;
- cryptographic SHA-256 digest;
- expected PhoneLending signing-certificate fingerprint or valid approved signing lineage;
- release/manifest version;
- rollout policy and mandatory/optional status;
- release identifier and notes suitable for audit.

For unattended/managed updates, the manifest/release authorization MUST be cryptographically authenticated independently of ordinary HTTPS hosting so that compromising only the update host is insufficient to authorize an arbitrary release.

### 5.2 APK signing separation
- PhoneLending APK-signing private keys MUST NOT be stored in the update service, diagnostics service, client APK, normal source repository, or public CI artifact.
- A downloaded APK must be rejected before install if package ID, version rules, digest, or signing identity do not match the authorized manifest/pinned release line.
- The server must never instruct a client to bypass Android signature/version protections.

### 5.3 Install behavior
- During ordinary development/sideload operation, user action may still be required by Android and must be handled honestly.
- Silent/managed installation is not considered qualified merely because an API exists; it is allowed only after the applicable managed-device authority and recovery path are qualified.
- Downloading an update during `ACTIVE` may be allowed if it does not materially disturb the renter, but installation/restart should default to a safe non-`ACTIVE` maintenance/available window unless a separately approved critical-update policy says otherwise.
- An update must not manufacture extra rental time, clear expiry, or silently reset canonical state.
- After update/reboot, Rental must reconcile persisted authoritative state before exposing normal use.

### 5.4 Update failure and rollback
- Hash/signature/compatibility failure blocks installation and produces diagnostics.
- Install failure must preserve the safest available currently working version/path where Android permits.
- PhoneLending MUST NOT promise automatic application rollback unless rollback and data-schema compatibility have been physically qualified on the applicable device/API path.
- Every release that can affect enforcement or recovery requires update-continuity and post-update recovery tests before stronger deployment.

## 6. Future Server-Assisted Recovery Plane Contract

Server-assisted recovery is defined now because stronger locking must not be designed first and recovery bolted on later. It is NOT implemented or production-qualified by this contract alone.

The recovery service is an additional break-glass recovery plane for business-owned/authorized devices that become stranded in an edge-case enforcement condition, including examples such as:
- canonical state and physical enforcement disagree;
- lock-entry succeeds but lock-exit fails;
- launcher/HOME or Lock Task policy becomes inconsistent;
- Rental process/update/reboot leaves the device in an unexpected restricted condition;
- normal Host-to-Rental management is unavailable;
- an approved enforcement adapter reports unhealthy/unknown state.

Server-assisted recovery MUST NOT become the only recovery route. Loss of Internet/server access must not by itself make an authorized device unrecoverable.

## 7. Recovery Authority Model

The recovery server does not directly mutate Rental state. It may issue a narrowly scoped, signed Recovery Authorization that Rental verifies locally and passes to the central RecoveryController/state controller.

A recovery authorization MUST be bound to sufficient context to prevent reuse on another phone or another recovery attempt, including as applicable:
- target Rental/device identity;
- requested recovery action/scope;
- one-time Rental-generated challenge/nonce;
- recovery-attempt identifier;
- recovery-key/version identifier;
- software/protocol compatibility bounds;
- issuance/expiry information where reliable;
- replay/consumption state.

Recovery authorization MUST be asymmetric: Rental stores only the public verification trust required to verify recovery authorization. The recovery private signing key must never be embedded in Host or Rental.

A recovery token is authorization, not an instruction to become `ACTIVE`.

## 8. Allowed Future Recovery Actions

Future qualified recovery actions may include narrowly scoped operations such as:
- enter controlled recovery/maintenance;
- relax or unwind a failed enforcement profile;
- restore an approved launcher/HOME/policy baseline;
- apply an approved recovery/update package or policy;
- exit an experimental enforcement adapter;
- provide owner/technician access necessary to repair the device.

Recovery MUST NOT authorize:
- adding/extending paid rental time;
- arbitrary transition directly to `ACTIVE`;
- arbitrary shell execution;
- unrestricted remote code execution;
- renter-accessible administrative authority;
- bypass of app-signing/release verification.

Normal recovery semantics should preserve the existing authoritative rental condition and use `RECOVERY_LOCKED` and/or `ADMIN_MAINTENANCE` as already defined by the canonical state machine.

## 9. Online and Offline Recovery Requirement

Future recovery qualification should support layered paths:

1. normal authenticated Host/owner maintenance where available;
2. online server-assisted signed recovery when Rental still has trusted network access;
3. offline challenge-response where the stuck Rental can generate a one-time challenge, another trusted device obtains a signed recovery response from the server, and Rental verifies that response locally without requiring its own Internet connection;
4. qualified technician/development recovery where platform/device allows;
5. documented destructive salvage/factory-reset/reprovision route as final fallback until an equal-or-stronger replacement is proven.

Offline recovery tokens must be one-time/replay resistant. If reliable wall-clock time cannot be assumed, one-time challenge binding and local consumption state must remain sufficient to prevent indefinite generic unlock codes.

## 10. Recovery Fail-Safe Rules

- A rejected, malformed, expired, replayed, wrong-device, wrong-key-version, or incompatible recovery authorization must fail closed with a clear diagnostic and must not grant renter access.
- Recovery-service unavailability must not alter canonical state.
- Recovery failure must not cause automatic strengthening of restrictions as compensation.
- A recovery action must verify the resulting enforcement/maintenance condition rather than report success from request issuance alone.
- A server-side `RECOVERY_SENT` event is not proof that the phone recovered; Rental confirmation/verification is required.

## 11. Service Availability / Offline Independence

PhoneLending core rental operation remains local-first:

- Rental expiry does not depend on the server;
- Host/Rental LAN management must not be silently routed through the cloud merely because the service exists;
- diagnostics may queue/drop safely;
- update checks may be delayed safely;
- online recovery is additive and must have an offline/local fallback;
- cloud outage is an operational degradation, not an automatic security-state transition.

## 12. Qualification / Regression Matrix

Before each plane is considered production-capable, applicable testing must cover:

### Diagnostics
- network/server unavailable;
- malformed/rejected report;
- redaction checks for secrets/private renter data;
- bounded storage/retry behavior;
- no impact on timer/expiry/Host commands.

### Updates
- valid update;
- wrong package;
- wrong signer;
- corrupted hash;
- stale/downgrade manifest;
- interrupted download/install;
- reboot during/after update where applicable;
- incompatible protocol/version;
- post-update state/timer reconciliation;
- post-update enforcement and recovery health;
- safe behavior when update server is unavailable.

### Future Recovery
- Host unavailable;
- Rental network unavailable;
- online recovery;
- offline challenge-response;
- replayed/wrong-device recovery token;
- recovery signer/key rotation;
- process crash/reboot while restricted;
- bad enforcement policy;
- failed/incompatible update;
- recovery-result verification;
- destructive salvage route.

## 13. Current Milestone Boundary

This contract does not authorize production locking or production server recovery in the v0.5.3 timer-qualification build.

Near-term implementation may add diagnostics and update discovery/download qualification while physical enforcement remains explicitly `UNQUALIFIED`.

Server-assisted recovery remains a required future design constraint and qualification target to be implemented only when stronger enforcement work reaches the corresponding Recovery-First gate.

## 14. Controlled Evolution

This contract is frozen against silent drift but may evolve through the project-wide controlled-evolution process.

Changes that merely replace hosting/provider/transport while preserving these authority and security boundaries are normally P2. Changes that let the cloud create rental time, become the sole recovery authority, weaken signer continuity, expose renter private data, or bypass the central Rental state/recovery controller are P0/P1 changes and require explicit higher-level amendment/authorization.

---

Established after field testing of v0.5.3 exposed the need for remotely inspectable diagnostics, controlled update distribution, and a future server-assisted edge-case recovery plane before stronger locking is qualified.