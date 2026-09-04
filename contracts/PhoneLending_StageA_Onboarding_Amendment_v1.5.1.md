# PhoneLending — Stage-A Guided Onboarding Amendment

Contract version: 1.5.1
Status: Explicitly authorized correction to the v0.5.x Stage-A development milestone

## Scope
This amendment corrects the failed v0.5.0 first-run flow without changing the six canonical states, Rental time authority, Host/Rental trust boundary, or the later production Device Owner/DPC/Lock Task direction.

## Frozen onboarding semantics
1. Stage-A first-run setup is exactly two verified steps: **Enable Rental Lock Screen** and then **Pair With Host**.
2. Android overlay permission and Host pairing are setup/readiness conditions, not new canonical states.
3. An unpaired Stage-A Rental remains `UNPROVISIONED`. Granting overlay permission alone must not change it to `AVAILABLE_LOCKED`.
4. `AVAILABLE_LOCKED` is entered only after successful Host pairing while required Stage-A lock access is available.
5. The soft-lock overlay must never cover first-run setup instructions or the pairing QR.
6. Pressing the Android-settings button is not success. Rental verifies whether PhoneLending Rental actually has Display over other apps permission when the operator returns.
7. If overlay permission is later revoked, canonical rental state is preserved. The condition is reported as a readiness/health problem, Host surfaces **NEEDS ATTENTION** after signed status reconciliation, and a new `START` is rejected until access is restored.
8. Permission loss must never manufacture `UNPROVISIONED`, `ACTIVE`, or extra renter time.
9. Seven-tap release and the five-minute automatic release remain presentation-only Stage-A recovery mechanisms. They never change canonical rental state or grant time.
10. Strong Device Owner/DPC/Lock Task enforcement remains a later Stage-B/production qualification milestone.

## Required regression checks
- first launch clearly identifies PhoneLending Rental on the Android overlay-permission step;
- returning without granting the permission remains on Step 1;
- granting the permission advances to Step 2 automatically;
- Step 2 exposes the Host pairing QR and no soft lock covers it;
- successful pairing transitions Rental to `AVAILABLE_LOCKED` and permits the READY soft lock;
- revoking overlay permission after pairing preserves canonical state and blocks `START` with `lock_access_required`;
- restoring permission and refreshing Host status clears the NEEDS ATTENTION presentation without inventing a new canonical state.
