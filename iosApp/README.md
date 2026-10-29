# Encly for iOS

The iPhone and iPad app. It is a thin SwiftUI shell around the Kotlin framework `Shared` (the `:shared` module):
the shell shows `MainViewController()`, and the vault, the lock, the screens and every security rule are Kotlin.
Nothing in the project asks for the network, the camera or any other permission: the only usage description is
`NSFaceIDUsageDescription` for the optional Face ID / Touch ID unlock.

| Path | What it is |
|---|---|
| `iosApp.xcodeproj` | The Xcode project (checked in; no generator). The `Sources` folder is a synchronized group: add a file there and it is in the target. |
| `Sources/` | `EnclyApp.swift` (the app, blocks third-party keyboards), `ContentView.swift` (hosts the Kotlin view controller), `Assets.xcassets` (the icon; 1024 px upscaled from the 512 px brand mark, replace it with the art master). |
| `Info.plist` | Bundle id `io.github.pasichdev.Encly` (the id of the Flatpak), no ATS exceptions, `LSApplicationQueriesSchemes` = `mailto` only (to know whether a mail app exists). |

## Build

The first build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, which builds the static
framework for the SDK and architecture Xcode is building and copies the Compose resources into the app. It needs a
JDK 21 (`JAVA_HOME`, else `/usr/libexec/java_home -v 21`) and takes several minutes the first time.
`GRADLE_COMMAND` replaces `./gradlew` (a wrapper script, for example).

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

The version is written into the app from `version.properties` by the last build phase, so a release bumps one file
for Android, the desktop and iOS. Only arm64 is built (Apple silicon simulators and every supported device).

## Running on the Simulator

An unsigned Simulator build has no application identifier, so the Keychain refuses every call: the app starts and
shows the screens, but cannot create a PIN. Build with `CODE_SIGN_IDENTITY=-` (ad-hoc signing) to try the whole
flow; Face ID is `Features → Face ID → Enrolled` in the Simulator.

## What differs from Android

See the table in the pull request description and `SECURITY.md`; in short: no way to block a screenshot (the
app switcher snapshot and screen recording are covered), the in-app language applies after a restart, an auto-lock
delay longer than iOS lets a background app run locks when that time is up, and the PDF export and printing are not
offered, as on Android.
