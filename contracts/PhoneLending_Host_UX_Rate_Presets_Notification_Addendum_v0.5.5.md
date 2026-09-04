# PhoneLending Host UX / Rate Presets / Renter Notification Addendum v0.5.5

Status: ACTIVE MILESTONE CONTRACT
Project: PhoneLending Control
Applies to: v0.5.5 Host/Rental qualification build

## 1. Purpose

This milestone follows successful physical qualification of v0.5.4 pairing and Rental-authoritative countdown on the Redmi Android Go test phone. It adds operator usability and renter time visibility without changing production enforcement status.

Authorized scope:
1. Host main navigation split into Dashboard, Devices, and Settings;
2. five Host-local editable rental rate presets;
3. a Host custom-duration stepper with direct numeric entry;
4. Host appearance choices: Light Mode (default), Dark Mode, and System default;
5. Rental persistent remaining-time notification and contextual notification-permission request;
6. explicit visible `00:00:00` at expiry; and
7. bounded success/failure diagnostic lifecycle events and diagnostic-retention rules.

Physical Android lock enforcement remains NOT QUALIFIED. Server-assisted Recovery remains deferred under the Control Service contract.

## 2. Protected Architecture

This milestone MUST NOT change:
- the exactly six canonical states;
- Rental authority over canonical state, remaining time, and expiry;
- signed Host↔Rental protocol authority and replay protection;
- package/signing identity continuity;
- offline expiry;
- Recovery-First requirements; or
- the rule that service availability is non-authoritative.

Pricing shown by Host is operator metadata only. Rental receives authoritative duration seconds through the existing signed START command; price metadata does not become Rental state or cloud authority.

## 3. Host Main Navigation

After Host unlock, the three primary operator pages are:
- Dashboard — fleet summary and active-rental monitoring;
- Devices — pairing and per-device management;
- Settings — rate presets, appearance, and operator preferences.

The pages may be changed by horizontal swipe or by a visible three-position page indicator/navigation control. Device detail remains a drill-in screen from Devices and is not a seventh canonical rental state or a fourth top-level page.

## 4. Rental Rate Presets

Host provides exactly five preset slots in this milestone. Defaults are:
- 1 hour — ₱5
- 2 hours — ₱10
- 4 hours — ₱20
- 10 hours — ₱50
- 20 hours — ₱100

Each preset slot is independently editable in Settings for both duration and displayed price. A preset duration must remain within the signed START duration bounds supported by Rental. Price may be zero or positive and is Host-local operator metadata only.

The number of slots remains fixed at five for v0.5.5. Unlimited add/remove preset management is intentionally deferred to avoid unnecessary UI/persistence complexity.

## 5. Custom Duration

Host device detail provides a custom duration control supporting:
- decrement control;
- increment control;
- direct keyboard numeric entry; and
- the existing signed START confirmation.

The v0.5.5 default step is 30 minutes. Direct entry may choose any valid duration from 1 through 1440 minutes. The last valid custom duration may be remembered locally on Host.

## 6. Appearance

Host Settings exposes:
- Light Mode — default;
- Dark Mode; and
- System default (follow Android system light/dark setting).

Theme selection is local presentation state only and cannot alter device/rental state, protocol behavior, pricing semantics, or enforcement.

## 7. Rental Remaining-Time Notification

Rental retains the existing in-app timer display. The notification is an additional accessibility/convenience surface and MUST NOT replace the in-app display.

During ACTIVE, Rental should expose a persistent public notification showing remaining rental time and a countdown/end time. On Android versions requiring notification runtime permission, PhoneLending should request that permission in context after pairing rather than blocking first-run QR setup.

Denial/revocation of notification permission MUST NOT block pairing, START, countdown, expiry, or future recovery. Rental may show a non-blocking hint that the notification is unavailable.

At authoritative expiry, canonical state transitions on time. Both the expired Rental UI and the persistent notification should visibly show `00:00:00`; PhoneLending MUST NOT delay canonical expiry merely to display zero for an extra second.

## 8. Diagnostic Lifecycle Events

Successful qualification transitions may generate bounded sanitized reports such as:
- PAIRING_SUCCESS;
- SESSION_STARTED;
- SESSION_EXTENDED;
- SESSION_ENDED; and
- SESSION_EXPIRED.

These events remain non-authoritative and must not be emitted every timer second.

## 9. Diagnostic Retention / Cleanup

The development control service does not intentionally create or retain per-device `.log` files. The current endpoint emits sanitized structured `PL_DIAG` runtime events to the hosting provider's runtime logging system.

Rules:
1. no unbounded client or server log-file accumulation;
2. no permanent diagnostic archive by default;
3. platform-managed transient runtime-log retention is acceptable for development;
4. if PhoneLending later adds its own persistent diagnostic store, ordinary reports MUST have an automatic TTL/purge policy, initially targeted at no more than 7 days unless a separately documented incident/compliance requirement justifies longer retention;
5. cleanup must be automatic rather than requiring operators to delete old reports manually;
6. a selected diagnostic may be explicitly promoted to an incident record, but that must be a deliberate separate action rather than default retention; and
7. diagnostic cleanup/failure must never alter canonical rental state.

## 10. Acceptance Gate

v0.5.5 passes this milestone only if physical testing confirms:
- Dashboard / Devices / Settings navigation works by tap and horizontal swipe without interfering with normal vertical scrolling;
- all five presets persist across Host restart and changed duration/price values appear on device rental controls;
- custom duration accepts decrement, increment, and keyboard entry and sends the chosen duration through signed START;
- Light, Dark, and System default persist and render usable contrast;
- Rental in-app countdown remains available;
- Rental notification shows remaining time outside the app when permission is granted;
- notification denial does not affect the rental timer;
- expiry visibly shows `00:00:00` while state becomes EXPIRED_LOCKED on time;
- diagnostic session transition reports remain bounded/sanitized; and
- no production physical-lock claim is introduced.
