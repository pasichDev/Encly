# Changelog

All notable changes to Encly are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version comes from
`version.properties`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`.

Each release section is mirrored in
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (shown by F-Droid and
IzzyOnDroid) and used as the GitHub Release notes.

## [Unreleased]

## [2.0.1] - 2026-09-25

versionCode 20001.

No changes to the app itself. The release APKs are now signed without touching the zip layout
that Gradle produced, so F-Droid can rebuild Encly from source and ship it with the same
signature as GitHub Releases.

### Changed

- Release signing keeps AGP's alignment (`--alignment-preserved`) and drops the v1 scheme,
  which minSdk 26 never uses.
- Store listings: the PIN wording now matches the app.

## [2.0.0] - 2026-09-25

versionCode 20000.

The first public release. Encly 2.0 is rebuilt around a new encrypted vault, with a PIN bound to
the phone's secure hardware, auto-lock, encrypted backups, a new design and nine languages. It
ships on GitHub Releases first; F-Droid and Google Play follow.

### ⚠️ Breaking: new vault format

- SQLCipher is now keyed by a random 256-bit data key (DEK). The PIN, an optional biometric
  Keystore key and an optional 12-word recovery phrase each wrap that key in their own
  AES-256-GCM slot.
- **1.x data is not migrated.** 1.x had no public users; uninstall it before installing 2.0.
  First-run setup refuses to create a vault over an encrypted database it cannot open, and asks
  for an explicit wipe instead.

### Added

**Security**

- Mandatory 6-digit PIN, bound to this phone: PBKDF2-HMAC-SHA256 (600,000 iterations) is mixed
  with an HMAC from a non-exportable Android Keystore key (StrongBox where the phone has one), so
  PIN guesses can only run on the device. Encly stores no PIN hash. Wrong PINs lock the app out
  from the 5th miss, doubling up to 24 hours; a reboot or a clock change does not shorten it.
- Optional Class 3 biometric unlock, bound to a `BiometricPrompt.CryptoObject`.
- Auto-lock: after you leave the app, the database is closed and its key wiped from memory once
  the delay chosen in Settings → Security has passed (immediately, 15 s by default, 30 s, 1 min
  or 2 min). Turning the screen off or locking the phone locks Encly at once.
- A new Security page: an encryption status card, "How Encly protects your notes", biometric
  unlock, the auto-lock delay, strict keyboard privacy, creating or replacing the recovery
  phrase, and a danger zone with **Erase all data** (hold for 5 s, confirm, then PIN or
  fingerprint).
- Optional 12-word BIP39 recovery phrase: unlocks the vault when the PIN is forgotten, and is
  the key to encrypted backups. It can be created during setup or later, and replaced.
- Encrypted backups (Settings → Backup): all notes, tasks and tags in one AES-256-GCM file keyed
  by the recovery phrase, saved wherever you choose through the system file picker. Import merges
  or replaces in one transaction, and "Restore from backup" in setup rebuilds a vault on a new
  phone from the file and the 12 words.
- Strict keyboard privacy (opt-in): text fields ask the keyboard for no suggestions and no cloud
  prediction. Every field already asks it not to learn from what you type.
- Autofill is excluded, other apps' overlays are hidden (Android 12+), screen content is marked
  sensitive for accessibility services (Android 14+), and anything copied is marked sensitive
  and cleared from the clipboard after 60 s.

**Design and editing**

- A new design: one type scale, spacing and component set across every screen; five colour
  themes (Paper, Forest, Ocean, Graphite, Midnight), System / Light / Dark mode, dynamic colour
  on Android 12+, and three bundled font sets (Editorial, Modern, Technical).
- Page transitions on Material's shared X axis, and an unlock animation in which the logo tile
  grows to fill the window on the first unlock after launch (later unlocks cross-fade).
- A new "E" launcher icon with a themed (monochrome) layer.
- Onboarding as one flow with progress: welcome, PIN with optional fingerprint, why the recovery
  phrase matters, write it down, check three words. "I have a backup" leads to restore; "PIN
  only, no backups" skips the phrase.
- Recovery phrase entry as 12 numbered cells with paste, a per-word BIP39 check and the checksum
  check, used everywhere the phrase is typed.
- Editor: Enter splits a block at the cursor and Backspace merges, Markdown-style shortcuts
  (`# `, `- `, `1. `), multi-line paste into blocks, per-word undo, a button that hides the
  keyboard and brings it back to the block you were writing in, and a discard confirmation.
- Link blocks show as offline cards (host as the title, the rest of the address under it);
  nothing is fetched to build them. Only `http`, `https` and `mailto` links are accepted.
- Tag drag-to-reorder and designed empty states.
- Nine languages: English (now the default), Ukrainian, German, French, Spanish, Italian,
  Polish, Portuguese and Dutch, with an in-app picker (Settings → Language, including "System
  default").
- A rebuilt Support page, an updated FAQ and an open-source licenses page. Donations (Ko-fi)
  appear only in the `fdroid` flavor.

**Distribution**

- `fdroid` and `play` flavors with the same application ID. The `fdroid` flavor has no Google
  Play link and no baseline profile, for reproducible builds.
- Release workflow: a `vX.Y.Z` tag builds both flavors unsigned, signs them with `apksigner`,
  checks the signing certificate against the fingerprint published in the README, and publishes
  the APKs with `SHA256SUMS`. It fails rather than publish an unsigned APK.
- Store listings, screenshots and release notes for all nine languages under
  `fastlane/metadata/android`.
- A privacy policy, published at <https://pasichdev.xyz/apps/encly/privacy-policy/> and linked
  from the About screen and the store listings.

### Changed

- Fonts (Playfair Display, Source Sans 3, IBM Plex Sans, Poppins) are bundled in the APK instead
  of being downloaded through Google Play Services.
- `FLAG_SECURE` is always on and applied before the first frame.
- Feedback goes to the GitHub issue tracker instead of a third-party form service.
- Search covers every note whatever tag is selected, lists every match, and ignores checkbox
  markers and separator lines; an unreadable note no longer breaks the list or search.
- Unlocking returns to the note that was open.
- Editor: fast typing no longer resets a field, new blocks go after the block being edited,
  hardware Enter adds no stray line break, and quotes end with a closing mark.
- Result messages on the backup and security screens stay visible until read.
- Build: one Kotlin version through a Gradle version catalog and a pinned Gradle wrapper
  checksum.

### Removed

- Plaintext note sharing, copying whole notes to the clipboard, seed export, calendar export and
  task reminders. Encly posts no notifications. (A link block's address can still be copied, on
  request and marked sensitive.)
- The Google Play Services font provider (`ui-text-google-fonts`).
- `androidx.security:security-crypto`, which is deprecated upstream.
- The hidden screen-protection preference: screen protection can no longer be turned off.

### Security

- Android backup and device-to-device transfer are disabled for all vault data.
- The full threat model and known limitations are in
  [SECURITY.md](https://github.com/pasichDev/Encly/blob/main/SECURITY.md).

## [1.1.1]

The last release of the old storage format (versionCode 30): the SQLCipher key was derived from a
Keystore-sealed seed hash, with an optional 4-digit PIN. It had no public users; superseded by
2.0.0.

[Unreleased]: https://github.com/pasichDev/Encly/compare/v2.0.1...HEAD
[2.0.1]: https://github.com/pasichDev/Encly/releases/tag/v2.0.1
[2.0.0]: https://github.com/pasichDev/Encly/releases/tag/v2.0.0
