# :desktopApp

Encly for Windows, macOS and Linux: Compose Multiplatform on the JVM around `:shared`, with the same security
model as the Android app (same vault format, same PIN and recovery slots, same encrypted database). It is the
counterpart of `:app`: its own dependency graph, its own implementations of the platform interfaces, and a window.

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

## Where the data is

One private directory per OS user. It is created `rwx------` where POSIX permissions exist.

| OS | Directory |
|---|---|
| Windows | `%APPDATA%\Encly` |
| macOS | `~/Library/Application Support/Encly` |
| Linux | `$XDG_DATA_HOME/encly`, else `~/.local/share/encly` |

| File | What it is |
|---|---|
| `vault_state_v3.bin` | key slots and PIN lockout (every secret in it is already an AES-GCM envelope) |
| `*.db` | the encrypted vault databases (SQLCipher) |
| `encly_flags_v3.properties` | non-secret flags: onboarding done, auto-lock delay, language, window size and place |
| `my_notes_settings.preferences_pb` | appearance and editor settings |
| `cache/` | the cache; the My Notes hand-off staging in it is swept at every start |
| `.instance.lock` | keeps a second Encly of the same user from opening the same vault |

The flags are a small file written atomically (temp file, fsync, rename), not `java.util.prefs`, which would put them
in the registry (Windows) or a plist outside this directory (macOS).

The device-bound half of the PIN key is not in this directory: it lives in the OS secret store (Windows DPAPI,
the macOS login Keychain, the freedesktop Secret Service). See `SECURITY.md`, "Desktop". On Windows the blob of
DPAPI sits in `%APPDATA%\Encly` too. Windows has no POSIX modes; the folder keeps the ACL it inherits from
`%APPDATA%` (the user, SYSTEM and Administrators), which is the same boundary DPAPI gives the secret. Encly does not
rewrite ACLs.

## Security parity with Android

| Android | Desktop | Not available |
|---|---|---|
| `FLAG_SECURE` from the first frame | the window is excluded from screenshots and recordings from the first frame and again whenever the theme asserts it: Windows `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` (Windows 10 2004+; older: `WDA_MONITOR`, a black window), macOS `NSWindow.sharingType = none` | Linux: X11 and Wayland have no such call. macOS: best effort, newer ScreenCaptureKit tools can ignore the flag on macOS 15 and later |
| lock when the app goes to the background, after the auto-lock delay | the same, when the window loses the focus or is minimised (its own file dialogs do not count) | |
| lock at once when the screen turns off | lock at once when the OS locks the session: macOS (the window server's session state, polled every 1.5 s), Windows (the input desktop, polled), Linux (logind `Lock` and `PrepareForSleep`, and the screen-saver `ActiveChanged` of GNOME, KDE, Cinnamon, MATE over D-Bus) | |
| (none) | idle lock: no keyboard or mouse input for the auto-lock delay, but at least 5 minutes, locks a window that stays in front | |
| clipboard marked sensitive, cleared after a minute | cleared after a minute unless something else was copied since, also for what the editor copies, and on quit | AWT has no sensitive flag: a clipboard history (Windows Win+V, clipboard managers) may keep a copy |
| PIN lockout on `elapsedRealtime` and boot count | the OS uptime (including sleep) and the boot id (Linux `boot_id`, macOS `kern.bootsessionuuid`), so restarting the app does not shorten a lockout; Windows has no boot id, the uptime comparison covers it | |
| biometrics | none yet | Windows Hello, Touch ID and fprintd are not wired in |

A second Encly of the same user exits at start (a file lock in the data directory). A backup file given to it is not
handed to the first one; drop it on the open window.

## The window

- minimum size 720 x 520; size, place and maximised state are remembered and clamped to the connected screens;
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
  they go through the Markdown export's warning and PIN (the print warning also names the system print spooler, which
  may keep temporary copies), then the save dialog or the system print dialog. The PDF is written with Apache PDFBox,
  built in memory with the app's Source Sans 3 (Latin and Cyrillic: all nine languages) embedded, and written to the
  file the user picked and nowhere else; it is not touched on the way by the network or a temp file;
- files dropped on the window, or a backup opened from the file manager, go to the same flows as the file dialogs:
  a `.enclybak` answers the restore or import-backup picker, notes (`.md`, `.zip`, `.txt`, `.json`, `.jex`, `.html`)
  and folders answer the import screen's pickers; with none open, the matching screen is opened first;
- file dialogs are the OS's: AWT's native dialogs on macOS and Windows, the freedesktop portal on Linux (the desktop's
  own dialog, with the AWT dialog as fallback);
- the theme follows the OS light and dark mode; a language change recomposes the app (it returns to the home screen);
- the app icon is the brand mark.

## Packaging

`compose.desktop.application.nativeDistributions` in `build.gradle.kts`: MSI, DMG and DEB, package name `Encly`,
bundle id `com.pasich.encly`, a fixed Windows upgrade UUID, per-user MSI install, and the `.enclybak` file
association with its own type `application/vnd.encly.backup` (never `application/octet-stream`).

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
| `--talk-name=org.freedesktop.secrets` | the device-bound half of the PIN key lives in the Secret Service |
| `--talk-name=org.freedesktop.ScreenSaver`, `org.gnome.ScreenSaver`, `org.cinnamon.ScreenSaver`, `org.mate.ScreenSaver` | lock when the desktop's screen saver locks the session |
| `--system-talk-name=org.freedesktop.login1` | lock on logind's `Lock` and on suspend |

The Flatpak keeps its data in `~/.var/app/io.github.pasichdev.Encly/data/encly`, not in `~/.local/share/encly`: a vault
of a `.deb` or tarball install is a different vault there. The bundle is not signed and is not on Flathub; a Flathub
submission needs a build from source (Gradle offline with pinned dependencies) and screenshots in the metainfo.

## No network

Like the Android app, the desktop app makes no network connection and has no update check; updates come from the
installer, a store or a package manager. Nothing in the module opens a socket: the only IPC is D-Bus over a local Unix
socket on Linux (the Secret Service, logind, the file chooser portal), and the OS calls are JNA calls into system
libraries. Opening a link hands it to the default browser (only `https`, `http` and `mailto`).

## Tests and coverage

`desktopTest` runs headless: the graph builds with a temporary directory and fakes in place of the OS, a Compose UI test
reaches the onboarding screen, and the platform services are tested with fakes (clipboard wipe timing on a manual
timer, atomic key-value writes, window state, drop routing, lifecycle and idle lock). Kover gates line coverage at 85 %.
Excluded: `Main` (the entry point), `nativeos` (JNA and D-Bus glue that needs the real OS) and `window.WindowHost` (needs
a real AWT window).
