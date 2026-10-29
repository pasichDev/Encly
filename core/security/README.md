# :core:security

The platform-free security core of Encly. It depends on `:core:common` and `:core:crypto` only.

- Key hierarchy: `VaultStore` (the slot file, its codec and checksum), `PinSlots`, `DuressSlots`, `SeedPhraseManager`
  and `Mnemonic` (recovery phrase), `Hkdf`, `Utf8`.
- Unlock logic: `AuthenticationManager` (PIN, wipe PIN, duress PIN, lockout), `SecurityManager` (the vault session
  and the wipe-PIN erase), `SessionLockManager` and `AutoLock` (re-lock), `KeyboardPrivacy`.
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

No iOS targets while `:core:crypto` has none. Line coverage is gated at 85 %.
