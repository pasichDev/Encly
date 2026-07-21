# Encly

**Offline, zero-knowledge encrypted notes for Android.**

Encly keeps your notes and tasks encrypted on-device. Nothing leaves your phone —
there is no account, no sync, no network. Access is protected by a mandatory PIN
(and biometrics where available), and the encryption is anchored to a BIP39 seed
phrase that only you hold.

> ⚠️ **Zero-knowledge by design:** if you lose your seed phrase, your data is
> unrecoverable. There is no backdoor and no server that can restore it.

## Features

- 🔒 **Encrypted at rest** — notes stored in a SQLCipher (AES-256) database.
- 🧩 **Block editor** — text, headings, quotes, checklists / numbered lists, links, separators.
- 🏷️ **Tags & tasks** — organize notes, and tasks with local reminders.
- 🗑️ **Trash** — soft-delete with restore.
- 🔑 **BIP39 seed phrase** — the recovery/identity anchor for your encryption.
- 📴 **Fully offline** — no INTERNET permission, no telemetry, no cloud.
- 🔐 **Mandatory lock** — PIN (PBKDF2 with lockout) plus optional biometric unlock.

## Security model

- A 12-word BIP39 seed phrase is generated (or entered) at onboarding. Its SHA-256
  hash is sealed with an AndroidKeyStore AES-GCM key and stored locally.
- Per-purpose data keys are derived from that hash via **HKDF-SHA256** with a
  per-install random salt, and used as the SQLCipher passphrase.
- The app PIN is stored as a salted **PBKDF2-HMAC-SHA256 (600k)** hash and gates
  database unlock; repeated failures trigger a progressive lockout.
- `allowBackup` is disabled and the encrypted DB / security prefs are excluded from
  cloud backup and device transfer.

See [SECURITY.md](SECURITY.md) for the threat model and reporting.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · SQLCipher · Coroutines ·
kotlinx.serialization · BIP39 (kotlin-bip39)

Clean architecture: `presentation` → `domain` (use-cases) → `data` (repositories) →
Room/SQLCipher, with a `core/security` layer for crypto and auth.

## Build

Requirements: **JDK 21**, Android SDK (compileSdk 36), Android Studio.

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # release build (R8); needs signing config below
```

### Release signing (optional)

Release builds are unsigned unless you provide a `keystore.properties` at the repo
root (git-ignored):

```
storeFile=/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Contributing

Issues and PRs are welcome. Please keep code comments in English and run
`./gradlew assembleDebug` before opening a PR. Security-sensitive changes should
reference the model in [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE).
