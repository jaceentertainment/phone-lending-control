# PhoneLending Rental UX + Expiry Refinement Addendum v0.5.8

Status: ACTIVE CURRENT MILESTONE
Project checkpoint target: v0.5.8-rental-ux-expiry-r1

## 1. Purpose
This addendum authorizes a focused Rental-side refinement on top of the accepted v0.5.7 Host professional-UX source baseline. It does not authorize stronger physical enforcement and does not replace any Tier-0, Recovery-First, protocol, signing, or six-state rule.

## 2. Build boundary
- Host remains the v0.5.7 professional-UX baseline. No Host behavior is changed by this milestone.
- Rental advances from `0.5.6-field` / versionCode 12 to `0.5.8-field` / versionCode 13.
- `0.5.8-field` remains in the explicitly governed timer/pairing qualification lane. The historical Stage-A overlay must not silently reactivate merely because the Rental version changes.
- Physical enforcement remains NOT QUALIFIED. Device Owner / Lock Task production qualification remains a later Recovery-First gate.

## 3. Centered renter-state presentation
The normal renter-facing state screens should place their primary state/timer content near the visual center of the usable display while preserving a conventional header and footer and remaining scroll-safe on small displays.

Centered state screens:
- Ready for rental (`AVAILABLE_LOCKED` presentation in this qualification lane);
- Rental active (`ACTIVE`);
- Rental ended (`EXPIRED_LOCKED`);
- Owner attention (`RECOVERY_LOCKED`).

Setup, pairing, permission, diagnostics, and owner/technician flows may remain top-aligned when that improves instruction readability.

This is presentation only. UI position never changes canonical state or timer authority.

## 4. ACTIVE timer notification persistence
While `ACTIVE`:
- the remaining-time notification is marked ongoing using supported Android notification behavior;
- Rental periodically reconciles whether its active status notification is still present and may re-post it if missing;
- the notification remains a presenter/observer of Rental Time Authority only;
- notification removal, suppression, permission denial, OEM behavior, process behavior, or re-post failure never pauses, extends, resets, or delays the rental deadline;
- Android-level absolute non-dismissability is not claimed for this field build.

When the rental is no longer `ACTIVE`, the countdown notification must not continue pretending that paid time remains. `EXPIRED_LOCKED` presents a static `Rental ended` / `00:00:00` status.

Production managed-device notification capability remains a provisioning/readiness concern under the Master Contract.

## 5. Expiry foreground handoff in the field qualification lane
Natural expiry follows this order:

`ACTIVE -> authoritative remaining <= 0 -> commit EXPIRED_LOCKED -> update expiry presentation -> one best-effort request to foreground the renter-safe Rental status UI`

Rules:
- the state transition is authoritative before the Activity handoff is attempted;
- the handoff is presentation only and must not mutate time/state;
- it is one-shot for the natural expiry transition, not a repeating Activity-relaunch loop;
- Android may reject a background Activity launch; such rejection is tolerated and must not change authoritative expiry;
- no full-screen takeover is allowed before zero;
- this best-effort handoff is not called a lock and is not evidence of physical enforcement qualification;
- later qualified Device Owner / Lock Task restricted-home behavior replaces the need to rely on this presentation handoff as enforcement.

## 6. Protected boundaries
This milestone must not change:
- exactly two app roles;
- exactly six canonical Rental states;
- Rental authority for remaining time and expiry;
- Host request/ACK semantics;
- replay/authentication/protocol trust boundaries;
- Host-local price metadata rule;
- recovery architecture;
- package/signing continuity;
- renter privacy/safety prohibitions.

No repeated foreground hijack, Accessibility lock substitute, new overlay dependency, or new canonical state is authorized.

## 7. Acceptance checks
CI/source checks must prove at least:
1. Host source is unchanged by the v0.5.8 delta after v0.5.7 reconstruction.
2. Rental package remains `com.jace.phonelending.consumer.field`.
3. Rental version is `0.5.8-field`, versionCode 13.
4. `0.5.8-field` remains a timer-qualification build and therefore does not re-enable the legacy overlay path.
5. The four renter-state screens use center-balancing layout behavior without changing setup/pairing screens.
6. ACTIVE notification is ongoing and has bounded self-reconciliation.
7. Expiry notification becomes static at zero.
8. Natural expiry requests the renter-safe foreground UI once after authoritative expiry.
9. The six canonical states remain exact.
10. Host and Rental compile successfully.

## 8. Physical validation still required
CI cannot prove OEM notification swipe behavior, background-Activity-launch acceptance, visual vertical centering on every screen size, or future physical lock enforcement. Physical testing on the target Rental phone remains required before those behaviors are accepted.
