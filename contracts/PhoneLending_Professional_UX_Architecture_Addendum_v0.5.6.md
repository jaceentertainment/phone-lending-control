# PhoneLending Professional UX Architecture Addendum v0.5.6

Status: ACTIVE MILESTONE ADDENDUM
Applies to: v0.5.6 field-development lane
Scope: Host UX architecture, Rental presentation, notification presentation, Host-only pricing configuration, and adaptive status refresh.

## 1. Governing boundaries

This addendum supplements the active PhoneLending governance, master, recovery-first, timer-qualification, control-service, connectivity, and v0.5.5 UX contracts. It does not change the protected core.

The following remain unchanged:
- exactly two apps: Host and Rental;
- exactly six canonical states: UNPROVISIONED, AVAILABLE_LOCKED, ACTIVE, EXPIRED_LOCKED, ADMIN_MAINTENANCE, RECOVERY_LOCKED;
- Rental remains authoritative for canonical state, local remaining time, expiry, and signed ACK;
- Host remains a management UI and may locally estimate a presentation countdown only between Rental confirmations;
- package IDs, protocol version, pairing trust, replay protection, and signing identity continuity remain unchanged;
- physical lock enforcement remains NOT QUALIFIED in this field lane;
- server-assisted Recovery remains contractually defined but not implemented by this milestone.

## 2. Design-system rule

Host and Rental shall look like one product family while serving different users.

The implementation shall use a small reusable visual vocabulary rather than ad-hoc per-widget sizing:
- bounded system-bar-aware app shell;
- consistent page margins and spacing rhythm;
- consistent typography roles for page title, section title, body, label, and metadata;
- consistent card, row, button, status, and input treatments;
- minimum practical touch targets;
- state must never be communicated by color alone.

Technical implementation vocabulary such as TLS, NSD, canonical state, ACK, protocol, or enforcement-unqualified shall not be primary everyday UI labels. Technical detail belongs under diagnostics/support views.

## 3. Host top-level information architecture

The three top-level destinations are:
1. Dashboard
2. Devices
3. Settings

They live inside one stable app shell. The top-level navigation is fixed outside scrolling content. Horizontal swipe is an optional convenience, not the only discoverability mechanism.

System Back follows actual navigation history. It does not blindly exit or blindly return to a fixed page.

Examples:
- Dashboard -> Active counter -> filtered Devices -> Device Detail -> Back returns filtered Devices -> Back returns Dashboard.
- Devices -> Device Detail -> Back returns Devices.
- Dashboard -> Settings -> Rental Presets -> Preset Editor -> Back returns Rental Presets -> Back returns Settings -> Back returns the prior origin.

Top-level or subpage navigation must not create new canonical Rental states.

## 4. Dashboard

Dashboard answers only:
- what is happening;
- what needs attention;
- what the operator can do next.

Required primary content:
- clickable Active, Ready, Expired, and Attention counts;
- Pair Rental Phone action;
- a compact active-rental preview.

Pairing belongs on Dashboard rather than the Devices inventory list.

Dashboard counters open the same Devices list component with a filter and preserve the Dashboard as navigation origin. Attention is orthogonal health/readiness metadata, never a seventh canonical state.

## 5. Devices

Devices is the paired-device inventory and management destination.

Required behavior:
- searchable device list;
- filters for All, Active, Ready, Expired, and Attention;
- when All is selected, devices may be grouped by those user-facing categories;
- friendly device names are primary; technical device IDs are not primary list labels;
- Device Detail is a subpage, not an overloaded inline card.

Attention may include stale/offline connectivity, re-pair requirement, lock-readiness problems, RECOVERY_LOCKED, maintenance/setup anomalies, or other operator-actionable health. It does not rewrite Rental canonical state.

## 6. Adaptive refresh

The normal UI shall not depend on a visible manual Refresh button or a single rigid polling interval.

Refresh is event-first:
- app foreground/resume;
- destination visibility;
- pairing completion;
- START/EXTEND/END/PREPARE outcomes;
- network/device responses.

A bounded adaptive reconciliation fallback is allowed. Active/attention devices may reconcile more frequently than ready/idle devices. Offline or low-value polling must back off. Host may animate a local presentation countdown between confirmations, but Rental remains authoritative.

## 7. Settings architecture

Settings is grouped into subpages instead of a flat control dump.

Root categories include:
- Appearance;
- Rental Presets;
- Custom Rental;
- Diagnostics & Support;
- About;
- operator lock action.

System default is the default theme for new installations. Light and Dark remain explicit choices.

## 8. Rental presets

Exactly five configurable preset slots remain in v0.5.6. Unlimited add/remove preset architecture is deferred.

Each preset contains:
- durationMinutes;
- Host-only priceCentavos.

Price is represented as integer centavos, never floating-point money. The v0.5.5 whole-peso persisted format must migrate safely.

Preset editing is a dedicated subpage that identifies the slot (for example, "Preset 3 of 5"), exposes Hours, Minutes, and Price, and provides a readable preview. The normal rental button shows the configured duration and price, not implementation jargon.

Only duration is transmitted in the signed START command. Price never becomes Rental authority.

## 9. Custom Rental

Custom Rental has two equivalent operator entry modes:
- Time;
- Amount.

A segmented selector switches the chosen input variable without changing the underlying business rule.

Configuration consists of:
- billingUnitMinutes: one of 5, 10, 15, 30, or 60;
- pricePerBillingUnitCentavos;
- default entry mode.

Default configuration is 15 minutes at 125 centavos (equivalent to PHP 5/hour).

### Time mode
- The underlying value is always integer minutes.
- +/- uses the configured billing unit.
- Press-and-hold repeat is an accelerator, never the only input method.
- The displayed unit becomes human-readable (for example 45 min, 1 hour, 1 hr 15 min) without silently changing the semantic step size at one hour.
- Tapping the value opens direct Hours/Minutes entry.
- Direct entry must match the configured billing unit and remain within the field limit of 24 hours.
- The corresponding Host-side amount is shown before START.

### Amount mode
- One peso amount input is primary.
- The equivalent duration is displayed before START.
- Amount must map exactly to whole billing units; hidden rounding is prohibited.
- Invalid/non-exact amounts are rejected rather than silently rounded.

## 10. Rental app UX

Rental is state-driven status software, not a smaller Host app. It does not get Dashboard/Devices/Settings navigation.

User-facing presentation maps the existing canonical states into simple screens:
- setup/pairing;
- Ready for rental;
- Rental active;
- Rental ended;
- Needs attention / controlled service state.

ACTIVE prioritizes one large in-app countdown. EXPIRED displays a static 00:00:00 without delaying canonical expiry. Renter-facing language remains simple. Qualification caveats may be shown as "Test build" / "Physical locking is not enabled yet" rather than internal enforcement vocabulary.

Notification permission is contextual and optional. Denial must never affect authoritative timer, pairing, START, or expiry.

## 11. Rental notification

Observed v0.5.5 defect: the notification visually presented duplicate remaining-time representations and the time was too small for quick renter checking.

v0.5.6 field design:
- exactly one visible countdown in the ACTIVE notification content;
- a dedicated larger compact countdown and larger expanded countdown may be used with Android DecoratedCustomViewStyle;
- countdown derives from the same Rental session deadline and is presentation only;
- the app must not rebuild the notification every second merely to decrement text;
- extension/state changes update the notification deadline/state;
- at expiry the running chronometer is replaced with a static 00:00:00 / Rental ended presentation;
- standard/OEM behavior must be physically tested on target devices before notification presentation is called qualified.

Warning notifications are intentionally restrained in this milestone to 5-minute and 1-minute thresholds. They are not timer authority.

## 12. System UI and accessibility

Both apps shall correctly account for Android status-bar and system-navigation insets. Important content must not sit underneath system bars. Modern edge-to-edge behavior may exist at the window level, but the visual content surface must feel bounded and deliberate.

Accessibility requirements include:
- scalable sp typography;
- logical reading order;
- useful content descriptions;
- central Rental countdown exposes a spoken phrase such as "1 hour 42 minutes remaining" rather than only colon-separated digits;
- long press is supplemental;
- direct typing/tapping alternatives remain available.

## 13. Explicit non-goals

v0.5.6 does NOT:
- qualify or implement production physical locking;
- implement server-assisted Recovery;
- change pairing protocol or canonical states;
- make the server timer-authoritative;
- make price part of Rental state or signed Rental authority;
- add unlimited presets;
- add fleet analytics/history dashboards;
- migrate the app wholesale to Jetpack Compose.

## 14. Qualification gate

This milestone is not accepted merely because it compiles. Field validation must confirm at minimum:
- v0.5.5 -> v0.5.6 in-place update/signing continuity;
- pairing remains successful;
- START/timer/expiry remain successful;
- Host system bars and bottom navigation render correctly on the physical Host phone;
- back-stack origin behavior works for Dashboard counters, Devices, Device Detail, Settings, and preset subpages;
- preset migration/configuration persists;
- Time and Amount custom rental modes produce the intended duration;
- long-press and direct-entry controls work;
- Rental in-app timer remains visible and authoritative;
- ACTIVE notification shows one readable countdown, not duplicate timers;
- expiry switches notification to static 00:00:00;
- notification denial does not affect session behavior.
