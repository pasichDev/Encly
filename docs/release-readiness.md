# Release readiness

This document records what has been verified for the current beta branch. It is not a claim that
the application is bug-free.

## Build

- [x] `lintDebug` — GitHub Actions #155 / run `35467200378`.
- [x] `:app:detekt` — GitHub Actions #155 / run `35467200378`.
- [x] `testDebugUnitTest` — 14 JVM tests passed in GitHub Actions #155.
- [x] `assembleRelease` with R8/ProGuard — GitHub Actions #155.
- [x] Release APK artifact `release-apk` was uploaded (SHA-256:
  `9d4c468ba9a9f682ebab824f6b6e63bef9712f18e344552bbfa9ac3bf1c6bc7b`).

## Tests

- [x] PIN-slot regression coverage: correct PIN, wrong PIN, tampered metadata, incomplete
  metadata and lockout.
- [x] Note-list selection race and Room update-result handling have JVM regression coverage.
- [ ] Task CRUD and tag write-failure regression coverage needs expansion.
- [ ] Android Keystore biometric behavior requires a physical-device run.

## Security and privacy

- [x] Mandatory screen protection and backup/device-transfer exclusion are present in source.
- [x] No app network permission, analytics or plaintext clipboard/share/export path is present in
  the current source audit.
- [x] Stale `VIBRATE` permission removed.
- [x] The placeholder privacy-policy URL no longer redirects users to feedback.
- [x] All Compose text fields request `IME_FLAG_NO_PERSONALIZED_LEARNING`; a
  compliant-keyboard device check remains required.
- [ ] **Public privacy policy URL is not published. This blocks any public beta release.**

## Data integrity and lifecycle

- [ ] Full device verification is required for background editor flush, process death, biometric
  enrollment invalidation, compliant-IME behavior and release-APK upgrade behavior.
- [ ] Run every scenario in [beta-test-checklist.md](beta-test-checklist.md) before distribution.

## Store readiness

- [ ] Public privacy policy URL.
- [ ] Signing/upload configuration.
- [ ] Store listing, content declarations and data-safety answers.
- [ ] Final clean-install and upgrade smoke test.

## Known limitations

- Recovery seed unlocks the local vault only; it is not a portable encrypted-backup format.
- Rooted or compromised Android systems are outside Encly's security boundary.
