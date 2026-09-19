# Release readiness

This document records what has been verified for the current beta branch. It is not a claim that
the application is bug-free.

## Build

- [ ] `lintDebug` — pending the current CI run.
- [ ] `:app:detekt` — pending the current CI run.
- [ ] `testDebugUnitTest` — pending the current CI run.
- [ ] `assembleRelease` with R8/ProGuard — pending the current CI run.
- [ ] Release APK artifact reviewed after a green CI run.

## Tests

- [x] PIN-slot regression coverage: correct PIN, wrong PIN, tampered metadata, incomplete
  metadata and lockout.
- [ ] Note CRUD, task CRUD and tag write-failure regression coverage needs expansion.
- [ ] Android Keystore biometric behavior requires a physical-device run.

## Security and privacy

- [x] Mandatory screen protection and backup/device-transfer exclusion are present in source.
- [x] No app network permission, analytics or plaintext clipboard/share/export path is present in
  the current source audit.
- [x] Stale `VIBRATE` permission removed.
- [x] The placeholder privacy-policy URL no longer redirects users to feedback.
- [ ] **Public privacy policy URL is not published. This blocks any public beta release.**

## Data integrity and lifecycle

- [ ] Full device verification is required for background editor flush, process death, biometric
  enrollment invalidation and release-APK upgrade behavior.
- [ ] Run every scenario in [beta-test-checklist.md](beta-test-checklist.md) before distribution.

## Store readiness

- [ ] Public privacy policy URL.
- [ ] Signing/upload configuration.
- [ ] Store listing, content declarations and data-safety answers.
- [ ] Final clean-install and upgrade smoke test.

## Known limitations

- Recovery seed unlocks the local vault only; it is not a portable encrypted-backup format.
- Rooted or compromised Android systems are outside Encly's security boundary.
