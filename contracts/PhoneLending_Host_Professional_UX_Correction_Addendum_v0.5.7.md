# PhoneLending Host Professional UX Correction Addendum v0.5.7

Status: ACTIVE HOST-ONLY UX MILESTONE

## 1. Purpose
v0.5.7 corrects the Host UX gaps that remained in v0.5.6. The Rental v0.5.6 implementation is the preserved baseline and must remain byte-for-byte unchanged in this milestone.

This addendum changes presentation, navigation, Host-side pricing configuration, and refresh orchestration only. It does not change Rental timer authority, the six canonical states, pairing cryptography, protocol version, signing identity, or physical-enforcement qualification.

## 2. Product design principles
- Host is operator-facing fleet management software, not a developer control panel.
- Normal Host surfaces use plain language; technical diagnostics are secondary/detail information.
- Information hierarchy and interaction semantics take precedence over decorative density.
- Reusable typography/spacing/component tokens must replace ad-hoc sizing.
- Back returns to navigation origin, not merely to a hard-coded parent.
- Money displayed/entered by operators uses whole Philippine pesos only in v0.5.7.

## 3. App shell and system areas
- Respect Android status-bar, cutout, gesture, and navigation-bar insets.
- Use a visually bounded app shell rather than accidental full-screen/infinite content.
- Top-level content destinations remain Dashboard, Devices, Settings.
- Bottom navigation is compact: icon + small active page indicator only. Do not visibly repeat Dashboard/Devices/Settings labels under the icons.
- Accessibility content descriptions still expose destination names.
- Horizontal swipe between the three top-level destinations remains a convenience; explicit navigation remains available.

## 4. Typography and spacing system
Host must use named design tokens for page titles, section titles, body, labels, metadata, numerical display text, spacing, touch target, card radius, and page margins. Major screens must not choose arbitrary font sizes or spacings widget-by-widget.

Minimum practical touch target is 48dp for interactive controls unless the control is wrapped by a larger clickable container.

## 5. Dashboard
Dashboard answers: what is happening, what needs attention, and what operator action should happen next.

Required sections:
1. Fleet overview: Active, Ready, Expired, Attention counters.
2. Pair Rental Phone action.
3. Start a rental workflow.

Do not crowd Dashboard with a duplicate inventory list or recent-activity system in this milestone.

Fleet counters are clickable and open the shared Devices screen with the selected filter. Back must return to Dashboard when that is the navigation origin.

### 5.1 Start a rental workflow
START always requires an explicit target Rental device.
- If zero Ready devices: show a friendly no-ready-device state and a route to Devices.
- If exactly one Ready device: it may be selected automatically and clearly displayed.
- If multiple Ready devices: require the operator to choose a target before rental controls are shown.
- Once a target is chosen, show the five configured rental presets and Custom Rental controls on Dashboard.
- START continues to send only signed duration to Rental; Host-side price metadata never becomes Rental authority.

## 6. Devices
- Devices is inventory/management, not pairing.
- Search and status filters are first-class.
- Filters: All, Active, Ready, Expired, Attention.
- All view may group rows by status for scanning; filtered views show the relevant list directly.
- Device rows use friendly names first; technical IDs are secondary/support detail.
- Device detail is a subpage and uses the real navigation back stack.
- Attention remains orthogonal health metadata, not a seventh canonical state.

## 7. Settings information architecture
Settings root uses grouped navigation rows and subpages:
- Appearance
- Rental
  - Rental presets
  - Custom rental
- Device Management
- Support
  - Diagnostics & Support
- Advanced
- About

Normal Settings root must not dump detailed controls inline.

## 8. Appearance
Theme options:
- System default (default)
- Light
- Dark

## 9. Rental presets
Exactly five configurable preset slots remain in v0.5.7.
Each preset independently stores:
- duration in whole minutes
- price in whole pesos

Default presets:
- 1 hour / ₱5
- 2 hours / ₱10
- 4 hours / ₱20
- 10 hours / ₱50
- 20 hours / ₱100

Preset list rows must identify what is being edited before opening the editor. The editor must show:
- slot identity such as “Preset 3 of 5”
- duration
- whole-peso price
- live button preview
- Save

No decimal/cents keyboard or fractional-peso preset value is allowed. Restore Defaults remains available.

## 10. Custom Rental
Custom Rental has a segmented input mode:
- Time
- Amount

The two modes are views of the same intended rental rather than unrelated calculators.

### 10.1 Default custom rate
Default operator rate is:
**₱1 gives 12 minutes**

Thus:
- ₱1 = 12 min
- ₱5 = 60 min
- ₱10 = 120 min

The configurable rate is represented to operators as whole pesos and whole minutes, not centavos/billing jargon. For v0.5.7, the rate editor uses “₱1 gives X minutes”.

### 10.2 Time mode
- Underlying value remains integer minutes.
- Default first value is one custom-rate unit (12 minutes under defaults), not 60 minutes.
- Minus/plus change by exactly the configured minutes-per-peso unit.
- Crossing one hour changes only the displayed duration format; the button meaning does not silently change.
- Long press repeats and may accelerate through repeat frequency.
- Long press is not the only path; tapping and direct entry remain supported.
- Tapping the central duration opens direct Hours/Minutes entry.
- Entered duration must be an exact multiple of minutes-per-peso so the calculated price stays whole pesos; no hidden rounding.
- Display calculated whole-peso price before START.

### 10.3 Amount mode
- One whole-peso amount field only.
- Numeric keyboard must not allow decimal entry.
- Amount must be a positive whole peso amount.
- Conversion is exact: amount × configured minutes-per-peso.
- Display equivalent time before START.
- Reject values that exceed the session-duration limit; do not silently round.

## 11. Plain-language error model
Normal operator errors use friendly language and actionable recovery. Internal labels such as TLS_IDENTITY, SSLHandshakeException, NSD, ACK, canonical state, or protocol stages do not appear as the primary message.

Where useful, provide “Technical details” as a secondary action/surface containing exact engineering diagnostics.

## 12. Refresh model
Automatic status reconciliation is event-first where possible:
- app foreground
- page visibility / destination change
- pairing completion
- command completion
- network change
- device response

Fallback polling is adaptive by relevance/health and should not present stale data as current. A manual pull/refresh fallback may remain available.

v0.5.7 does not need a server push architecture or complex fleet backend.

## 13. Rental preservation gate
No file under `apps/consumer/` may change in this Host-only milestone. Rental v0.5.6 notification, state-driven UI, timer display, pairing behavior, and timer authority are preserved exactly.

## 14. Protected architecture
Still exactly six canonical states:
- UNPROVISIONED
- AVAILABLE_LOCKED
- ACTIVE
- EXPIRED_LOCKED
- ADMIN_MAINTENANCE
- RECOVERY_LOCKED

Rental remains authoritative for canonical state, remaining time, expiry, and signed acknowledgements. Host pricing is presentation/business metadata only.

Physical lock enforcement remains NOT QUALIFIED. Server-assisted Recovery remains contractually defined but NOT IMPLEMENTED.

## 15. Release acceptance
A green v0.5.7 release must validate both engineering boundaries and product-design requirements above. “Method exists” is not sufficient evidence of UX completion.
