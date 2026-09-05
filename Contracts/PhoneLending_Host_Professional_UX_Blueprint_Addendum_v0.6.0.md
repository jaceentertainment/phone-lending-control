# PhoneLending Host Professional UX Blueprint Addendum v0.6.0

**Status:** ACTIVE CANDIDATE MILESTONE — physical owner acceptance pending

## Purpose
v0.6.0 is the corrective Host UX architecture milestone. The Professional Host UX Blueprint supplied by the owner is the primary design authority for Host presentation, information architecture, navigation/back-stack semantics, reusable UI structure, device-list presentation, Settings organization, accessibility, and screen-state organization.

The active PhoneLending core contracts continue to win on security, authority, state-machine, protocol, privacy, recovery, package identity, and signing identity.

## Historical correction
The v0.5.9 and v0.5.10 Host visual attempts are retained as historical development checkpoints but are not owner-accepted implementations of the Professional Host UX Blueprint. CI success for those checkpoints is not evidence of physical UX acceptance.

## Host v0.6.0 requirements
- Respect Android status-bar and system-navigation insets; no aggressive fullscreen/infinite-layout treatment.
- Use a stable app shell with compact app bar, content region, compact semantic top-level navigation, and system-navigation inset.
- Dashboard / Devices / Settings remain the three top-level destinations; swipe may remain a convenience.
- Back behavior follows actual navigation history and preserves filtered-list origin.
- Dashboard is deliberately limited to Fleet overview, Pair Rental Phone, Start Rental presets, and Custom Rental.
- Dashboard counters enter the same filtered Devices screen implementation.
- Pairing is a dedicated Dashboard-launched flow.
- Devices uses search + filters + one scalable row list; ordinary list rows prioritize friendly device names and hide technical IDs.
- Attention and Offline are orthogonal presentation/health metadata and never canonical Rental states.
- Settings uses categories and dedicated subpages.
- Five configurable presets remain exactly five.
- Friendly device naming/renaming is first-class.
- Design tokens govern typography, spacing, touch targets, radii, and app-shell dimensions.
- Screen UI state is modeled coherently rather than assembled from unrelated widget-local fields.
- No full Compose migration is part of this milestone.

## Later business-rule reconciliation
The older blueprint's exploratory centavo/billing-unit pricing model is superseded by the later approved Host business rules:
- default rate: **₱1 gives 12 minutes**;
- custom Time default: **12 minutes**;
- Time increments/decrements use the exact configured rate unit;
- direct Hours + Minutes entry remains available;
- Amount mode accepts whole positive pesos only and shows equivalent duration immediately;
- five default presets: 60m/₱5, 120m/₱10, 240m/₱20, 600m/₱50, 1200m/₱100;
- Host owns price metadata; Rental receives signed duration only.

## Frozen boundaries preserved
- Exactly two Android apps.
- Exactly six canonical states: UNPROVISIONED, AVAILABLE_LOCKED, ACTIVE, EXPIRED_LOCKED, ADMIN_MAINTENANCE, RECOVERY_LOCKED.
- Rental remains authoritative for state/time/expiry.
- No price authority moves to Rental.
- No signing/package identity rotation.
- No weaker recovery or privacy posture.

## Acceptance
A green compile/static-validation result proves only source/build conformance. The Host UX milestone is not owner-accepted until the signed APK is physically inspected and the owner confirms the redesigned screens and navigation behave as intended.
