# :core:crypto

The one door to cryptographic primitives, and BIP-39.

- `Crypto`: AES-256-GCM (12-byte nonce, 16-byte tag), HKDF-SHA-256, PBKDF2-HMAC-SHA-256, SHA-256, the platform
  CSPRNG and a constant-time comparison. Arguments are checked here; the provider is behind `PlatformCrypto`.
- `Bip39`, `Bip39Wordlist`: English mnemonics, entropy to words and the checksum check. Encly never turns the words
  into a BIP-39 seed; its keys come from SHA-256 of the words (see `:core:security`).

| Target | Source sets | Checked in |
|---|---|---|
| Android | `commonMain`, `jvmAndAndroidMain` | `androidHostTest` |
| Desktop JVM | `commonMain`, `jvmAndAndroidMain` | `desktopTest` (also `CryptoBoundaryTest`) |
| iOS (device, simulator) | `commonMain`, `iosMain` | `iosSimulatorArm64Test` (`CryptoTest` and the BIP-39 tests run there too) |

`jvmAndAndroidMain` holds the JCA implementation (`JcaCrypto`) both JVM targets share.

## iOS

`iosMain` uses only Apple's own libraries, no cryptography is written by hand: CommonCrypto for SHA-256, HMAC (HKDF is
RFC 5869 over it, as on the JVM) and PBKDF2 (`CCKeyDerivationPBKDF`), `SecRandomCopyBytes` for randomness,
`timingsafe_bcmp` for the comparison and CryptoKit's `AES.GCM`. CommonCrypto has no public AES-GCM and CryptoKit is
Swift-only, so `src/iosMain/swift/EnclyAesGcm.swift` exports two C functions over it (`@_cdecl`, declared in
`src/nativeInterop/cinterop/enclyaesgcm.h`). Gradle compiles it per SDK with `SwiftStaticLibrary` (`build-logic`; needs
Xcode) and cinterop links the static library into the klib. Keys and data cross as raw pointers into the Kotlin arrays,
so they can be wiped like on the JVM. The golden vectors of `CryptoTest` and `:core:security` pin the bytes: they are
the same on every platform.

Line and branch coverage are gated at 100 % (Kover measures the JVM targets; the iOS code is covered by
`iosSimulatorArm64Test`).
