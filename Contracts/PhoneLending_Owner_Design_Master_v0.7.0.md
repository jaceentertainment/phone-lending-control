# PhoneLending Owner Design Master — v0.7.0

**Status:** OWNER-DIRECTED IMPLEMENTATION CANDIDATE — physical acceptance pending

This document records the owner's current visual/interaction design. It is not a visual re-audit. Implementation and code audits may test correctness, security, navigation history, state authority, calculations, and protocol boundaries, but must not silently redesign the approved presentation.

## 1. Main pages

There are exactly two top-level Host pages:

- Settings — left page — indicator `● ○`
- Dashboard — right page/root — indicator `○ ●`

There is no top-level Devices page.

Dashboard and Settings use a horizontal phone-launcher-style pager. The page follows the user's finger. Release settles to the destination without fade, zoom, 3D, bounce, or wraparound. Submenus do not participate in this pager.

Back history is contextual. Settings Back returns to Dashboard. Dashboard Back leaves/minimizes normally. Submenus retrace the exact path that opened them. Top-level swipes do not build a back-stack chain.

## 2. Dashboard

Dashboard's main content is centered as one coherent block when space is available:

- Fleet
  - Active
  - Ready
  - Expired
  - Attention
- `+ Pair Rental Phone` — BLUE — above
- `+ Add Time using QR` — GREEN — below

The lower Add Time action is intentionally easier to reach with the human thumb because it is expected to be used more frequently.

The four Fleet status controls are buttons. They open their status-specific device list directly. There is no intermediate Devices landing page.

Dashboard must not contain rental preset controls, custom rental controls, device lists, or Settings content.

## 3. Device status submenus

Dashboard status buttons open focused lists:

- Active -> Active Devices
- Ready -> Ready Devices
- Expired -> Expired Devices
- Attention -> Attention Devices

Lists use friendly Rental phone names as the primary identity. Technical IDs remain under technical details/diagnostics.

Selecting a device opens Device Detail. Sparse Device Detail content is centered when space is available.

Active Device Detail retains manual `Add Time` and `End Rental` actions. Ready Device Detail can enter the Start Rental workflow. Manual Add Time and QR Add Time converge on the same Add Time time/price workflow.

## 4. Pair and Add Time QR

Pair Rental Phone uses the existing secure one-time pairing QR workflow.

Add Time using QR is a separate fast path for an already-paired active Rental phone. The Rental app shows `Show QR to Add Time` on its ACTIVE screen. That opens a centered QR submenu.

The Add Time QR is identification-only. It must not grant command authority or expose pairing secrets/key material. Host must reconcile authoritative Rental status/session and then use the normal signed/replay-protected/ACKed EXTEND command.

## 5. Rental presets and custom rental

Default presets:

1. 1 hour / ₱5
2. 2 hours / ₱10
3. 4 hours / ₱20
4. 10 hours / ₱50
5. 20 hours / ₱100

Default custom rate: `₱1 = 12 minutes`.

Preset packages are independent from the custom rate. A preset stores duration and price. Editing one does not rewrite the custom rate, and changing the custom rate does not rewrite the five presets.

Operational Start Rental / Add Time screens use the same preset button visual language that Settings uses for editing those buttons.

Custom Rental has TIME and AMOUNT modes representing the same intended duration:

- TIME: `+` and `-` change exactly one configured rate unit per step; hold repeats the same unit with no hidden step-size change. The displayed duration is tappable for direct Hours + Minutes entry.
- AMOUNT: operator enters a whole positive peso amount and sees the equivalent time.
- Switching TIME/AMOUNT does not reset the intended rental.

Host owns pricing metadata. Rental START/EXTEND receives duration only.

## 6. Settings

Settings main page is only a simple category router:

- Appearance
- Rental
- Device Management
- Support
- Advanced
- About

Actual controls belong in submenus.

Settings -> Rental contains:

- Rental Presets
- Custom Rental

Rental Presets shows the same five time/price buttons as the operational rental workflow. Tapping a button opens Edit Rental Button with Hours, Minutes, Price, live button preview, and Save.

Custom Rental configures Minutes for ₱1, default TIME/AMOUNT input mode, Save, and Restore defaults.

## 7. Sparse screen placement

If a screen has substantial unused space, its main content is centered as a coherent block. Dense/list screens use normal top-down flow. Intentional whitespace is not an invitation to add more controls.

## 8. Visual authority rule

Do not visually re-audit, modernize, regroup, add extra cards, add top-level destinations, or move submenu content into parent pages unless the owner explicitly requests a design change. Code audits may question implementation decisions and must report disagreements immediately.
