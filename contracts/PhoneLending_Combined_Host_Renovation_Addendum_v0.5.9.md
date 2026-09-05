# PhoneLending Combined Host Renovation Addendum v0.5.9

Status: ACTIVE COMBINED UI MILESTONE

## 1. Purpose
v0.5.9 continues the Host professional renovation from v0.5.7 while preserving the Rental UX/expiry refinement introduced in v0.5.8.

This milestone addresses the owner's explicit instruction to implement the remaining Host UI renovation work **in the same project line** as the new Rental changes, instead of freezing the Host at v0.5.7.

## 2. Version scope
- Host: `0.5.9-dev`, versionCode 12.
- Rental: preserved `0.5.8-field`, versionCode 13.
- Rental source and behavior are not changed by the v0.5.9 Host delta.

Host and Rental retain their existing package identities and signing lines.

## 3. Host renovation requirements
The existing v0.5.7 professional shell, Dashboard/Devices/Settings information architecture, whole-peso pricing model, five presets, custom Time/Amount rental model, theme support, navigation stack, and event-first refresh remain required.

v0.5.9 adds the following complaint-driven corrections:

### 3.1 Dashboard rental targeting
- Dashboard remains limited to Fleet overview, Pair a device, and Start a rental.
- Pairing is presented as a deliberate operator action card rather than a bare developer-style control.
- When several Ready phones exist, the operator must choose the exact target before rental controls are shown.
- Once selected, the target is presented in a dedicated Rental phone card with friendly name first and technical device ID secondary.
- Quick presets and Custom rental appear only after target selection.

### 3.2 Devices is inventory/management, not a second START screen
- Search matches both friendly name and technical device ID.
- Device cards display friendly name first and device ID second when they differ.
- Device Detail no longer duplicates the full preset/custom START UI for a Ready phone.
- A Ready Device Detail instead routes to the Dashboard Start a rental workflow with that device preselected.
- Active/expired/recovery management actions remain available where appropriate.

### 3.3 Technical information is secondary
- Primary Device Detail uses friendly status/presentation.
- Exact device ID, canonical state, protocol, service, endpoint, and raw last result are available under a secondary `Technical details` action.
- Manual `Refresh status` remains a fallback while page-visible status reconciliation is also triggered automatically.

### 3.4 Preset editor clarity
- Preset editor continues to show `Preset X of 5`, Hours, Minutes, whole-peso price, and Save.
- The live preview is rendered in the same button-like form used by the operator instead of plain text only.

### 3.5 Custom rental clarity
- Rate editor visually states `₱1 gives X minutes` while editing.
- Time mode clearly indicates that the center duration can be tapped for Hours + Minutes direct entry and that step size is the configured minutes-per-peso unit.
- Calculated price is labeled before START.
- Amount mode labels the equivalent time before START.
- START confirmation explicitly lists Rental phone, duration, and Host-local price before the Rental phone is asked to confirm.

### 3.6 Visual rhythm
- Host design tokens are refined for a slightly stronger page/section/body hierarchy, 18dp page margin, and a slightly more compact 56dp bottom navigation bar.
- Icon-only bottom navigation and Android system-bar/cutout handling remain unchanged in principle.

## 4. Rental preservation
The v0.5.9 Host delta must not change files under `apps/consumer/`.

The preserved Rental v0.5.8 behavior remains:
- renter-state main UI center-balanced;
- ACTIVE timer notification ongoing and bounded self-restoring where Android permits;
- notification changes to Rental ended / 00:00:00 at expiry;
- authoritative expiry occurs before one best-effort foreground handoff;
- no repeated Activity relaunch loop is used as a lock;
- timer-qualification mode remains active and the historical overlay is not silently restored.

## 5. Protected architecture
- Exactly two apps remain: Host and Rental.
- Exactly six canonical states remain.
- Rental remains authoritative for canonical state, remaining time, expiry, and signed command acknowledgement.
- Host price remains local business/operator metadata. START transmits signed duration, not price authority.
- Pairing cryptography, replay protection, protocol trust, recovery architecture, package identities, and signing continuity are unchanged.
- Physical lock enforcement remains NOT QUALIFIED.
- Server-assisted Recovery remains NOT IMPLEMENTED.
- No UI relaunch or notification behavior may be represented as production enforcement.
- Recovery-first and anti-brick rules remain in force.
- No merge to `main` without explicit owner authorization.

## 6. Acceptance
CI must prove reconstruction, compilation, Host v0.5.9 version identity, preserved Rental v0.5.8 version identity, v0.5.9 Host renovation markers, exact six-state model, and absence of any v0.5.9 consumer-source delta.

Physical owner review remains authoritative for visual acceptance. A green CI run does not prove that the Host UI is visually accepted on the actual device.
