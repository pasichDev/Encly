# Encly

**Offline encrypted notes and tasks for Android.**

Encly keeps notes and tasks encrypted on-device. There is no account, sync, telemetry,
notification pipeline, plaintext sharing flow, or network access. Access is protected by
a mandatory app PIN and, optionally, strong biometrics.

## Features

- 🔒 **Encrypted at rest** — Room on SQLCipher with a random 256-bit database key.
- 🧩 **Block editor** — text, headings, quotes, checklists / numbered lists, links, separators.
- 🏷️ **Tags & tasks** — local organization without reminder notifications.
- 🗑️ **Trash** — soft-delete with restore.
- 🔑 **Optional BIP39 recovery seed** — a separate recovery slot for the database key.
- 📴 **Fully offline** — no `INTERNET` permission, cloud sync, or analytics.
- 🔐 **Mandatory lock** — 6-digit PIN plus optional Class 3 biometric unlock.
- 🛡️ **Protected UI** — `FLAG_SECURE` is enforced from the first Activity frame.

## Security model

Encly v2 uses envelope encryption rather than deriving the SQLCipher key directly from
a seed or PIN:

1. Onboarding creates a random 256-bit **DEK** (data-encryption key).
2. SQLCipher is opened with that DEK.
3. The mandatory PIN is processed with **PBKDF2-HMAC-SHA256 (600k)** and a random salt.
   The resulting KEK wraps the DEK using **AES-256-GCM**. Encly does not store a PIN hash.
4. If biometrics are enabled, the same DEK gets a second AES-GCM slot protected by an
   auth-per-use AndroidKeyStore key. Unwrapping requires
   `BiometricPrompt.CryptoObject` with `BIOMETRIC_STRONG`.
5. In user-managed recovery mode, a 12-word BIP39 seed derives a recovery KEK and wraps
   the same DEK in a separate AES-GCM recovery slot.
6. When Encly goes to the background, SQLCipher is closed and Encly's in-memory DEK copy
   is zeroized. The next entry must unwrap the DEK again.

The auto-managed onboarding option deliberately has **no recovery seed**. Losing the PIN
and local unlock material in that mode makes the encrypted database unrecoverable.

The app also disables Android backup/device transfer for protected data and removes
system-visible reminder notifications, clipboard export, plaintext note sharing,
calendar export, and seed export.

See [SECURITY.md](SECURITY.md) for the threat model and reporting process.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · SQLCipher · Coroutines ·
kotlinx.serialization · BIP39 (kotlin-bip39)

Clean architecture: `presentation` → `domain` → `data`, with `core/security`
coordinating the v2 key vault.

## Build

Requirements: **JDK 21**, Android SDK (compileSdk 36), Android Studio.

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew :app:detekt
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

### Release signing (optional)

Release builds are unsigned unless you provide an untracked `keystore.properties` at
the repo root:

```
storeFile=/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Contributing

Issues and PRs are welcome. Security-sensitive changes should keep the key hierarchy and
threat model in [SECURITY.md](SECURITY.md) synchronized with the implementation.

## License

[Apache License 2.0](LICENSE).
