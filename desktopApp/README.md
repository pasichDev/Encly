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
OS (`jpackage` does not cross-build); CI builds all three, unsigned, in the `desktop-package` job.

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
- menu bar (the native one on macOS): File (new note, import, backup, lock now, settings, exit), View (theme,
  language), Window, Help (about, licences, FAQ). The shortcuts use Cmd on macOS and Ctrl elsewhere. There is no Edit
  menu on purpose: the text fields handle cut, copy, paste and undo themselves, and a menu item with the same
  shortcut would take the key from them on macOS. The menu titles "File", "View", "Window", "Help", "Exit" and
  "Minimize" are English for now; the items use the app's translated strings;
- menu items ask the UI to open a screen through `NavigationRequests` (`:shared`, `platform`), which `EnclyApp`
  collects. They are ignored while the vault is locked, so no item skips the lock screen;
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

Signing and notarization are not done by this repository's CI. The hooks are environment variables that turn them
on in a local or release build; no secret is in the repository:

| Variable | Used for |
|---|---|
| `ENCLY_MACOS_SIGN_IDENTITY` | the Developer ID Application identity; when set, the DMG is signed |
| `ENCLY_MACOS_NOTARY_APPLE_ID`, `ENCLY_MACOS_NOTARY_PASSWORD`, `ENCLY_MACOS_NOTARY_TEAM_ID` | notarization |

Windows signing (signtool on the MSI) is a TODO for the release workflow. A Flatpak manifest (Flathub) is a follow-up;
the portal file dialogs are already the ones a Flatpak needs.

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
