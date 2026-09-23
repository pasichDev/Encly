## What and why

<!-- What does this change and why is it needed? Link the issue if there is one. -->

## How it was tested

<!-- Commands you ran and, for UI changes, the device / emulator you checked it on. -->

- [ ] `./gradlew :app:testFdroidDebugUnitTest :app:koverVerifyFdroidDebug`
- [ ] `./gradlew spotlessCheck :app:detekt :app:lintFdroidRelease`
- [ ] `./gradlew assembleRelease`
- [ ] Checked on a device / emulator (say which)

## Security and data checklist

- [ ] No new permission, and in particular no `INTERNET` permission.
- [ ] No network, analytics, telemetry or proprietary (Google Play Services) dependency.
- [ ] No new path that puts plaintext outside the vault (clipboard, share, notifications, files, logs).
- [ ] If key derivation, the SQLCipher passphrase, the vault slots or any on-disk format changed:
      existing installs still open (migration added and tested), and SECURITY.md is updated.
- [ ] If the Room schema changed: `DB_VERSION` bumped, a migration added, the new `app/schemas`
      export committed, and `AppDatabaseSchemaTest` extended.
- [ ] User-visible changes are listed under `[Unreleased]` in CHANGELOG.md.
