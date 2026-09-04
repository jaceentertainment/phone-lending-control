# PhoneLending Timer & Pairing Qualification Addendum v0.5.3

Status: ACTIVE MILESTONE CONTRACT
Project: PhoneLending Control
Applies to: v0.5.3 timer/pairing qualification field build only

## 1. Purpose

This addendum authorizes a bounded development lane whose sole goal is to physically test the already-protected Host↔Rental pairing, signed command flow, Rental-authoritative timer, state persistence, and expiry transition on Android devices where the v0.5.x Stage-A overlay adapter is unavailable or unsuitable.

It does not authorize production rental deployment and it does not qualify physical lock enforcement.

## 2. Relationship to Earlier Stage-A Contracts

For the v0.5.3 timer/pairing qualification field build only, this addendum supersedes the requirement in the Early Development Soft Lock Addendum and Stage-A Onboarding Amendment that overlay permission must be granted before Host pairing or START testing.

Those earlier contracts remain authoritative historical descriptions of the v0.5.1/v0.5.2 soft-overlay milestone and remain applicable to those builds. They are not permanent platform architecture.

This addendum does not weaken the Master Production Contract, Governance Contract, AI Engineering Decision Contract, or Recovery-First Enforcement Contract.

## 3. Protected Invariants

The following remain unchanged:

1. Exactly two Android applications: Host and Rental/Consumer.
2. Exactly six canonical states: `UNPROVISIONED`, `AVAILABLE_LOCKED`, `ACTIVE`, `EXPIRED_LOCKED`, `ADMIN_MAINTENANCE`, `RECOVERY_LOCKED`.
3. Rental remains authoritative for canonical session state, remaining time, expiry, and signed acknowledgement.
4. Host remains a management/request UI and never becomes the timer authority.
5. Pairing and management commands remain authenticated, versioned, signed, replay-protected, and transactionally acknowledged.
6. Package IDs and pinned development signing identities remain unchanged.
7. No overlay, screen-pinning, Accessibility automation, foreground relaunch trick, or other weak mechanism may be reported as production-equivalent enforcement.
8. Recovery-First remains mandatory before stronger management restrictions are accepted.

## 4. Qualification-State Semantics

In this v0.5.3 lane, canonical state and physical enforcement health are explicitly separate dimensions.

After successful authenticated Host pairing, the qualification build may transition from `UNPROVISIONED` to `AVAILABLE_LOCKED` for the purpose of exercising the canonical rental workflow even though physical lock enforcement is not qualified.

`AVAILABLE_LOCKED` and `EXPIRED_LOCKED` therefore express the authoritative rental policy state that should be enforced; they MUST NOT be presented as proof that Android has physically enforced the lock.

The build must simultaneously and conspicuously report enforcement health as `UNQUALIFIED` / `NOT QUALIFIED` wherever an operator could otherwise mistake the canonical locked state for verified physical enforcement.

This exception is limited to the explicitly marked v0.5.3 qualification build and must not silently propagate into a production build.

## 5. Allowed Test Flow

The v0.5.3 field build may perform:

1. normal app installation/update using the pinned Rental development signer;
2. Rental secure pairing QR presentation without requiring overlay permission;
3. authenticated Host pairing;
4. `AVAILABLE_LOCKED` desired-state readiness for timer testing;
5. signed Host `START` with a selected duration;
6. Rental-local `ACTIVE` countdown using the existing authoritative `SessionStore`;
7. signed `STATUS`, `EXTEND`, `END`, and `PREPARE` workflow tests;
8. automatic local transition to `EXPIRED_LOCKED` at zero;
9. reboot/offline/timer persistence testing where safe.

## 6. Explicitly Not Qualified

This milestone does NOT qualify:

- physical renter lockout;
- overlay enforcement;
- Device Owner provisioning;
- Lock Task / kiosk enforcement;
- arbitrary-foreground expiry takeover;
- persistent HOME control;
- factory-reset or Safe Boot restrictions;
- production owner recovery credentials;
- production renter-data turnover;
- production deployment on any model or OS.

At expiry the authoritative state must still become `EXPIRED_LOCKED`, but the build must not claim that the physical device is locked unless a separately qualified enforcement adapter verifies that fact.

## 7. Protocol Capability Disclosure

The Rental pairing acknowledgement must cryptographically bind a capability marker indicating this lane is `timer_qualification` and `enforcement_unqualified`.

Host must preserve/display that distinction and must not reinterpret the device as production-ready merely because START/STATUS commands succeed.

The qualification build should advertise only commands actually intended for this lane. Strong-management maintenance/relock capabilities must not be advertised as available when no qualified enforcement mechanism exists.

## 8. Host UI Boundary

The existing v0.5.2 Host presentation is preserved for this milestone.

Only minimal status/banners necessary to distinguish timer qualification from production enforcement may be added. No unrelated Host redesign is authorized by this addendum.

## 9. Rental UI Boundary

The existing v0.5.2 Rental visual language is preserved where practical.

The old overlay-permission first step is bypassed only in the v0.5.3 qualification lane. Rental must instead clearly identify the build as timer qualification and state that physical lock enforcement is not qualified.

Further Rental UI redesign is deferred until the user can physically test the new path.

## 10. Acceptance Gate

This milestone passes only if a physical test can demonstrate:

- Rental pairing QR appears without the denied overlay prerequisite;
- Host securely pairs with Rental;
- Host can select a duration and send signed START;
- Rental acknowledges START and becomes `ACTIVE`;
- Rental displays and owns the countdown;
- Host timer display remains an explicit estimate between signed status confirmations;
- expiry changes Rental authoritative state to `EXPIRED_LOCKED` without fabricating physical lock success.

Passing this milestone authorizes further locking/provisioning qualification work. It does not authorize production locking or hardening.

---

Created under the project-wide Controlled Evolution process after verified Android Go incompatibility with the Stage-A overlay adapter and explicit owner authorization to proceed with a bounded timer/pairing qualification build.
