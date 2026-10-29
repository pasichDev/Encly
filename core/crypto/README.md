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

`jvmAndAndroidMain` holds the JCA implementation (`JcaCrypto`) both JVM targets share.

## iOS

There are no iOS targets yet, on purpose. Apple's public CommonCrypto gives SHA-256, HMAC, PBKDF2
(`CCKeyDerivationPBKDF`) and `SecRandomCopyBytes`, but no AES-GCM (`CCCryptorGCM` is private API), and CryptoKit
cannot be called from Kotlin/Native without a Swift bridge. A placeholder that does weaker crypto is not an option,
so the targets are added (`id("encly.kmp-ios")`) together with a real AES-GCM implementation. Until then
`:core:security` has no iOS target either.

Line and branch coverage are gated at 100 %.
