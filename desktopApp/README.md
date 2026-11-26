# :desktopApp

Encly for Windows, macOS and Linux: Compose Multiplatform on the JVM around `:shared`. Linux is released (a `.deb` and a
Flatpak bundle on GitHub Releases); Windows and macOS are in the repository, not published. It has the same
vault format, recovery slot and encrypted database as the Android app, but it is unlocked with a passphrase instead of
a PIN (see "Using the Linux package"). It is the counterpart of `:app`: its own dependency graph, its own
implementations of the platform interfaces, and a window.

```
./gradlew :desktopApp:run                  # run it
./gradlew :desktopApp:desktopTest          # tests (headless)
./gradlew :desktopApp:packageDeb           # Linux .deb      (on Linux)
./gradlew :desktopApp:packageMsi           # Windows .msi    (on Windows, needs WiX 3)
./gradlew :desktopApp:packageDmg           # macOS .dmg      (on macOS)
```

The version is the one in `version.properties`, shared with the Android app. A package can only be built on its own
OS (`jpackage` does not cross-build); CI builds all three, and a Linux Flatpak, in the `desktop-package` job: signed when
the signing secrets exist, unsigned otherwise (see "Signing the desktop installers").

## Using the Linux package

The details and the limits are in [SECURITY.md](../SECURITY.md#desktop); this is what you need to know to use it.

- **A passphrase, not a PIN.** It is 12 to 128 characters, not only digits, with at least 5 different characters, in any
  language. Wherever a new passphrase is chosen, *Suggest a passphrase* is offered: five random words from the recovery-phrase word list (55 bits), shown in
  clear so you can write them down or change them. They are not your recovery phrase. The lock screen is one field and
  *Unlock*; it never shows or stores how long your passphrase is. The wipe passphrase and the duress passphrase follow
  the same rule, and keep working after you change the passphrase unless the stored slot had a different format or costs
  (then both are turned off and the app says so). There is no biometric unlock.
- **A Secret Service must be running** (GNOME Keyring, KWallet or KeePassXC). It keeps the device secret that is mixed
  into your passphrase, so a copy of the vault files alone is useless. Encly checks it at the start of onboarding and
  asks it to unlock its default keyring, or to create one, through the service's own dialog (waited for at most
  120 seconds, and closed when the vault locks). There is no fallback to a file: without a Secret Service, or with a
  keyring that stays locked, Encly cannot unlock, and says what is missing (for example "Unlock your system keyring and
  try again") without counting the attempt.
- **An X11 display is required** (XWayland on a Wayland session). On a Wayland session without XWayland Encly writes an
  error to stderr, sends a desktop notification (the portal's, then `notify-send`) and exits. Links open through the
  OpenURI portal, then `xdg-open`; AWT's browser call is used only outside a sandbox.
- **If the keyring is reset or replaced** (a new keyring, a new OS account, the home directory restored without it, the
  vault copied to another computer). The secret is also tied to the data folder, so a vault moved to another path, a
  changed `XDG_DATA_HOME`, or a `.deb` vault copied into the Flatpak's folder (or the reverse) finds no secret either. In
  all of these the passphrase no longer unlocks. On Linux the lock screen says the key was not found, which does not by
  itself mean it is gone for good: try again (for example after unlocking, or switching back to the keyring that holds
  it), or use your **recovery phrase** on the lock screen, then set a new passphrase; a wipe, held to confirm, is the
  last resort. Without a recovery phrase and without the keyring that holds the secret there is no way back, so keep
  the phrase that onboarding offers.
- **The strength is the passphrase's.** Argon2id makes each guess about nine times dearer than the PBKDF2 of the phone
  PIN, and does not make a short passphrase safe: use a long one.
- Unlocking takes a moment and about 64 MiB of memory; if the computer cannot give it, Encly reports a failure of the key
  storage, counts nothing, and a retry may work.
- Backups use the recovery phrase and nothing else, so a backup from a phone restores here and the reverse.

## Where the data is

One private directory per OS user. It is created `rwx------` where POSIX permissions exist.

| OS | Directory |
|---|---|
| Windows | `%LOCALAPPDATA%\pasichdev\Encly` (local, never the roaming profile, and not the install folder `%LOCALAPPDATA%\Programs\Encly`) |
| macOS | `~/Library/Application Support/Encly` |
| Linux | `$XDG_DATA_HOME/encly`, else `~/.local/share/encly` |

| File | What it is |
|---|---|
| `vault_state_v3.bin` | key slots and the lockout (every secret in it is already an AES-GCM envelope) |
| `*.db` | the encrypted vault databases (SQLCipher) |
| `encly_flags_v3.properties` | non-secret flags: onboarding done, auto-lock delay, language, window size and place |
| `my_notes_settings.preferences_pb` | appearance and editor settings |
| `cache/` | the cache; the My Notes hand-off staging in it is swept at every start |
| `.instance.lock` | keeps a second Encly of the same user from opening the same vault |

The flags are a small file written atomically (temp file, fsync, rename), not `java.util.prefs`, which would put them
in the registry (Windows) or a plist outside this directory (macOS).

The device-bound half of the vault key is not in this directory: it lives in the OS secret store (Windows DPAPI,
the macOS login Keychain, the freedesktop Secret Service). See `SECURITY.md`, "Desktop". On Windows the blob of
DPAPI sits in `%LOCALAPPDATA%\pasichdev\Encly` too: the roaming `%APPDATA%` is copied to profile servers and to every machine
the user signs in to, which would take the vault and its device secret off this device. Windows has no POSIX
modes; the folder keeps the ACL it inherits from `%LOCALAPPDATA%` (the user, SYSTEM and Administrators), which is the
same boundary DPAPI gives the secret. Encly does not rewrite ACLs.

## Security parity with Android

| Android | Desktop | Not available |
|---|---|---|
| a PIN of 6 to 12 digits, PBKDF2-HMAC-SHA256 (600,000), slot version 3; the device half in the Keystore | a passphrase of 12 to 128 characters, Argon2id (64 MiB, 3 passes), slot version 4; the device half in the OS secret store, bound to the user account (`SECURITY.md`, "Desktop") | StrongBox or TEE binding of the device half; the PIN keypad |
| `FLAG_SECURE` from the first frame | the window is excluded from screenshots and recordings from the first frame and again whenever the theme asserts it: Windows `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` (Windows 10 2004+; older: `WDA_MONITOR`, a black window), macOS `NSWindow.sharingType = none` | Linux: X11 and Wayland have no such call. macOS: best effort, newer ScreenCaptureKit tools can ignore the flag on macOS 15 and later |
| lock when the app goes to the background, after the auto-lock delay | the same, when the window loses the focus or is minimised (its own file dialogs do not count) | |
| lock at once when the screen turns off | lock at once when the OS locks the session: macOS (the window server's session state, polled every 1.5 s), Windows (the input desktop, polled), Linux (logind `Lock` and `PrepareForSleep`, and the screen-saver `ActiveChanged` of GNOME, KDE, Cinnamon, MATE over D-Bus; every report of a lock locks the vault, also after a locker that never reports its end, such as swaylock or i3lock through xss-lock). A sleep without a session lock locks the vault too: on macOS and Windows it is seen when the machine wakes (the clock that counts sleep runs ahead of the one that does not), at the next 1.5 s poll | |
| (none) | idle lock: no keyboard or mouse input for the auto-lock delay, but at least 5 minutes, locks a window that stays in front; the time is measured on the OS uptime, so time asleep counts | |
| clipboard marked sensitive, cleared after a minute | cleared after a minute unless something else was copied since, also for what the editor copies, and on quit. Windows: the text is written with `ExcludeClipboardContentFromMonitorProcessing`, `CanIncludeInClipboardHistory` = 0 and `CanUploadToCloudClipboard` = 0 (no Win+V history, no cloud sync); macOS: with `org.nspasteboard.ConcealedType` and `TransientType` (clipboard managers skip it) | Linux: the text is offered with the `x-kde-passwordManagerHint` target, which Klipper and CopyQ honour (not verified on a real KDE session); a manager that ignores the hint keeps a copy that the wipe cannot reach |
| strict keyboard privacy | not offered (a desktop has no on-screen keyboard that learns) | |
| PIN lockout on `elapsedRealtime` and boot count | the OS uptime (including sleep) and the boot id (Linux `boot_id`, macOS `kern.bootsessionuuid`), so restarting the app does not shorten a lockout; Windows has no boot id, the uptime comparison covers it | |
| biometrics | none yet | Windows Hello, Touch ID and fprintd are not wired in |

A second Encly of the same user exits at start (a file lock in the data directory). Before it exits it asks the first
one, over a Unix-domain socket in the same directory (`.instance.sock`, owner only, no network), to come to the front
and to take the files it was given; only paths travel, and the first checks them like its own arguments. Where no
socket can be made (a path too long for one) or the first one does not answer, the second shows a plain message
(not an error) and a file it was given is to be dropped on the open window.

## The window

- minimum size 720 x 520; size, place and maximised state are remembered and clamped to the connected screens, for each window slot (the first window, the second, ...) on its own;
- menu bar (the native one on macOS): File (new note, new window, export as PDF, print, import, backup, lock now,
  settings, exit), View (theme, language), Window, Help (about, licences, FAQ). The shortcuts use Cmd on macOS and
  Ctrl elsewhere; new window, print and PDF (Cmd/Ctrl+Shift+N, +P, +Shift+P) are menu chords like import and backup. There is no Edit
  menu on purpose: the text fields handle cut, copy, paste and undo themselves, and a menu item with the same
  shortcut would take the key from them on macOS. The menu titles "File", "View", "Window", "Help", "Exit" and
  "Minimize" are English for now; the items use the app's translated strings;
- menu items ask the UI to open a screen through `NavigationRequests` (`:shared`, `platform`), which `EnclyApp`
  collects. They are ignored while the vault is locked, so no item skips the lock screen;
- several windows ("New window", and "Open in new window" on a note's right-click menu): all show the one vault
  behind the one `SessionLockManager`, so a lock (idle, Lock now, the OS) locks every window and an unlock on any lock
  screen opens the others (`LockScreen` follows it, switched on by `LocalSessionSharedAcrossWindows`). Each window has
  its own `EnclyApp`, `AppCommandRouter`, navigation, `NavigationRequests`, drop router and file dialogs
  (`WindowServices`); the graph, the lock, the idle timer and the OS hooks (About, Preferences, open file) are the
  process's. Each window has its own menu bar and its own commands, so on a Mac, where one menu bar shows the active
  window's menu, an item acts on the window in front. The app counts as in front while any window is active and not
  every one is minimised, and focus moving between its own windows is not leaving (`WindowForeground`), so it does not
  start the auto-lock. Closing a window saves its editors; closing the last one (or Quit) waits for the last saves,
  locks and closes the database, and ends the app. A further window is refused until the vault exists (onboarding);
- a note has one editor at a time across the windows (`NoteEditRegistry`): the editor saves the whole note, so a second
  editor would overwrite the first one's work. The window that opens a note another window is editing shows it
  read-only, with a notice, and writes nothing from it;
- export as PDF and print (File menu, the note's right-click menu and the editor's More sheet) are plaintext exports:
  they go through the Markdown export's warning and passphrase (the print warning also names the system print spooler, which
  may keep temporary copies), then the save dialog or the system print dialog. The PDF is written with Apache PDFBox,
  built in memory with the app's Source Sans 3 (Latin and Cyrillic: all nine languages) embedded, and written to the
  file the user picked and nowhere else; it is not touched on the way by the network or a temp file;
- files dropped on the window, or a backup opened from the file manager, go to the same flows as the file dialogs:
  a `.enclybak` answers the restore or import-backup picker, notes (`.md`, `.zip`, `.txt`, `.json`, `.jex`, `.html`)
  and folders answer the import screen's pickers; with none open, the matching screen is opened first. A path the
  app cannot read (in the Flatpak sandbox a drop is the host's path, which the sandbox does not have) is not routed:
  a message points to Import and Backup in the File menu, whose dialogs go through the portal;
- file dialogs are the OS's: AWT's native dialogs on macOS and Windows, the freedesktop portal on Linux (the desktop's
  own dialog, with the AWT dialog as fallback);
- the theme follows the OS light and dark mode on macOS and Windows; on Linux the System choice follows what the
  runtime reports, which is light on many desktops (GNOME and KDE settings are not read yet), so pick Light or Dark
  there; the native title bar and, on Windows, the menu strip keep the OS's own look; a language change recomposes the app (it returns to the home screen);
- the app icon is the brand mark.

## Packaging

`compose.desktop.application.nativeDistributions` in `build.gradle.kts`: MSI, DMG and DEB, package name `Encly`,
bundle id `com.pasich.encly`, a fixed Windows upgrade UUID, per-user MSI install, and the `.enclybak` file
association with its own type `application/vnd.encly.backup` (never `application/octet-stream`).

### Licences in the installers

The licence and notice texts of what the installer bundles are files, not only the in-app screen (About, Licenses):

| What | Where it comes from | In the `.deb` | In the Flatpak |
|---|---|---|---|
| PDFBox, FontBox, ICU4J, Noto Sans Arabic, Commons Logging, JNA, dbus-java, SLF4J, Skiko, Skia, Bouncy Castle (Argon2id), and `THIRD-PARTY-NOTICES.txt` (what is what) | `packaging/resources/common/licenses/`, which Compose puts in the app's resources | `licenses/` in the app's resources, under `/opt/encly/` (jpackage layout) | `/app/share/licenses/io.github.pasichdev.Encly/` |
| the Java runtime (OpenJDK: Eclipse Temurin in the CI builds) | the `legal/` folder of the runtime that jlink cuts, taken from the built image, not copied into the repository | `lib/runtime/legal/` under `/opt/encly/` | `/app/share/licenses/io.github.pasichdev.Encly/openjdk/` |
| Encly's own licence | `LICENSE`, given to jpackage as the licence file | where jpackage puts it | `.../licenses/io.github.pasichdev.Encly/LICENSE` |

The texts are taken from the libraries' own jars (PDFBox, Commons Logging, SLF4J), from the Skiko and Skia sources at the
tags the release uses (Skiko `v0.150.2`, Skia `m150-a3f509bf52`; the Skiko jars carry no licence file), and from the
runtime itself. When `libs.versions.toml` bumps one of them, refresh its file and the version in
`THIRD-PARTY-NOTICES.txt`. `flatpak/build.sh` fails when the notices or the runtime's `legal/java.base/LICENSE` are not
in the image, and the `linux verify` job of the release workflow checks that they are in both installed packages.
Not included: the licences of the code Skia is built with (FreeType, HarfBuzz, ICU, expat, zlib, libpng ...), which the
Skiko artifacts do not ship, and ICU4J 79.1 and dbus-java 5.2.2 have no licence file in their jars (the texts kept here are
from the previous versions).

### Signing the desktop installers

The `desktop-package` job (`.github/workflows/android.yml`) signs the installers when the repository has the
secrets below, and builds them unsigned when it has none. No certificate, password or identity is in the repository;
the secrets are read from GitHub Actions secrets only in the steps that need them.

| Secret | Value |
|---|---|
| `ENCLY_MACOS_CERT_P12_BASE64` | the "Developer ID Application" certificate with its private key, exported as `.p12` and base64-encoded (`base64 -i cert.p12`) |
| `ENCLY_MACOS_CERT_PASSWORD` | the password of that `.p12` |
| `ENCLY_MACOS_SIGN_IDENTITY` | the identity's full name, as `security find-identity -v -p codesigning` prints it: `Developer ID Application: Name (TEAMID)` |
| `ENCLY_MACOS_NOTARY_APPLE_ID` | the Apple ID that submits to the notary service |
| `ENCLY_MACOS_NOTARY_PASSWORD` | an app-specific password of that Apple ID (appleid.apple.com), not the account password |
| `ENCLY_MACOS_NOTARY_TEAM_ID` | the 10-character Apple team ID |
| `ENCLY_WINDOWS_CERT_PFX_BASE64` | a code-signing certificate with its private key as `.pfx`, base64-encoded |
| `ENCLY_WINDOWS_CERT_PASSWORD` | the password of that `.pfx` |

Optional repository variable (not a secret): `ENCLY_WINDOWS_TIMESTAMP_URL`, an RFC 3161 timestamp server;
the default is `http://timestamp.digicert.com`.

How a run decides:

- **Per OS, and per group.** macOS has two groups: the certificate (`ENCLY_MACOS_CERT_*`, `ENCLY_MACOS_SIGN_IDENTITY`)
  and the notarization (`ENCLY_MACOS_NOTARY_*`). No secrets of an OS: that installer is built unsigned, with a notice.
  Some secrets of a group set and others not, or notarization without the certificate: the job fails. A half-signed
  release is never built silently.
- **Pull requests never sign**, so the code of a pull request does not run next to a certificate. Pushes to `main`
  and manual runs (`workflow_dispatch`) do.
- **Artifact names.** A signed installer is uploaded as `encly-desktop-<os>`, an unsigned one as
  `encly-desktop-<os>-unsigned`. The Linux packages (`.deb`, Flatpak) are never code-signed here and always say
  `-unsigned`.

macOS. The certificate is imported into a temporary keychain with a random, masked password, which the job deletes
at its end (also when it fails). Gradle (`nativeDistributions.macOS` in `build.gradle.kts`, switched on by
`ENCLY_MACOS_SIGN_IDENTITY` and `ENCLY_MACOS_KEYCHAIN`) signs the app image with the hardened runtime and the
entitlements of `packaging/macos/entitlements.plist` (`allow-jit`, `allow-unsigned-executable-memory` and
`disable-library-validation`: the JVM generates code, and loads native libraries it extracts at run time). Then
`notarizeDmg` submits the DMG with `notarytool`, waits for the verdict, and staples the ticket. The job then mounts
the DMG and checks the signature with `codesign --verify`, and the stapled ticket and Gatekeeper's verdict
(`stapler validate`, `spctl`) when it was notarized.

Windows. `signtool` (from the Windows SDK on the runner) signs the MSI with the PFX, SHA-256 and a timestamp, and
`signtool verify /pa` checks it. Only the MSI is signed, not the executables inside it. To sign with Azure Trusted
Signing instead of a PFX, replace the "Sign the MSI (Windows)" step with the
Azure sign action and an OIDC login; the rest of the job does not change.

To sign a build on your own Mac, set the same variables for Gradle:
`ENCLY_MACOS_SIGN_IDENTITY` (and `ENCLY_MACOS_KEYCHAIN` for a keychain other than the login one), and, for
notarization, `ENCLY_MACOS_NOTARY_APPLE_ID`, `ENCLY_MACOS_NOTARY_PASSWORD`, `ENCLY_MACOS_NOTARY_TEAM_ID`; then run
`./gradlew :desktopApp:packageDmg :desktopApp:notarizeDmg`.

### Flatpak (Linux)

`packaging/flatpak/` holds the manifest `io.github.pasichdev.Encly.yml`, the desktop entry, the AppStream metainfo,
the MIME type of `.enclybak` and `build.sh`, which stages the jpackage app image
(`:desktopApp:createDistributable`: the same launcher, JVM arguments and bundled Java runtime as the `.deb`), checks
the metainfo with `appstreamcli validate` and the desktop entry with `desktop-file-validate`, runs `flatpak-builder`
against the freedesktop `Platform`/`Sdk` 25.08 and writes `build/flatpak/encly-<version>-<arch>.flatpak`. CI builds
it on Ubuntu and uploads it as `encly-desktop-flatpak-unsigned`. Locally (on Linux with `flatpak` and
`flatpak-builder`): `desktopApp/packaging/flatpak/build.sh`, then
`flatpak install --user build/flatpak/encly-*.flatpak` and `flatpak run io.github.pasichdev.Encly`.

The sandbox permissions are the minimum the app needs, and there is no network:

| Permission | Why |
|---|---|
| (none for network) | the app makes no network connection |
| (none for files) | files are chosen with the FileChooser portal and arrive through the document portal; there is no `--filesystem` |
| `--socket=x11`, `--share=ipc` | the bundled Java runtime draws through X11 only (XWayland on a Wayland session) |
| `--device=dri` | OpenGL rendering of Compose (Skiko) |
| `--socket=cups` | printing a note: the runtime's libcups reaches the host's print service through it; no network is shared |
| `--talk-name=org.freedesktop.secrets` | the device-bound half of the vault key lives in the Secret Service |
| `--talk-name=org.freedesktop.ScreenSaver`, `org.gnome.ScreenSaver`, `org.cinnamon.ScreenSaver`, `org.mate.ScreenSaver` | lock when the desktop's screen saver locks the session |
| `--system-talk-name=org.freedesktop.login1` | lock on logind's `Lock` and on suspend |

The Flatpak keeps its data in `~/.var/app/io.github.pasichdev.Encly/data/encly`, not in `~/.local/share/encly`: a vault
of a `.deb` or tarball install is a different vault there. The bundle is not signed and is not on Flathub; a Flathub
submission needs a build from source (Gradle offline with pinned dependencies) and screenshots in the metainfo.

### The .deb's postinst

The `.deb` has a file association (`.enclybak`), so jpackage puts `xdg-desktop-menu install ...` into the package's
postinst. That command fails with "No writable system menu directory found" on a system that has no
`/usr/share/desktop-directories`, for example a bare window manager with no desktop installed, and the install then
stops half-configured. Compose Desktop has no supported way to change jpackage's maintainer scripts, so
`packaging/deb/harden-postinst.sh` patches the finished package: one line, added right after the `    configure)` line of
the postinst (it must be there exactly once, or the script fails and nothing ships), creates that directory (mode 0755)
when it is missing. It does nothing on uninstall (the directory may be in use by other packages). The script unpacks the
`.deb` with `dpkg-deb -R`, repacks it with `dpkg-deb -b` under `fakeroot` with the compression it had, checks that the file
list and every other control file are unchanged, prints a diff of the postinst, and can be run twice. CI runs it right
after `packageDeb` in `release.yml` and `android.yml`, before the file is uploaded and verified. A `.deb` from a local
`./gradlew :desktopApp:packageDeb` is not patched unless you run
`desktopApp/packaging/deb/harden-postinst.sh --in-place <file.deb>` on it (needs `dpkg-deb` and `fakeroot`).

### Releasing the Linux packages

A `v*` tag builds the `.deb` and the Flatpak bundle (`release.yml`, jobs `linux-deb`, `linux-flatpak` and `linux-verify`:
clean machines install the `.deb` (every Ubuntu LTS runner image, Debian 12 and 13 in containers) and the bundle and check
the files, the metadata, the sandbox permissions and the licence files; the window is not opened; the same checks run on
every pull request, `.github/workflows/_verify-linux.yml`) and attaches them to the GitHub Release of the tag next to the APKs. They are in the one `SHA256SUMS` (and
`SHA256SUMS.asc`, which exists only when the GPG secrets are configured) and each package file has a build-provenance attestation (`gh attestation verify <file> --repo pasichDev/Encly`).
They are not code-signed. The MSI and the DMG are not attached to a release (they are unsigned).

The `.deb` is built on Ubuntu 22.04, on purpose: jpackage writes the package's `Depends:` from the machine it runs on, and a
build on Ubuntu 24.04 or later names `libasound2t64`, `libpng16-16t64` and the like, which Ubuntu 22.04 and Debian 12 do not
have. The pre-t64 names that a 22.04 build gives are also satisfied by the newer systems (their t64 packages `Provide` them,
checked in the Ubuntu noble and Debian trixie package indexes). The verify script fails a `.deb` whose `Depends:` has a t64
name. Expected to install on: Ubuntu 22.04 LTS and later, Debian 12 and later; older systems are not tested.

If the Linux build or its check fails, the Android files are published without them. To add the Linux files to that Release
afterwards (or to replace them), run the **Release** workflow by hand (Actions, Release, Run workflow) with the tag, for
example `v3.1.0`: it builds and checks only the Linux packages and adds them to the existing Release, and it never builds
Android and never publishes to Google Play.

## No network

Like the Android app, the desktop app makes no network connection and has no update check; updates come from the
installer, a store or a package manager. Nothing in the module opens a network socket: the only IPC is D-Bus over a local Unix
socket on Linux (the Secret Service, logind, the file chooser portal) and the owner-only Unix-domain socket `.instance.sock`
of the single-instance check, and the OS calls are JNA calls into system
libraries. Opening a link hands it to the default browser (only `https`, `http` and `mailto`).

## Tests and coverage

`desktopTest` runs headless: the graph builds with a temporary directory and fakes in place of the OS, a Compose UI test
reaches the onboarding screen, and the platform services are tested with fakes (clipboard wipe timing on a manual
timer, atomic key-value writes, window state, drop routing, lifecycle and idle lock). Kover gates line coverage at 85 %.
Excluded: `Main` (the entry point), `nativeos` (JNA and D-Bus glue that needs the real OS) and `window.WindowHost` (needs
a real AWT window).
