# Privacy Policy

_Effective: 2026-11-26 · Applies to Encly 2.0.0 and later on Android (package `com.pasich.encly`) and to Encly 3.1.0 and later on Linux (`.deb` and Flatpak `io.github.pasichdev.Encly`). The Windows and macOS builds are not published and are not covered._

Everything below describes the Android app unless a section says otherwise; what is true on Linux is in
[Encly for Linux](#encly-for-linux).

Encly is an offline notes and tasks app. This policy explains what happens to your data.
The short version: **everything stays on your device, encrypted, and nobody (including the
developer) receives any of it.**

## What Encly collects

Nothing. Encly has no account, no server, no analytics, no crash reporting, no advertising
and no tracking. The app does not request the Android `INTERNET` permission, so it cannot send
data over the network itself.

Encly asks every keyboard not to learn from what you type, and the optional "Strict keyboard
privacy" setting (Settings → Security) also asks it for no suggestions, no cloud prediction and no
access to the text around the cursor; a keyboard app still receives each key you type and may
ignore these requests, so use one you trust.

## What Encly stores on your device

- **Notes, tasks and tags** you create, stored in a database encrypted with SQLCipher
  (AES-256) under a random key that only your PIN, your biometric key or your recovery seed can
  unlock. See [SECURITY.md](SECURITY.md) for the full key hierarchy.
- **Security metadata** needed to unlock that database: encrypted key envelopes, a PBKDF2 salt,
  lockout counters and whether biometrics are enabled. Your PIN and seed words themselves are
  never stored.
- **App settings** such as theme, sort order and editor font.

This data is stored in Encly's private app storage. Android cloud backup and device-to-device
transfer are disabled for it, so it is not copied to Google Drive or another phone.

## Duress PIN and decoy vault (optional)

In **Settings → Security** you can set a **duress PIN**: a second PIN, typed on the lock screen
like any other. Instead of your real vault it opens a **decoy vault**, a separate vault that
starts empty and works like the real one, so you can show someone "the app" without showing your
notes. It erases nothing, and your real vault stays closed and unchanged while the decoy is open.
It is not meant to hide your real vault from someone who examines the phone's storage.

What it keeps on your device, in the same private app storage as everything else:

- **The decoy vault's notes, tags and tasks**, in a second database file of its own
  (`vault_b.db`), encrypted with SQLCipher under its own random key. The file is created the
  first time the duress PIN is used and kept between locks until you remove it.
- **Two entries of security metadata:** the encrypted key envelope that the duress PIN opens, and
  the decoy vault's key sealed under your real vault's key (so changing the duress PIN keeps the
  decoy notes). Both exist, with the same size, whether or not you set a duress PIN, so the
  metadata does not show whether you did. The duress PIN itself is never stored.
- **The same app settings** (theme, sort order, editor font, auto-lock delay, keyboard privacy)
  are used in both vaults.

Inside the decoy vault, what belongs to your real vault does not work: changing the PINs,
fingerprint unlock, the recovery phrase, encrypted backups and "Erase all data". Fingerprint
unlock and the recovery phrase on the lock screen always open the real vault. An encrypted backup
never contains decoy notes. Markdown export and notes import do work there, on the decoy vault's
own notes, and the files they make are yours to keep or delete (see the export sections below).

Turning the duress PIN off (Settings → Security, after your PIN and a confirmation) deletes the
decoy vault and its notes. The wipe PIN and **Erase all data** delete the decoy vault together with
the real one. See [SECURITY.md → Duress PIN](SECURITY.md#duress-pin) for the technical details and
what it does not hide.

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Optional fingerprint / face unlock of your vault. Biometric data never leaves the Android system; Encly only receives "authenticated" or "not authenticated". |
| `USE_FINGERPRINT` | Added by Android's biometric library for fingerprint unlock on Android 8.x, which predates `USE_BIOMETRIC`. Same purpose, same limits. |
| `HIDE_OVERLAY_WINDOWS` | Lets Encly stop other apps from drawing over its screens (Android 12 and later), so an overlay cannot cover or imitate the PIN pad. It gives Encly no access to any data. |

Encly requests no internet, storage, contacts, location, camera, microphone or notification
permission.

## Links that leave the app

A few items open a page outside Encly, **only when you tap them**: on the About screen, this
privacy policy, the GitHub issue tracker, an email to the developer, and, in the Google Play
build only, the Play Store listing; on the open-source licenses screen, each library's or font's
license; in the F-Droid build only (also offered as the `fdroid` APK on GitHub Releases), the
developer's Ko-fi donation page (Support screen); and a link block you added to a note, which
opens in your browser. These are handled by your browser, email or store app under their own
privacy policies. Encly sends them nothing beyond the fact that the link was opened (for a link
block, the address you wrote).

## Sharing

Encly does not share, sell or transfer any data to anyone. It has no share sheet and no share flow
for note content. It puts something on the clipboard only when you copy it yourself: text you select, or a
link from a note's link block. Every copy is marked as sensitive (on Android 13 and later it is
hidden from the clipboard preview) and cleared after about a minute. Apart from that, data
leaves the app only in files that you export yourself: an encrypted backup (below) or, if you
choose, a readable Markdown export (below).

## Encrypted backup file

You can export a backup in **Settings → Backup**. Nothing is exported automatically.

- **What it contains:** all your notes (title, content, trash, pin, archive and colour state,
  created and edited dates),
  tags (name, visibility, order) and tasks (title, description, completion, dates, priority,
  order), plus your display and behaviour preferences (theme, fonts, language, list layout,
  auto-lock delay and similar). It does **not** contain your PIN, your recovery phrase, biometric
  data, or any encryption key of the app itself.
- **How it is protected:** the whole file is encrypted with AES-256-GCM under a key derived from
  your 12-word recovery phrase. There is no separate backup password; without the 12 words the
  file cannot be read or changed unnoticed, not even by the developer. If your vault has no
  recovery phrase yet, Encly creates one before the first export.
- **Where it goes:** Encly writes it only to the location you pick in Android's save dialog and
  needs no storage permission for that. Where the file goes afterwards (a folder, a USB stick,
  a cloud drive, e-mail) is your choice and is then governed by that service's own policy. Encly
  never uploads it anywhere and keeps no copy.
- **Restoring:** "Restore from backup" (first-run setup) or Settings → Backup reads a file you
  pick and asks for the 12 words; the file is decrypted on the device only.

Anyone who has both the file and your 12 words can read that backup, and an exported file cannot
be revoked, so keep the words and the file apart. See
[SECURITY.md → Encrypted backups](SECURITY.md#encrypted-backups) for the technical details.

## Markdown export (not encrypted)

You can also export your notes as plain, readable Markdown: from a note's menu (**Export as
Markdown**, one `.md` file) or in **Settings → Backup** (**Export as Markdown**, one `.zip`).
Nothing is exported automatically.

- **What it contains:** one `.md` file per note (title, tag, dates, pin, archive and colour in a
  small header, then the text, checklists, links and so on), archived notes in an `archive`
  folder, and `tasks.md` with your tasks and sub-tasks. Notes in the trash are left out. It
  does **not** contain your PIN, recovery phrase, keys or app settings.
- **It is not encrypted.** Anyone who gets the file can read it, and wherever you save it (a
  folder, a USB stick, a cloud drive) holds a readable copy that Encly cannot protect or take
  back. Encly says so before every export and asks for your PIN (or biometrics) again.
- **Where it goes:** only to the document you create in Android's save dialog. Encly shows no
  share sheet, writes no temporary copy, and never uploads it.

## Import from My Notes

If you also use My Notes (the developer's earlier notes app), it can move its notes, tasks, tags
and task categories into Encly. This happens **only on your device**, only when you start it in
My Notes, and only after you unlock Encly and confirm what will be imported. Encly accepts the
data only from the genuine My Notes app (checked by its signing certificate). The temporary copy
Encly makes while reading it is deleted right away (or, if Encly is closed in the middle, the next
time it starts); the imported notes are stored encrypted like everything else. Nothing is sent over the network, and Encly sends nothing back to My Notes
except how many items were imported. Attachments and images stay in My Notes.

## Import notes from other apps

**Settings → Import notes** reads export files of Google Keep (Takeout), Standard Notes
(decrypted backup), Joplin (JEX or RAW), Notesnook (Markdown or plain-text export) and plain
Markdown/text notes such as an Obsidian vault. You choose the files or folder in the system file
picker; Encly reads only what you picked, only while the app is unlocked, and only on this
phone. Nothing is uploaded, no account is needed, and no network is used. The files are read
into memory, never copied to disk, and the files themselves are left unchanged. You see the list
of notes before anything is saved, pick which ones to import, and the selected notes are stored
encrypted in the vault that is open. Encrypted exports of those apps are not read; export them
in a readable format first.

## Deleting your data

On Linux, removing the package (`apt remove`) or uninstalling the Flatpak does **not** delete your data: neither runs
any clean-up of the data folder or of the keyring item, and `flatpak uninstall` keeps `~/.var/app/io.github.pasichdev.Encly`
unless you add `--delete-data`. To delete it, use **Settings → Security → Erase all data** before you uninstall (it
removes the databases and the key slots, and asks your keyring to delete its secret; if the keyring is locked or not
running, the erase still succeeds and the item stays, without a message), or afterwards delete the folder named above
and, in your keyring program (Passwords and Keys, KWalletManager, KeePassXC), the item whose attribute `application` is
`com.pasich.encly`. An encrypted backup that you exported
stays where you saved it.

On Android, uninstalling Encly, or clearing its storage in Android settings, permanently deletes all of
it, and so does **Settings → Security → Erase all data** (the decoy vault too, if you have one).
There is no copy anywhere else, apart from encrypted backups you exported yourself. If you did not
keep a recovery seed, a forgotten PIN also means the data cannot be recovered.

## Encly for Linux

Encly 3.1.0 is also released for Linux, as a `.deb` and a Flatpak bundle from
[GitHub Releases](https://github.com/pasichDev/Encly/releases/latest). The promises above hold there too: it
collects nothing, and nobody (including the developer) receives any of your data. What differs from Android:

- **No network, no accounts, no analytics.** There is no account, server, analytics, telemetry, crash reporting,
  advertising, tracking or update check. The Flatpak is built without network access (no `--share=network`) and without
  file-system access; the `.deb` is not sandboxed, but the app opens no network connection either. The only things that
  leave the app are links that you tap (to your default browser or mail program, `https`, `http` and `mailto` only), and
  the files you export yourself.
- **Where your data lives.** In one folder that only your user can enter: `~/.local/share/encly` (or
  `$XDG_DATA_HOME/encly`) for the `.deb`, `~/.var/app/io.github.pasichdev.Encly/data/encly` for the Flatpak, which is
  a separate vault. In it: the encrypted database files (SQLCipher, AES-256), `vault_state_v3.bin` with the encrypted key
  envelopes, the salt, the lockout counters and, if you set a duress passphrase, the second database for the decoy vault
  (`vault_b.db`), a small settings file (onboarding done, auto-lock delay, language, window size and place), your
  appearance and editor settings, a cache folder, and two local files that stop a second Encly from opening the same vault
  (`.instance.lock` and `.instance.sock`; the socket is for your user only and never networked). Your passphrase and
  your recovery words are never stored.
- **A secret in your system keyring.** The device half of your vault key, 32 random bytes, is kept in the Secret
  Service of your desktop (GNOME Keyring, KWallet or KeePassXC), as an item with the attributes
  `application = com.pasich.encly`, `purpose = pin-factor-v1` and `vault` (the path of the data folder, so each vault
  has its own). It is not in Encly's folder. It crosses the session bus
  encrypted, and it never leaves your computer. A copy of the vault files alone cannot be opened without it, and a vault moved to another path, or copied
  between the `.deb` and the Flatpak, finds no item: only its recovery phrase opens it.
- **What Encly talks to on D-Bus.** Everything below happens on your own machine, over the local session or system bus,
  and none of it reaches the network: the Secret Service (to store and read that secret); the screen saver and
  `systemd-logind` signals (to lock the vault when your session locks or the computer sleeps); and the desktop portal's
  file chooser (`xdg-desktop-portal`, to open and save the backup, import and export files that you pick).
- **Clipboard.** Encly puts something on the clipboard only when you copy it yourself. It clears it after about a minute
  if it is still Encly's text (it leaves alone anything you copied since), when the vault locks and when Encly quits. On
  Linux the copy is marked with the `x-kde-passwordManagerHint` target, which Klipper and CopyQ honour; other clipboard
  managers may keep a copy that Encly cannot reach.
- **Exports and printing.** Backups, Markdown and PDF files go only where you choose, and are created readable by your user only when the file does not exist yet (a file that the save dialog or the portal already created keeps its permissions).
  A Markdown or PDF file is not encrypted, and printing hands the note's text to the system's print spooler, which may
  keep temporary copies; Encly warns you before each of these and asks for your passphrase again.
- **The same as on Android:** the duress passphrase and the decoy vault (see "Duress PIN and decoy vault", with a
  passphrase for the PIN), the encrypted backup file and its recovery phrase, the Markdown export, and the notes import
  from other apps, which reads only the files you pick.

## Children

Encly collects no data from anyone, including children.

## Changes

Changes to this policy are published at
<https://pasichdev.xyz/apps/encly/privacy-policy/>, mirrored in `PRIVACY.md` in the
[Encly repository](https://github.com/pasichDev/Encly), and noted in
[CHANGELOG.md](CHANGELOG.md).

## Contact

Questions: **pasichdev@outlook.com** or a
[GitHub issue](https://github.com/pasichDev/Encly/issues).
