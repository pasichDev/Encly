# Changelog

All notable changes to Encly are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version comes from
`version.properties`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`.

Each release section is mirrored in
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (shown by F-Droid and
IzzyOnDroid) and used as the GitHub Release notes.

## [Unreleased]

## [2.0.0] - 2026-09-24

versionCode 20000.

### ⚠️ Breaking: new vault format

- Storage moves to the **v2 vault**: SQLCipher is keyed by a random 256-bit data key (DEK) that
  is wrapped separately by the PIN (PBKDF2-HMAC-SHA256, 600,000 iterations + AES-256-GCM), by an
  optional auth-bound biometric Keystore key, and by an optional BIP39 recovery seed.
- **1.x data is not migrated.** 1.x had no public users; uninstall it before installing 2.0.
  First-run setup still refuses to create a vault over an encrypted database it cannot open,
  and asks for an explicit wipe instead.

### Added

- Encrypted backups (Settings → Backup): export and import of all notes, tasks and tags as one
  AES-256-GCM file keyed by the recovery seed (HKDF-SHA256, random salt and nonce per file),
  saved through the system file picker. Merge or replace on import, in one transaction.
  "Restore from backup" in onboarding recreates a vault on a new phone from the file and the
  12 words. A vault without a recovery seed can add one before its first export.
- Mandatory 6-digit PIN; optional Class 3 biometric unlock bound to a `BiometricPrompt.CryptoObject`.
- The database is closed and the in-memory key zeroized when the app goes to the background.
- Privacy policy, published at <https://pasichdev.xyz/apps/encly/privacy-policy/> (mirrored in
  [PRIVACY.md](PRIVACY.md)) and linked from the About screen and both store listings.
- `fdroid` and `play` distribution flavors (same application ID). The `fdroid` flavor has no
  Google Play "Rate app" link and no baseline profile, for reproducible builds.
- Signed release workflow: tag `vX.Y.Z` builds, signs (when secrets exist), and publishes APKs
  with `SHA256SUMS`.
- Store listings for Google Play and F-Droid in all 9 languages under `fastlane/metadata/android`,
  a Play App Bundle of the `play` flavor (`bundlePlayRelease`, signed with the same key, all
  languages in the base module so the in-app language picker keeps working), and a Play upload
  workflow that is disabled until explicitly enabled. Release builds no longer embed VCS info.
- The interface is available in English (now the default), Ukrainian, German, French, Spanish,
  Italian, Polish, Portuguese and Dutch, with an in-app language picker (Settings → Language,
  including "System default"); on Android 13+ the choice also appears in the system's per-app
  language settings. A unit test and lint (`MissingTranslation` as an error) keep every locale
  complete.
- Settings → Security: create a recovery phrase later (after the PIN, with the same three-word
  check as onboarding), and "Erase all data" behind the PIN and an explicit confirmation.
- New brand: an "E" lettermark launcher icon with a themed (monochrome) layer, used in the app in
  place of the old lock tile, and one icon set drawn in the app's stroke.
- Unlock animation: after a PIN or fingerprint unlock the logo tile grows to fill the window and
  the notes list slides in under it, without the lock screen flashing.
- Recovery phrase entry as 12 numbered word cells, shared by onboarding, recovery from the lock
  screen, backup import and phrase confirmation, with paste, a per-word BIP39 check and the
  checksum check.
- Editor toolbar: a button that hides the keyboard and brings it back to the block you were
  writing in (also on OEM keyboards that ignore one of the two Android APIs).
- Link blocks show as offline cards: the host as the title and the rest of the address under it.
  Nothing is fetched to build them.
- Onboarding rebuilt as one flow with progress: welcome, PIN with optional fingerprint, why the
  recovery phrase matters, write it down, check three words. "I have a backup" leads to restore;
  "PIN only, no backups" skips the phrase.
- A rebuilt Support page and an updated FAQ; donations (Ko-fi) appear only in the F-Droid build.
- Tag drag-to-reorder, designed empty states, and a discard confirmation in the editor.

### Changed

- Fonts (Playfair Display, Source Sans 3, IBM Plex Sans, Poppins) are bundled in the APK instead
  of being downloaded through Google Play Services.
- Settings → Appearance: five colour themes (Paper, Forest, Ocean, Graphite, Midnight), a
  System / Light / Dark mode and an app-wide font choice (Editorial, Modern, Technical).
- Feedback goes to the GitHub issue tracker instead of a third-party form service.
- `FLAG_SECURE` is always on and applied before the first frame.
- All text fields ask the keyboard not to learn from input (`IME_FLAG_NO_PERSONALIZED_LEARNING`).
- Build: one Kotlin version through a Gradle version catalog, pinned Gradle wrapper checksum.
- Search covers every note whatever tag chip is selected (not notes under a hidden tag), lists
  every match instead of the first five, and ignores checkbox markers and separator lines.
  Note text is parsed once per change, off the main thread; an unreadable note no longer breaks
  the list or search.
- The editor toolbar's move up, move down and delete buttons act on the current block.
- New design system: Playfair Display, Source Sans 3 and IBM Plex Sans on one type scale, shared
  spacing, shapes and components; every screen restyled (lock, notes, editor, tasks, trash,
  tags, settings, backup, dialogs). Tasks are always in the drawer.
- Editor: fields own their text, so fast typing no longer resets; new blocks go after the block
  being edited; hardware Enter adds no stray line break; quotes end with a closing mark.
- Unlocking returns to the note that was open.
- Result messages on the backup and security screens stay visible until read.
- Store listings: texts, feature graphics and screenshots for all 9 languages.

### Removed

- Plaintext note sharing, clipboard copy of notes, seed export and calendar export. (A link
  block's address can still be copied, on request and marked sensitive.)
- The `ui-text-google-fonts` dependency and its Google Play Services font provider.
- Unused libraries.
- The hidden screen-protection preference (screen protection cannot be turned off) and other
  unused code; debug logging calls (release builds strip the remaining failure logs).

### Security

- Android backup and device-to-device transfer are disabled for all vault data.
- See [SECURITY.md](SECURITY.md) for the full threat model and known limitations.

## [1.1.1] - v1

Last release of the v1 storage format (versionCode 30): SQLCipher key derived from a
Keystore-sealed seed hash, optional 4-digit PIN. Superseded by 2.0.0.

[Unreleased]: https://github.com/pasichDev/Encly/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/pasichDev/Encly/releases/tag/v2.0.0
[1.1.1]: https://github.com/pasichDev/Encly/tree/main
