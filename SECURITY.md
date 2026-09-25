# Security

Encly is a local encrypted notes application. This document describes the security model of
Encly 2.0 (vault format v3), its trust boundaries, and known limitations.

## Key hierarchy

Encly does **not** derive the SQLCipher passphrase directly from a PIN or seed.

- A fresh vault generates a random 256-bit **DEK**.
- SQLCipher uses that DEK as a **raw key** (`x'<64 hex>'`): the DEK is already uniformly
  random, so SQLCipher's own PBKDF2 over a passphrase is skipped. `cipher_memory_security` is
  switched on (best effort) so SQLCipher wipes the memory it frees.
- Unlock factors each protect their own AES-GCM envelope containing the same DEK.

```
DEK (random 256 bit) ── SQLCipher raw key
├─ PIN slot       AES-GCM(kek_pin)       kek_pin = HKDF(PBKDF2(pin, salt, 600k) ‖ HMAC_keystore(salt ‖ pin))
├─ biometric slot AES-GCM(keystore key)  auth-per-use, BIOMETRIC_STRONG CryptoObject
├─ recovery slot  AES-GCM(kek_rec)       kek_rec = HKDF(SHA-256(normalized 12 words), "encly/recovery/kek/v3")
└─ backup-key slot AES-GCM(HKDF(DEK))    holds backupRoot = HKDF(SHA-256(words), "encly/backup/root/v1")
```

### PIN slot

- The app requires a 6-digit PIN.
- The PIN KEK has two halves, mixed with HKDF-SHA256 (`info = "encly/pin/kek/v3"`, the slot's
  random 16-byte salt as HKDF salt):
  - **software:** PBKDF2-HMAC-SHA256 (600,000 iterations) of the PIN and the salt;
  - **device-bound:** HMAC-SHA256 of `salt ‖ PIN` under a non-exportable **AndroidKeyStore**
    key, generated in **StrongBox** where the device has one (falling back to the TEE), and
    usable only while the device is unlocked (`setUnlockedDeviceRequired`, API 28+).
- That KEK wraps the DEK with **AES-256-GCM**. The slot is `version ‖ salt ‖ IV ‖ ciphertext`;
  the version and salt are authenticated as associated data (`"encly/pin/slot/v3"`).
- No separate PIN hash is stored.
- Because of the device-bound half, a copied slot cannot be attacked off the device: every PIN
  guess needs this phone's secure hardware. PBKDF2 stays at full cost as defence in depth, for
  the case where the Keystore key itself were ever extracted.
- If the Keystore key is lost (a Keystore reset, a key the system invalidated), the PIN can no
  longer unlock. The lock screen says so and switches to the recovery phrase (or keeps
  fingerprint unlock); the vault without either routes to the damaged-vault screen. A new PIN
  set after a recovery unlock gets a new Keystore key.
- Wrong PINs fail GCM authentication. **Lockout:** every attempt is recorded durably (atomic
  file write) *before* the key derivation runs, under one lock, so killing the app mid-check or
  racing attempts never yields a free guess. From the 5th consecutive miss the lockout is 30 s
  and doubles with each further miss (1, 2, 4 … 32 min, ~1 h …) up to 24 h. It runs on
  `SystemClock.elapsedRealtime`, so changing the wall clock does not end it; across a reboot
  the remaining penalty is kept and restarts from boot (a reboot never shortens a lockout). A
  failure of the Keystore itself is not counted as a guess. There is no auto-wipe.

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

- The system screen lock (device PIN, pattern or password) is never accepted in place of a
  biometric: the prompt allows `BIOMETRIC_STRONG` only, and falling back means Encly's own
  PIN.
- Turning biometric unlock **on** needs a biometric prompt that wraps the DEK through the
  new key's CryptoObject; turning it **off** needs a Class 3 biometric confirmation, and
  deletes the slot and the key. Only one such prompt can be open at a time, and the switch
  is disabled on devices without enrolled strong biometrics.

### Recovery slot

Onboarding offers an optional BIP39 recovery slot.

- Encly generates a 12-word BIP39 seed.
- The seed is normalized (lower case, single spaces; the one normalization shared with the
  backup key), hashed with SHA-256 and processed through HKDF-SHA256 with domain-separated
  recovery context.
- The resulting recovery KEK wraps the same random DEK with AES-256-GCM.
- Entering the correct recovery seed can therefore recover local database access even
  when the PIN is unavailable.

Choosing "PIN only, no backups" during onboarding intentionally creates no recovery slot. One
can be added later from an unlocked session (Settings → Security, or Settings → Backup before
the first export, after re-entering the PIN): the live DEK is wrapped under a newly generated
seed that the user writes down and confirms.

**Replacing the phrase.** Settings → Security → "Replace recovery phrase" (unlocked session,
after PIN or biometric re-authentication, and a confirmation) generates 12 new words, shows
them and checks three of them. Only then are the recovery slot and the backup-key slot rewritten
for the new words, in one atomic write that drops the old recovery slot. The old words then
open nothing on this device. Backups exported before still open **only with the old words**
(their key derives from them); the UI says so before and during the replacement.

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

- The info strings are distinct from the recovery KEK's (`encly/recovery/kek/v3`), so a backup
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
  **Export** needs no open vault while it is shown (the file is already sealed), so backgrounding
  locks as usual and the sealed file is still written to the chosen document. For **import**,
  `SessionLockManager` keeps the vault open only when Encly itself opened the picker within the
  last 5 seconds, for at most 60 seconds measured on `elapsedRealtime` (deep sleep counts). The
  screen turning off ends the grace at once and closes the vault; returning after the grace
  expired, or with the keyguard up, closes it the moment the app is back; the picker's result
  ends the grace.
- No temp files, no plaintext on disk, no logging of payloads, words, keys or SQLite error
  messages (which can quote row content). Derived keys and the plaintext payload buffer are
  zeroized after use.

## Session lifecycle

- The database is not considered committed until mandatory PIN setup succeeds.
- Interrupted first-run setup restarts onboarding and discards incomplete vault slots.
- When the app backgrounds, SQLCipher is closed and Encly's in-memory DEK copy is
  zeroized, after the user's auto-lock delay (Settings → Security: immediately, 15 s by
  default, 30 s, 1 min or 2 min; measured on `elapsedRealtime`, so deep sleep counts). The
  screen turning off or the device locking closes it at once, whatever the delay. ViewModels
  holding decrypted content (notes list, tasks, editor, a decrypted backup, new recovery
  words) drop it at that moment, not only when the UI resumes.
- Within the delay the vault stays open in the background: the DEK and decrypted content are
  in memory, and the recents thumbnail is still blocked by `FLAG_SECURE`. Choose
  "Immediately" to close it the moment Encly leaves the screen.
- The next foreground entry must unwrap the DEK again through PIN, biometric, or
  recovery.
- A process started with a committed, closed vault starts in the locked state, and a central
  navigation guard sends every vault screen to the lock screen while the database is closed, so
  a back stack restored after process death shows nothing. Search queries and tag drafts are
  not kept in saved instance state. Navigation deep-link extras are stripped from incoming
  intents.
- An unlock whose screen is gone by the time the key derivation finishes (or that finishes in
  the background) closes the vault again. A re-lock during the unlock animation cancels it.
- The PIN typed on the lock screen is kept in a `CharArray` that is wiped after the attempt
  (never a `String`); unlock DEK copies are wiped after use. While open, the database accepts a
  second unlock call only with the same key, compared in constant time.
- `FLAG_SECURE` is applied before the first Activity frame and cannot be disabled in
  settings.
- **Erase all data** (Settings → Security, danger zone) is held for 5 seconds, confirmed in a
  dialog, then re-authenticated with the PIN or a Class 3 biometric. It closes and deletes the
  database, every key slot, the lockout state, Encly's Keystore keys (PIN factor and biometric
  key) and the vault flags (onboarding state, last export, strict keyboard privacy), and
  returns to onboarding. There is no undo; only an exported
  backup brings the data back.

## Outbound data policy

Encly intentionally leaves out system-visible plaintext features:

- no notifications at all: no notification permission, channel, alarm or scheduled work;
- no seed clipboard, file, Drive, or generic share export (encrypted backups never contain the
  seed; see "Encrypted backups");
- no plaintext note share flow;
- no task export to the system calendar;
- no FileProvider retained for those export flows.

**Clipboard.** Everything Encly puts on the clipboard (a text-field copy, a copied link) is
marked sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`, honoured from Android 13) and cleared
after 60 seconds unless something else was copied since. When the system hides the clipboard
from a backgrounded app, the clear happens without that check. A recovery phrase pasted into
the 12 cells is removed from the clipboard right away.

**Autofill, accessibility, overlays.** The window excludes itself and all its fields from
autofill services. On Android 14+ its content is marked accessibility-data-sensitive, so only
services that declare themselves accessibility tools (TalkBack, Switch Access) can read it. On
Android 12+ other apps' overlay windows are hidden while Encly is in front
(`HIDE_OVERLAY_WINDOWS`).

**Keyboard.** All Compose text fields run inside a shared input boundary that requests
`IME_FLAG_NO_PERSONALIZED_LEARNING`. The opt-in **Strict keyboard privacy** setting
(Settings → Security, off by default) additionally presents every text field as a
visible-password field with `TYPE_TEXT_FLAG_NO_SUGGESTIONS`: compliant keyboards then show no
suggestions, use no cloud prediction and receive no surrounding text. The residual risk is the
keyboard itself: it still receives every key it types, and a malicious or non-compliant IME can
ignore all of these flags (see "Important limitations"). The cost is no autocorrect, and some
keyboards hide emoji or voice input in password fields.

The app requests no `INTERNET` permission and performs no analytics or sync.

## Platform storage

- Android app sandbox isolates local files from ordinary apps.
- `allowBackup=false` is set.
- Database/security state is excluded from cloud backup and device transfer.
- The key slots (PIN, recovery, backup-key and biometric envelopes) and the PIN lockout state
  live in one app-private file, `no_backup/vault_state_v3.bin`. Every secret in it is already
  an AES-256-GCM envelope, so it needs no further encryption layer and **no Keystore key to be
  read**: a broken Keystore or Tink keyset can no longer make the vault state unreadable at
  startup. Every change rewrites the file to a temp file, `fsync`s it and renames it over the
  old one (an atomic replace), so a crash leaves the old or the new state, never a mix. A
  SHA-256 over the content detects damage: a damaged file reads as empty (nothing unlocks),
  refuses writes, and startup routes to the damaged-vault screen instead of crashing. (That
  digest detects accident, not tampering; tampering with an envelope fails its GCM tag.)
- The envelopes are what protect the DEK, and each is only as strong as its KEK. The file
  itself is readable by anyone who can read the app's private storage (root, a forensic
  extraction of an unlocked device). That is why the PIN KEK includes a non-exportable Keystore
  factor: with only a copy of the file, the 10^6 PINs can not be tried off the device.
- Non-secret flags (onboarding finished, last export time, strict keyboard privacy) are kept in
  plain app-private preferences.
- Encly no longer uses `androidx.security:security-crypto` (deprecated upstream).

## Network and third parties

- The merged release manifest requests no `INTERNET` permission (checked in CI).
- Editor fonts are bundled OFL font files. Encly does not use the Google Play Services
  downloadable-font provider, so no font request goes to Google and the app works the same on
  devices without Google Play Services.
- The About screen offers links (privacy policy, GitHub issues, developer email and, only in the
  `play` flavor, the Play Store listing), the open-source licenses page links each library's and
  font's license, and, only in the `fdroid` flavor, the Support page links the developer's Ko-fi
  page. They open in another app **only after an explicit tap**; Encly itself sends nothing.
- **Links in notes are a deliberate exception** to "nothing leaves the vault". A link block
  opens only from its sheet, which first shows the full address, after an explicit tap on
  "Open", through the system chooser. Only `http`, `https` and `mailto` are saved or opened;
  addresses with user info, backslashes, whitespace or control characters are refused, and
  hosts are shown in punycode, so the card cannot name a different host than the one that
  opens. The receiving app (a browser, a mail client) then sees that one address. Encly
  fetches no previews: a link card is built from the stored address alone.

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
- A numeric PIN has finite entropy (10^6 values). Off the device, a copied PIN slot is useless
  without the Keystore factor. **On** a rooted device that is unlocked, an attacker can drive
  the Keystore key from the app's own security context: guesses then run at the secure
  hardware's HMAC speed (StrongBox is much slower than the TEE), the PBKDF2 half can be
  computed off the device, and the lockout file can be reset. A device that has never been
  unlocked since boot, or is locked (API 28+), refuses the key entirely. Where StrongBox is
  missing the key lives in the TEE; on devices without hardware-backed Keystore it is software
  only.
- The lockout is enforced by the app, not by the secure hardware.
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
  no-personalized-learning flag (and, with strict keyboard privacy, a password-type field
  without suggestions), but an IME sees every key typed and may ignore these requests.
- Clipboard clearing is best effort: a clipboard manager or another app may read or keep a
  clip during its 60 seconds.

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
| 2.0.x | v3 vault (random DEK in PIN / biometric / recovery slots; Keystore-bound PIN KEK; slot file) | ✅ yes |
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
