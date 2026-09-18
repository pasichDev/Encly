# Security

Encly is a local encrypted notes application. This document describes the current v2
security model, its trust boundaries, and known limitations.

## Key hierarchy

Encly does **not** derive the SQLCipher passphrase directly from a PIN or seed.

- A fresh vault generates a random 256-bit **DEK**.
- SQLCipher uses that DEK as its database key.
- Unlock factors each protect their own AES-GCM envelope containing the same DEK.

### PIN slot

- The app requires a 6-digit PIN.
- A random 16-byte salt and **PBKDF2-HMAC-SHA256 (600,000 iterations)** derive a
  256-bit KEK from the PIN.
- That KEK wraps the DEK with **AES-256-GCM** and authenticated associated data.
- No separate PIN hash is stored.
- Wrong PINs fail GCM authentication and trigger a progressive software lockout.

### Biometric slot

Biometric unlock is optional.

- Encly generates an AES-256 AndroidKeyStore wrapping key.
- The key is configured with `setUserAuthenticationRequired(true)`.
- On Android 11+ it uses auth-per-use `AUTH_BIOMETRIC_STRONG`; older supported Android
  versions use the equivalent per-use biometric validity configuration.
- The DEK can only be wrapped/unwrapped through a `BiometricPrompt.CryptoObject`
  carrying the authenticated Cipher.
- The biometric key is invalidated when biometric enrollment changes.

A successful biometric prompt by itself is therefore insufficient to open the
database. The authenticated cryptographic operation must release the DEK.

### Recovery slot

User-managed onboarding creates an optional BIP39 recovery slot.

- Encly generates a 12-word BIP39 seed.
- The normalized seed is hashed and processed through HKDF-SHA256 with domain-separated
  recovery context.
- The resulting recovery KEK wraps the same random DEK with AES-256-GCM.
- Entering the correct recovery seed can therefore recover local database access even
  when the PIN is unavailable.

Auto-managed mode intentionally creates no recovery slot.

## Session lifecycle

- The database is not considered committed until mandatory PIN setup succeeds.
- Interrupted first-run setup restarts onboarding and discards incomplete vault slots.
- When the app backgrounds, SQLCipher is closed and Encly's in-memory DEK copy is
  zeroized.
- The next foreground entry must unwrap the DEK again through PIN, biometric, or
  recovery.
- `FLAG_SECURE` is applied before the first Activity frame and cannot be disabled in
  settings.

## Outbound data policy

The beta security boundary intentionally removes system-visible plaintext features:

- no task reminder notifications or notification actions;
- no exact-alarm reminder pipeline;
- no seed clipboard, file, Drive, or generic share export;
- no plaintext note share flow;
- no note/link clipboard copy flow;
- no task export to the system calendar;
- no FileProvider retained for those export flows.

The app requests no `INTERNET` permission and performs no analytics or sync.

## Platform storage

- Android app sandbox isolates local files from ordinary apps.
- `allowBackup=false` is set.
- Database/security state is excluded from cloud backup and device transfer.
- PIN slot metadata is stored in encrypted preferences; the PIN-derived AES-GCM slot
  remains the cryptographic gate to the DEK.

## Threat model

### Designed to resist

- ordinary cross-app filesystem access;
- offline theft of the encrypted SQLCipher database;
- casual physical access after Encly has re-locked;
- lock-screen / notification leakage from task reminders;
- screenshots, screen recording, and recent-app snapshots through `FLAG_SECURE`;
- bypassing biometric UI without successfully using the auth-bound Keystore Cipher.

### Important limitations

- A rooted or fully compromised OS can observe process memory, UI input, or execute code
  in the app's security context. Encly cannot provide a trustworthy boundary against a
  hostile kernel / system image.
- A numeric PIN has finite entropy. PBKDF2 increases offline attack cost but does not
  turn a short PIN into a high-entropy secret.
- Recovery currently restores access to the local encrypted database. A future portable
  backup format must include the encrypted database plus its non-secret v2 envelope
  metadata; the seed alone cannot recreate missing ciphertext.
- User-visible seed words necessarily exist in UI memory while being displayed or
  entered. Encly minimizes avoidable copies but cannot guarantee JVM/Compose heap
  zeroization of every immutable string representation.

## Reporting a vulnerability

Please report security issues privately rather than publishing exploit details in a
public issue. Use the maintainer contact channels and allow reasonable time for a fix.
