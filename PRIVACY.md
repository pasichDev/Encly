# Privacy Policy

_Effective: 2026-09-25 · Applies to Encly 2.0.0 and later (Android, package `com.pasich.encly`)._

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

Encly does not share, sell or transfer any data to anyone. It has no share flow for note
content. It puts something on the clipboard only when you copy it yourself: text you select, or a
link from a note's link block. Every copy is marked as sensitive (on Android 13 and later it is
hidden from the clipboard preview) and cleared after about a minute. Apart from that, the only way
data leaves the app is an encrypted backup file that you export yourself (below).

## Encrypted backup file

You can export a backup in **Settings → Backup**. Nothing is exported automatically.

- **What it contains:** all your notes (title, content, trash state, created and edited dates),
  tags (name, visibility, order) and tasks (title, description, completion, dates, priority,
  order). It does **not** contain your PIN, your recovery phrase, biometric data, any encryption
  key of the app itself, or app settings.
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

## Deleting your data

Uninstalling Encly, or clearing its storage in Android settings, permanently deletes all of
it, and so does **Settings → Security → Erase all data**. There is no copy anywhere else, apart
from encrypted backups you exported yourself. If you did not keep a recovery seed, a forgotten
PIN also means the data cannot be recovered.

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
