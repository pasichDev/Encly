# :core:security

The platform-free security core of Encly. It depends on `:core:common` and `:core:crypto` only.

- Key hierarchy: `VaultStore` (the slot file, its codec and checksum), `PinSlots` (slot version 3), `DuressSlots` (the duress and wipe
  slot shape, for the slot version it is given) and `SecretSlots` (version 3 and version 4 by header), `SeedPhraseManager` and `Mnemonic` (recovery phrase), `Hkdf`, `Utf8`.
- The unlock secret: `UnlockSecretPolicy` says what the user types, what may be set, how it is stretched, what the
  device factor signs and which slot format it lives in. `PinSecretPolicy` (the 6 to 12 digit PIN, PBKDF2, version 3) is
  here and is what Android and iOS use; the desktop binds `DesktopPasswordPolicy` from `:core:keystore` instead
  (a passphrase, Argon2id, version 4) and excludes the PIN policy from its graph.
- Unlock logic: `AuthenticationManager` (the secret, wipe secret, duress secret, lockout), `SecurityManager` (the vault
  session and the wipe erase), `SessionLockManager` and `AutoLock` (re-lock), `KeyboardPrivacy`.
- Backups: the file format, the cipher and the key derivation (`core/backup`).

Everything a platform provides is an interface here, implemented in `:app` and bound in its graph: `VaultFileIo`
(fsync and atomic replace; `JvmVaultFileIo` is in this module for both JVM targets), `VaultDatabase`,
`PinHardwareFactor`, `BiometricManager` / `BiometricHost`, `LockoutClock`, `DeviceLockWatcher`, `MainThreadTimer`,
`KeyValueStore`, `HandoffStagingSweeper`.

Classes keep their Metro annotations (`@Inject`, `@SingleIn(AppScope::class)`); the app graph builds them. Locks are
atomicfu `SynchronizedObject`s (the same critical sections as the `synchronized` blocks they replace).

| Target | Source sets | Checked in |
|---|---|---|
| Android | `commonMain`, `jvmAndAndroidMain` | `androidHostTest` |
| Desktop JVM | `commonMain`, `jvmAndAndroidMain` | `desktopTest` (also `SecurityBoundaryTest`) |

iOS targets (device and simulator) with the golden tests of every format running on the simulator. Line coverage is gated at 85 %.
