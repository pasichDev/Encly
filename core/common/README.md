# :core:common

The bottom layer of the module graph. It depends on nothing of Encly and runs on every target.

- `UiText`, `AppStrings`: text kept as a resource id and resolved by the UI (`UiText`) or stored in the in-app
  language (`AppStrings`).
- `LoadState`, `suspendRunCatching`: the loading / ready / failed state of a screen.
- `KeyValueStore`, `MainThreadTimer`: what the platform provides for small non-secret flags and for the lock timers.
- `SensitiveDataCleaner`: wipes key material in place.
- `AppLogger`: the failure-only logger (its output is disabled on purpose; nothing sensitive reaches a log).

| Target | Checked in |
|---|---|
| Android | `androidHostTest` (JVM) |
| Desktop JVM | `desktopTest` (also `CommonBoundaryTest`: no `android.`, `java.` or `R` in `commonMain`) |
| iOS (`iosArm64`, `iosSimulatorArm64`) | compiled in CI on macOS |

The Android implementations of the interfaces (`SharedPreferencesKeyValueStore`, `HandlerMainThreadTimer`,
`AndroidAppStrings`, `asString`) are in `:app`. Line coverage is gated at 85 %.
