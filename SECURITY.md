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

Auto-managed mode intentionally creates no recovery slot. One can be added later from an
unlocked session (Settings → Backup, after re-entering the PIN): the live DEK is wrapped under a
newly generated seed that the user writes down and confirms.

## Encrypted backups

Android backup stays disabled. The only way data leaves the device is an **encrypted backup file
the user exports explicitly** (Settings → Backup), stored wherever they choose through the
Storage Access Framework. Encly requests no storage permission and no network access for it;
the file is written only to the single document the user picks. Its storage location, and any
copying or syncing of it afterwards, is the user's choice and outside Encly's control: the file
must therefore be safe to keep anywhere, which is why it is sealed with the recovery-seed key
below and holds no vault key material. This is also why the store listings declare that Encly
collects and shares no data (see [PRIVACY.md](PRIVACY.md#encrypted-backup-file)).

### What a backup contains

Every note (title, the serialized block list verbatim so every block type survives, trash
state, created/edited timestamps), every tag (name, visibility, order) and every task (title,
description, completion, created/completed dates, priority, order). Records carry a
stable random `uid`; links between them (note → tag, task → tag) are stored as uids, never as
per-device database ids.

A backup **never** contains the DEK, the PIN or biometric slots, lockout state, the recovery
seed, the backup key, or app settings.

### Key: the recovery seed, not a separate password

```
seedHash   = SHA-256(normalized BIP39 words)
backupRoot = HKDF-SHA256(ikm = seedHash, salt = "encly/backup/salt/v1", info = "encly/backup/root/v1")
fileKey    = HKDF-SHA256(ikm = backupRoot, salt = <32 random bytes per file>, info = "encly/backup/file/v1")
```

- The info strings are distinct from the recovery KEK's (`encly/recovery/kek/v2`), so a backup
  key can never unwrap the vault and a recovery KEK can never open a backup.
- Each file has its own random salt, so each file has its own key; the 12-byte GCM nonce is
  random per file as well.
- The vault keeps `backupRoot` in a **backup-key slot**: AES-256-GCM under a sub-key of the DEK
  (`HKDF(DEK, "encly/backup/slot/salt/v1", "encly/backup/slot/kek/v1")`, so the SQLCipher key
  itself is never used as an AES-GCM key). An unlocked session can therefore export without
  asking for the words, and the same 12 words re-derive `backupRoot` on a fresh install.
- A fast KDF is enough: a BIP39 phrase carries 128 bits of entropy, so brute force is
  infeasible regardless of the KDF cost.
- **No backup passphrase.** A user-chosen passphrase would be the weakest link of a file that can
  end up anywhere (cloud drives, e-mail) and could be attacked offline forever; a PBKDF2/Argon2
  work factor only slows that down linearly. It would also be a second secret to lose. Instead,
  exporting from a vault without a recovery seed first creates one: Encly shows 12 new words,
  the user types three of them back, and the seed becomes both the vault's recovery slot and the
  backup key. A vault created before the backup-key slot existed asks for its words once.

### Export and import rules

- Export and import require an unlocked session **and** re-authentication (PIN, or a
  Class 3 biometric confirmation when biometric unlock is on) at the start of each operation.
- Export seals the file in memory before the save dialog opens; nothing is written if sealing
  fails, and the plaintext payload buffer is zeroized right after sealing. A vault whose file
  would exceed the 64 MiB import limit is refused (`TOO_LARGE`) instead of exported, so every
  file Encly writes can be restored.
- Export never overwrites: a picked document that already holds data is refused
  (`TARGET_EXISTS`), because truncating it first would lose the previous backup whenever the
  new write then fails. The written file is read back and compared; a failed or incomplete
  write is reported and the new document deleted where the provider allows it.
- Import reads the file (at most 64 MiB), checks magic, format version and header structure
  before asking for the words, then authenticates and decrypts in memory, then validates the
  payload (schema version, unique uids, links that resolve, known priorities) — all **before
  touching the database**. It is then applied in one SQLite transaction: any failure rolls the
  whole import back.
- Merge adds only records whose uid is not in the vault yet (local versions win); replace deletes
  every note, tag and task first and needs an explicit confirmation.
- Wrong words and any modification of the header or ciphertext fail AES-GCM authentication and
  are reported as one error ("these words don't open this backup, or the file was modified"),
  with nothing written.
- Restoring on a fresh install (onboarding "Restore from backup") decrypts and validates the file
  first, then creates the new vault with those words as its recovery seed, and writes the
  backup once the mandatory PIN setup has opened the vault — **before** onboarding is
  committed. If the import fails, nothing is committed and the vault is closed; the user can
  retry or continue with an empty vault. A process killed meanwhile restarts onboarding rather
  than opening an empty vault. Until then the decrypted payload is held in memory only, and an
  uncommitted setup vault is not re-locked by backgrounding (setup closes it itself if it
  finishes in the background).
- The system file picker is another app, so the process briefly goes to the background.
  `SessionLockManager` keeps the vault open only when Encly itself opened the picker within the
  last 5 seconds; the vault is closed anyway if the user stays away for more than 5 minutes.
- No temp files, no plaintext on disk, no logging of payloads, words, keys or SQLite error
  messages (which can quote row content). Derived keys and the plaintext payload buffer are
  zeroized after use.

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

- no notifications at all: no notification permission, channel, alarm or scheduled work;
- no seed clipboard, file, Drive, or generic share export (encrypted backups never contain the
  seed; see "Encrypted backups");
- no plaintext note share flow;
- no note/link clipboard copy flow;
- no task export to the system calendar;
- no FileProvider retained for those export flows.

All Compose text fields run inside a shared input boundary that requests
`IME_FLAG_NO_PERSONALIZED_LEARNING` from the Android IME. The app requests no
`INTERNET` permission and performs no analytics or sync.

## Platform storage

- Android app sandbox isolates local files from ordinary apps.
- `allowBackup=false` is set.
- Database/security state is excluded from cloud backup and device transfer.
- The v2 slot metadata (PBKDF2 salt, AES-GCM envelopes, lockout counters, biometric flag) is
  kept in `secure_prefs_v2`, an `EncryptedSharedPreferences` file from
  `androidx.security:security-crypto` whose master key lives in AndroidKeyStore.
  That library is **deprecated upstream** and receives no further fixes. Encly does not rely on
  it for confidentiality of the DEK: every slot is already an AES-256-GCM envelope, so the
  encrypted-preferences layer only hides non-secret metadata. It stays because the
  vault metadata is stored in its format; replacing it requires a migration of that file and is
  tracked as future work.

## Network and third parties

- The merged release manifest requests no `INTERNET` permission (checked in CI).
- Editor fonts are bundled OFL font files. Encly does not use the Google Play Services
  downloadable-font provider, so no font request goes to Google and the app works the same on
  devices without Google Play Services.
- The About screen offers links (privacy policy, GitHub issues, developer email and, only in the
  `play` flavor, the Play Store listing). They open in another app **only after an explicit
  tap**; Encly itself sends nothing.

## Threat model

### Designed to resist

- ordinary cross-app filesystem access;
- offline theft of the encrypted SQLCipher database;
- casual physical access after Encly has re-locked;
- lock-screen / notification leakage (the app posts no notifications);
- screenshots, screen recording, and recent-app snapshots through `FLAG_SECURE`;
- bypassing biometric UI without successfully using the auth-bound Keystore Cipher.

### Important limitations

- A rooted or fully compromised OS can observe process memory, UI input, or execute code
  in the app's security context. Encly cannot provide a trustworthy boundary against a
  hostile kernel / system image.
- A numeric PIN has finite entropy. PBKDF2 increases offline attack cost but does not
  turn a short PIN into a high-entropy secret.
- The recovery seed restores the local vault (recovery slot) and opens encrypted backups. On a
  new phone the seed alone recreates nothing: the user also needs a backup file, and only data
  up to that export is restored.
- Anyone with the 12 words and a backup file can read that backup. Once exported, a file cannot
  be revoked; a backup's secrecy never exceeds the seed's.
- A backup's size and timestamp reveal roughly how much data it holds. The file name is chosen
  by the user (the default contains only the date).
- Decrypted backup content passes through JVM strings while it is parsed and imported; like the
  editor itself, Encly cannot zeroize those copies.
- User-visible seed words necessarily exist in UI memory while being displayed or
  entered. Encly minimizes avoidable copies but cannot guarantee JVM/Compose heap
  zeroization of every immutable string representation.
- A third-party keyboard is part of the device trust boundary. Encly requests Android's
  no-personalized-learning flag, but an IME may ignore that request.

## Distribution and signing

GitHub Releases, Google Play and F-Droid ship the same source under the same application ID.
All channels are meant to carry the same release signing certificate (Play App Signing with the
uploaded release key; F-Droid via reproducible builds of the `fdroid` flavor), whose SHA-256 is
published in the README. Android refuses to update an install with an APK signed by a different
certificate, so a mismatch shows up as a failed update rather than a silent replacement; never
uninstall to "fix" it without an exported backup, because uninstalling deletes the vault.

## Supported versions

| Version | Storage format | Security fixes |
|---|---|---|
| 2.0.x (beta) | v2 vault (random DEK in PIN / biometric / recovery slots) | ✅ yes |
| 1.x (≤ 1.1.1, versionCode ≤ 30) | v1 (seed-derived SQLCipher key, 4-digit PIN) | ❌ no; not migrated, reinstall 2.0 |

Reports should include the exact app version (About screen), where it was installed from, and
the Android version.

## Reporting a vulnerability

Please report security issues **privately**; do not open a public issue or pull request with
exploit details.

- Preferred: [GitHub private vulnerability reporting](https://github.com/pasichDev/Encly/security/advisories/new).
- Email: **pasichdev@outlook.com** (subject starting with `[Encly security]`).

Include the app version, Android version and device, the steps to reproduce, and the impact you
expect. **Never send real seed words, PINs or note content**; a test vault is enough.
Please allow reasonable time for a fix before public disclosure. Reporters are credited in the
release notes unless they prefer otherwise.
