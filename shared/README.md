# :shared

Everything of the app that is not Android-specific, in Kotlin Multiplatform (Android and desktop JVM) with Compose
Multiplatform.

- `domain`: use cases and repository contracts.
- `data`: the repositories, the settings store, and the backup, Markdown export, notes import and My Notes hand-off
  logic. Files arrive as `kotlinx-io` sources and sinks through `PlatformDocuments`.
- `presentation`, `ui`, `utils`: the design system, screens, dialogs, navigation (`EnclyApp`, `AppNavHost`),
  ViewModels (Metro, contributed to the host's graph) and the theme and fonts.
- `platform`: the interfaces the host app implements for what only a platform can do (document dialogs and files,
  links, toasts, the sensitive clipboard, the window, the biometric host, the in-app language, the build's facts).
  The host gives the composables theirs through the `Local...` values around `EnclyApp`.

It depends on `:core:*` and `:editor`, never on `:app` (`SharedBoundaryTest`). There is no iOS target while
`:core:crypto` has none.

| Target | Checked in |
|---|---|
| Android | `testAndroidHostTest` (JVM; the Compose UI tests run under Robolectric) |
| Desktop JVM | `desktopTest` (also `SharedBoundaryTest`: no `android.`, `java.` or Android-bound locals in `commonMain`) |

Tests that need no Android are in `jvmAndAndroidTest` (JUnit and Mockito, run on both). Line coverage is gated at 85 %.
The fonts and the brand mark are Compose resources of this module; the strings are `:core:resources`'.
