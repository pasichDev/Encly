# Changelog

All notable changes to Encly are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version comes from
`version.properties`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`.

Each release section is mirrored in
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (shown by F-Droid and
IzzyOnDroid) and used as the GitHub Release notes.

## [Unreleased]

## [2.3.0] - 2026-10-26

versionCode 20300.

### Added

- Pin notes to the top of the list, archive them (a new Archive screen in the drawer; archived
  notes leave the list, search and tag counts) and mark them with one of eight colours. The
  colours are tints of your theme, so they read well in all five themes, light and dark.
- Swipe actions on note cards, in the list, tag views and the Archive: swipe right to pin or
  unpin, left to archive (in the Archive: to take the note out of it). In Settings → Swipe
  actions you choose pin, archive, next colour, move to the trash (it always asks first) or
  nothing for each direction, or turn swipes off. The action's colour and icon show behind the
  card, with a haptic tick when it is armed; with system animations off the card does not
  animate. A failed write springs the card back. TalkBack gets the same actions in the note's
  actions menu.
- Export as Markdown: one note as a `.md` file from its menu, or every note outside the trash as
  a `.zip` (archived notes in `archive/`, plus `tasks.md` with sub-tasks and priorities) from
  Settings → Backup. The suggested file names carry a date, never a note title. These files are
  **not encrypted**: Encly warns you first, asks for your PIN (or biometrics) again, and writes
  only to the document you create in the save dialog.
- Default text size and line spacing for every note in Settings → Appearance. The sliders in a
  note's menu now set that note's own values, with "Use default text settings" to go back (#76).
  Line height grows with large text instead of staying fixed.
- A checklist can be locked from the block tools (the chevron): its text takes no focus and no
  keyboard while its boxes still tick, and it can be unlocked again from the toolbar or with
  TalkBack (#76).
- Keyboard shortcuts in the editor: Ctrl or Cmd+Z, Ctrl or Cmd+Shift+Z and Ctrl or Cmd+Y undo and
  redo across the whole note (the title and the find bar keep their own undo) (#82).
- The PIN keypad sits at the bottom of the screen on the lock screen and the PIN checks, within
  reach of one hand, and a long press on delete clears every digit (#76).
- About links to the source code on GitHub, in every flavor, and Import notes has its own entry
  in the drawer, above Settings.

### Changed

- Editor toolbar: undo and redo are on the toolbar itself, and the plus became a chevron ("More
  block tools") that opens headings, link, separator and block moves (#76).
- Checklist boxes are smaller, grow with the text size and sit centred on the first line of their
  item, with a fixed gap and tighter rows (#76).
- Support is a highlighted button in the drawer, and the Support page explains where donations
  go and offers a single Ko-fi card (`fdroid` flavor only).
- The database moves to version 6 (pin, archive and colour on notes, then a note's own text size
  and spacing, migrated in place). Installing an older Encly over this version is not supported:
  Room refuses to open a newer database, so a downgrade fails to open the vault. Export a backup
  first if you might go back.
- A backup made with this version is schema 4. Older Encly versions (2.2.1 and before) refuse it
  as "made by a newer version, update the app" instead of failing; backups made by older versions
  still restore, with their notes unpinned, unarchived and uncoloured.

### Fixed

- Blank lines you add between paragraphs are no longer erased when a note is closed and reopened
  (#76), and note cards in the list show one block per line.
- The hints in empty editor blocks ("Text", "List item", ...) show only on the block you are in
  and are much lighter, so they no longer look like typed text (#76).
- Undo and redo no longer close the keyboard: the cursor stays in the block you were editing and
  returns to where it was (#76, #82).
- Arrow keys move through a note by line: Up and Down leave a block only from its first or last
  line and keep the column, Left and Right cross to the neighbouring block (also in right-to-left
  text), and Down in the last block no longer adds a paragraph. Shift and Ctrl selections stay
  inside the block (#82).
- Holding Backspace at the start of a paragraph keeps deleting through the paragraphs above
  without the cursor jumping between blocks, and undo puts the cursor back (#82).

## [2.2.1] - 2026-10-18

versionCode 20201.

### Fixed

- The app crashed whenever text was selected in a note (for example by double-tapping a word).
  Encly's sensitive-clipboard wrapper was not an `AndroidClipboard`, which the text selection
  toolbar of Compose 1.12 requires. Copies stay marked sensitive and still clear after a minute.
- The app could crash when the vault locked while the Tasks list or the drawer counts were
  being read. Such a read now keeps what is shown; the lock screen replaces it right after.

### Changed

- Google Play releases are published from CI on the release tag, together with the R8 mapping
  file, so Play Console shows readable crash reports. No Google credential is stored in the
  repository settings (Workload Identity Federation).

## [2.2.0] - 2026-10-15

versionCode 20200.

Notes can be imported from other apps, an optional duress PIN opens a separate decoy vault, and
the editor gets slash commands, smarter paste, find in note and templates. The database and the
backup format do not change.

### Added

- Import notes (Settings → Import): bring notes from Google Keep, Standard Notes, Joplin, Notesnook or
  Markdown/TXT files, fully offline. The format is recognised from the files, you review and tick the
  notes first, and a repeated import adds nothing. Photos and other attachments are left out and
  listed without counting toward the size limit, so large Takeouts and Markdown vaults import.
- Reading density for notes (Compact, Default, Comfortable) from the editor's More actions, with a
  persistent setting; Default is the existing layout and the stored note never changes.
- Slash commands: type `/` in an empty paragraph to pick a block kind (text, headings, checklist,
  lists, quote, link, divider) from a small filtered list. Not available in Simple Edit.
- Smarter paste: pasted text keeps its paragraphs (a blank line starts a new one, a single line
  break stays inside it) and Markdown headings, lists, checkboxes, quotes and rules become blocks.
  Pasted after existing text, the first line stays plain text. Nothing is rewritten or dropped,
  and one undo takes the whole paste back.
- Block actions: duplicate a block from the toolbar, the block sheets and TalkBack, next to move,
  delete and convert.
- Find in this note (More actions, or Ctrl/Cmd+F): highlights matches, shows "n of m" and jumps
  between them without changing the note. The query is never saved and is cleared when the vault locks.
- Start from template: an optional Meeting, Journal, Project notes or Checklist outline for a new,
  empty note. Blank stays the default.
- Optional 15- and 30-minute auto-lock delays, with a warning about longer background sessions.
- Duress PIN (Settings → Security): a third PIN that opens a separate, fully usable decoy vault
  with its own encryption key and database, from the same lock screen and with the same unlock.
  The real vault stays closed and unchanged, and nothing in the decoy vault leads back to it.
  Unlike the wipe PIN it erases nothing, and typing it between wrong guesses never lifts the
  limit on guesses at the real PIN. It does not hide the real vault from a forensic
  examination (see SECURITY.md).

### Changed

- Pasting several lines of plain text keeps paragraphs (a blank line starts a new block) instead
  of creating one block per line.
- The predictive back gesture plays the same sliding motion as the back button instead of
  shrinking the screen.
- Dependencies updated to their latest stable versions, Compose through its BOM; the app now
  compiles against SDK 37 (minimum and target SDK unchanged).

### ⚠️ Compatibility

- Going back to 2.1.0 keeps the vault and its backups readable: neither the database nor the
  backup format changes. The duress PIN only works in 2.2.0 and later, and changing the PIN in an
  older version turns it off (see SECURITY.md → Duress PIN).

## [2.1.0] - 2026-10-05

versionCode 20100.

Tasks get sub-tasks, an optional wipe PIN can silently erase the vault, and notes can move over
from My Notes in one step. The database and the backup format change with this version, so an
older Encly cannot open either (see Compatibility).

### Added

- Sub-tasks: a task can have a checklist of sub-tasks (one level). On the Tasks list an open
  task shows its next step (its first open sub-task) as one leaf under it, and every task with
  sub-tasks a segment bar of its progress; a task without sub-tasks looks as before. Ticking the
  next step saves it at once and the following one slides up into its place. A tap on the task
  (or the leaf's chevron) opens the whole tree, done ones struck through, ending with an add leaf
  ("First step" on a task without any) whose field adds each title on Done and stays open for
  the next. In the tree a sub-task ticks in place, a tap on its title renames it, clearing the
  title or its ✕ deletes it with Undo, and a long press drags it into a new place (Move up/down
  for TalkBack); Back closes the field, then folds the tree. The pencil opens the task sheet,
  which still adds, edits, ticks, reorders and deletes them. Completing a task never ticks its
  sub-tasks, and ticking the last open sub-task does not complete the task on its own: a
  snackbar offers "Complete task". Deleting a task deletes its sub-tasks, and Undo brings them
  back.
- Encrypted backups carry sub-tasks (backup schema 3). Older backups (schema 1 and 2) still
  restore, with no sub-tasks. Merge import adds the sub-tasks a task already on the device is
  missing.
- Wipe PIN (Settings → Security → Wipe PIN): an optional second PIN that, typed on the lock
  screen, erases the vault without a visible sign and opens Encly as an empty vault. Nothing in
  the app or its storage shows whether one is set. Off by default; nothing changes for vaults
  that never set one (a random decoy slot is added at startup without asking for the PIN).
  Exported backups are not touched. See SECURITY.md → Wipe PIN for what it does and does not
  protect against.
- Import from My Notes: My Notes can hand its notes (trash included), tasks, tags and task
  categories over to Encly on the same device. Encly accepts it only from the genuine My Notes
  app, after you unlock and confirm a preview; a repeated hand-off adds nothing twice. Tags and
  task categories are matched by name; attachments, images and pinning do not come over.

### ⚠️ Compatibility

- The database moves to version 4 (new `subtasks` table, migrated in place). Installing an older
  Encly over this version is not supported: Room refuses to open a newer database, so a
  downgrade fails to open the vault. Export a backup first if you might go back.
- A backup made with this version is schema 3. Older Encly versions refuse it as "made by a
  newer version, update the app" instead of restoring it without sub-tasks.

### Changed

- Changing the PIN keeps the PIN slot's salt.
- When Encly was restarted in the background while locked, unlocking opens the notes list
  instead of the note that was open.

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

[Unreleased]: https://github.com/pasichDev/Encly/compare/v2.3.0...HEAD
[2.3.0]: https://github.com/pasichDev/Encly/compare/v2.2.1...v2.3.0
[2.2.1]: https://github.com/pasichDev/Encly/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/pasichDev/Encly/releases/tag/v2.2.0
[2.1.0]: https://github.com/pasichDev/Encly/releases/tag/v2.1.0
[2.0.1]: https://github.com/pasichDev/Encly/releases/tag/v2.0.1
[2.0.0]: https://github.com/pasichDev/Encly/releases/tag/v2.0.0
