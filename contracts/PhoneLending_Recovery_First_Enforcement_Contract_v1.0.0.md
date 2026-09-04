# PhoneLending Recovery-First Enforcement Contract v1.0.0

Status: FROZEN SUPPLEMENTAL CONTRACT
Project: PhoneLending Control

## 1. Purpose

This contract makes owner recoverability a prerequisite for stronger Android enforcement. PhoneLending must never treat successful locking as sufficient proof that a locking mechanism is safe for deployment.

The governing principle is:

> Recovery is qualified before enforcement is strengthened.

A device that can be locked but cannot be reliably recovered by an authorized owner or technician is NOT a successful PhoneLending implementation.

## 2. Scope

This contract governs all changes that increase Rental-side control over Android, including but not limited to:

- Device Owner / fully managed provisioning;
- Lock Task or kiosk enforcement;
- launcher or HOME control;
- restrictions on Settings, Safe Boot, factory reset, debugging, USB, accounts, or app management;
- persistent post-reboot enforcement;
- device-lock or OEM-specific management APIs;
- any mechanism whose failure could strand an authorized owner outside normal device control.

It supplements, and does not replace, the Master Production Contract, AI Engineering Decision Contract, Early Development Soft Lock Addendum, Stage-A Onboarding Amendment, or any future approved architecture contract.

## 3. Protected Architecture Invariants

This contract does not authorize changes to the following core PhoneLending invariants:

1. Host and Rental remain separate Android applications.
2. Rental remains authoritative for canonical session state, authoritative remaining time, expiry, and signed-command acknowledgement.
3. The canonical state set remains exactly:
   - `UNPROVISIONED`
   - `AVAILABLE_LOCKED`
   - `ACTIVE`
   - `EXPIRED_LOCKED`
   - `ADMIN_MAINTENANCE`
   - `RECOVERY_LOCKED`
4. Locking/enforcement mechanisms are implementation adapters and must not redefine canonical state semantics.
5. Host must never become authoritative for the Rental timer or physical lock state.
6. Authentication, replay protection, version compatibility, and transactional command acknowledgement must not be weakened to simplify locking.
7. Recovery mechanisms must not silently grant renter-accessible administrative authority.

## 4. Recovery-First Gate

Before a stronger enforcement mechanism may advance beyond experimental qualification, PhoneLending must prove an authorized recovery path that is independent enough to remain useful when the normal Rental UI or normal Host workflow fails.

At minimum, development qualification must prove:

- authorized owner/technician maintenance entry;
- recovery after Rental process crash or force-stop where platform behavior permits;
- recovery after reboot while a locked canonical state is persisted;
- recovery after a malformed or corrupted local state condition;
- recovery when Host is unavailable or offline;
- recovery after a failed or incompatible app update scenario that can be safely simulated;
- a documented physical/destructive salvage route for the actual qualification device class;
- an out-of-band development recovery route where technically available, including ADB/testOnly recovery during development;
- the ability to remove or unwind experimental enforcement without permanently stranding the device.

If any mandatory recovery test fails, stronger hardening is BLOCKED until the failure is corrected and re-qualified.

## 5. Development Hardening Order

PhoneLending must strengthen enforcement in stages rather than enabling all restrictions at once.

The default order is:

1. establish recoverable management authority;
2. prove lock entry;
3. prove lock exit;
4. prove `AVAILABLE_LOCKED -> ACTIVE -> EXPIRED_LOCKED -> ACTIVE/AVAILABLE_LOCKED` transitions;
5. prove reboot reconciliation;
6. prove owner maintenance and independent recovery;
7. prove update continuity;
8. only then consider closing additional escape routes.

Restrictions such as disabling factory reset, Safe Boot, debugging, or other independent recovery avenues must remain OFF until the corresponding recovery replacement has been separately demonstrated and approved.

## 6. No False Equivalence Between Locking Mechanisms

PhoneLending must not report weaker mechanisms as equivalent to production-grade managed enforcement.

Examples:

- screen pinning is not equivalent to Device Owner Lock Task;
- an overlay is not equivalent to managed-device enforcement;
- Accessibility automation is not equivalent to Android enterprise authority;
- foreground-activity tricks are not equivalent to a verified locked device state.

A mechanism may be used for development workflow testing only when its limitations are explicit and it does not masquerade as production-qualified enforcement.

## 7. Verification Before Reporting Success

PhoneLending must distinguish between REQUESTING enforcement and VERIFYING enforcement.

A lock operation is not considered healthy merely because an API call returned or an Activity was launched. Where the Android platform exposes an observable enforcement state, PhoneLending must verify that state before reporting enforcement as healthy.

Similarly, unlock/release must be verified before the device is reported as normally usable.

## 8. Mixed-Fleet / Capability-Driven Requirement

PhoneLending must not encode its core enforcement architecture around a hardcoded list of phone models.

Compatibility decisions must primarily be based on runtime capabilities, Android API behavior, provisioning availability, management authority, and verified enforcement/recovery results.

Device make, model, OS build, and OEM skin may be recorded for diagnostics and qualification history, but they are evidence metadata rather than the primary architectural switch.

If a device lacks a capability required to satisfy the current production enforcement contract, PhoneLending must mark the device as NOT QUALIFIED rather than silently falling back to materially weaker security.

## 9. Failure Behavior

When enforcement health is uncertain:

- preserve the authoritative canonical state;
- do not fabricate a successful lock/unlock status;
- surface a clear owner/operator diagnostic;
- preserve the safest available recovery route;
- do not automatically add stronger restrictions to compensate for the failure.

A failure in presentation/enforcement must never rewrite Rental authority or create an unauthorized extension of rental time.

## 10. Anti-Brick Priority

During development and qualification, avoiding an unrecoverable business-owned phone has priority over demonstrating maximum enforcement strength.

A stronger lock that introduces an unqualified brick/stranding risk is a regression even if it blocks the renter more effectively.

Production hardening may only remove a recovery path after an equal-or-stronger authorized recovery path has been demonstrated, documented, regression-tested, and explicitly approved.

## 11. Controlled Evolution

This contract is frozen against silent drift, but it is not intended to prevent evidence-driven evolution.

Changes are allowed only through controlled amendment when Android platform behavior, OEM behavior, verified field evidence, security findings, or business requirements demonstrate that the existing implementation rule is insufficient.

Any amendment must:

1. identify the evidence or requirement that necessitates change;
2. classify whether the change affects a core architecture invariant or only an implementation/capability rule;
3. preserve the project's mission, two-app architecture, canonical states, Rental authority, security boundaries, and anti-brick priority unless an explicit higher-level architecture amendment authorizes otherwise;
4. document compatibility and migration consequences;
5. define new or updated regression/recovery tests;
6. increment the contract version;
7. update the project changelog/handoff record;
8. receive explicit user authorization before implementation when a frozen invariant or contract rule changes.

No AI, build script, CI process, or runtime component may silently reinterpret this contract merely because a different implementation would be easier.

## 12. Acceptance Rule

A future PhoneLending locking mechanism may be considered for stronger deployment only when BOTH are true:

- enforcement behavior meets the required rental-control contract; AND
- authorized recovery has passed the applicable recovery qualification matrix.

`LOCK WORKS` without `RECOVERY WORKS` is a failed qualification.

---

Frozen supplemental contract established by explicit user instruction during the v0.5.2 professional-UI / locking-architecture re-audit.
