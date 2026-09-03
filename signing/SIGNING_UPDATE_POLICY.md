# PhoneLending Development Signing and Update Continuity Policy

Status: implementation guardrail for the v0.4.x development line. This document does not amend the frozen Master Production Contract.

## Purpose
Android only accepts an in-place APK update when application identity and signing identity are compatible. PhoneLending therefore treats APK signing as part of update continuity and anti-brick safety, not as a packaging afterthought.

## Trust separation
- Host and Consumer use different APK signing identities.
- Development and future production signing identities are different.
- Consumer signing material is more sensitive because the Consumer may become Device Owner.
- Private signing keys must never be committed to this repository.

## v0.4.1 development baseline
Pinned public certificate fingerprints:
- Consumer DEV: `8b46873ee7d9fec4cecc2faa03694e8001e1ad42accf862257222f74174936c9`
- Host DEV: `e7da706d3a4692b6603b311d0856fe557815ee9db59d9399b04910875c67352f`

A distributable development APK must be signed by the corresponding pinned identity. CI compile-only builds may use an ephemeral runner debug key, but those outputs are NON-DISTRIBUTABLE and must not be presented as update-capable APKs.

## Consumer engineering boundary
The current Consumer development line remains `android:testOnly=true` because the Tier-0 development recovery contract requires the independent ADB/testOnly Device Owner removal path. This means the recovery-qualified engineering Consumer is installed/updated with ADB, not by ordinary tap-to-install.

Do not remove `testOnly` merely to improve installation convenience. A normal/tap-installable Consumer that is allowed to become Device Owner requires independent non-ADB owner recovery qualification first.

## Update invariants
Within one signed development line:
1. application ID stays stable;
2. versionCode increases monotonically;
3. signing identity remains the pinned identity;
4. package replacement must not reset authoritative Consumer session state, pairing trust, or Device Owner ownership;
5. `MY_PACKAGE_REPLACED` must reconcile Consumer state/policy;
6. a failed/unknown update must never grant renter access.

## Broken v0.3/v0.4 transition
The previously emitted v0.3 and v0.4 debug APKs were signed by different ephemeral CI debug certificates. They cannot form a legitimate Android in-place update chain. v0.4.1 is therefore the intended one-time stable development signing baseline. Devices from the older ephemeral-signing era require one controlled migration before future in-place updates can work reliably.

## Production direction
Production will use separate production signing identities, production provisioning, and production owner recovery. Engineering-to-production is an enrollment boundary, not an ordinary APK update.
