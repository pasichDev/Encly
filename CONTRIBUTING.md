# Contributing to Encly

Thanks for helping. Encly is a security-focused, offline-only app, so a few rules are stricter
than usual. Please read this file and [SECURITY.md](SECURITY.md) before opening a pull request.
By taking part you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ground rules

- **No network.** Do not add the `INTERNET` permission, networking libraries, analytics,
  crash reporting, telemetry, ads, or downloadable fonts/resources. CI fails if a merged release
  manifest requests `INTERNET`.
- **No proprietary dependencies.** Nothing that needs Google Play Services or another closed
  SDK in the shared code; store-only UI goes into the `play` flavor behind `BuildConfig`.
- **No plaintext outside the vault.** No new share, clipboard, notification, file-export or
  logging path for note/task content, PINs or seed words.
- **Existing installs must keep opening.** Any change to key derivation, the SQLCipher
  passphrase, the vault slots, preferences layout or the Room schema needs a migration and a
  test that upgrades the old state. Never "fix" a format by wiping data.
- **Security-model changes update [SECURITY.md](SECURITY.md)** in the same pull request.
- Security vulnerabilities are reported privately (see SECURITY.md), not in issues or PRs.

## Setting up

1. Install **JDK 21** (Android Studio's bundled JBR works) and the Android SDK with
   `platforms;android-36` and `build-tools;36.0.0`.
2. Point Gradle at the SDK: `export ANDROID_HOME=...` or `sdk.dir=...` in an untracked
   `local.properties` (see [README → Build](README.md#build)).
3. Open the project in Android Studio or use `./gradlew`. Pick the `fdroidDebug` variant for
   day-to-day work.

## Before you open a pull request

Run the same gates as CI:

```bash
./gradlew spotlessCheck                                               # formatting (ktlint)
./gradlew :app:testFdroidDebugUnitTest :app:koverVerifyFdroidDebug   # tests + coverage gate
./gradlew :app:detekt                                                 # config/detekt/detekt.yml
./gradlew :app:lintFdroidRelease :app:lintPlayRelease                  # release lint
./gradlew assembleRelease                                              # R8 build
```

- Add or extend unit tests for behaviour you change. Code in `core/security` has a minimum line
  coverage gate (Kover, see `app/build.gradle.kts`); do not lower it to get a PR through.
- For storage, Keystore or SQLCipher changes also run the instrumented tests on an emulator:
  `./gradlew :app:connectedFdroidDebugAndroidTest` (they wipe the debug app's data).
- New detekt or lint findings must be fixed, not added to `app/detekt-baseline.xml` or
  `app/lint-baseline.xml`. Pull requests that shrink those baselines are very welcome.
- For UI changes, say in the PR which device or emulator you checked it on.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for user-visible changes.

## Code style

- Kotlin coding conventions (IntelliJ IDEA / Android Studio style), 4-space indent, 120 columns.
  [`.editorconfig`](.editorconfig) holds the rules; Android Studio picks it up, and
  [Spotless](https://github.com/diffplug/spotless) runs ktlint with the same file.
  `./gradlew spotlessApply` formats everything, `./gradlew spotlessCheck` (CI) only checks.
- [detekt](https://detekt.dev) (`config/detekt/detekt.yml`) adds code-smell, unused-code and
  [Jetpack Compose](https://mrmans0n.github.io/compose-rules/) rules: public composables take a
  `modifier: Modifier = Modifier` applied to their root, ViewModels are obtained with a
  `hiltViewModel()` default parameter and not passed down to child composables (hoist state and
  pass lambdas instead), and event lambdas are named in the present tense (`onClick`, not
  `onClicked`).
- Android lint severities live in [`app/lint.xml`](app/lint.xml): hardcoded or untranslated
  text, unused resources and lint's security checks are errors.
- Layers: `presentation` → `domain` → `data`; key handling lives in `core/security`.
- Comments explain *why*, especially around cryptography and lifecycle.

## Git hooks

The repository ships two opt-in hooks in [`.githooks/`](.githooks):

- `pre-commit` runs `spotlessCheck` and `:app:detekt` when the commit stages Kotlin or Gradle
  files (`*.kt`, `*.kts`, `gradle/libs.versions.toml`, `.editorconfig`, `config/detekt/`), and
  does nothing for other commits, so docs and resource commits stay instant. It checks the
  working tree, so unstaged edits count too.
- `commit-msg` rejects a subject line that is not a Conventional Commit (see below).

Enable them once per clone with either of:

```bash
./gradlew installGitHooks
git config core.hooksPath .githooks
```

`git commit --no-verify` skips them for one commit; CI runs the same checks regardless.

## Commits and pull requests

- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`,
  `test:`, `build:`, `ci:`, `refactor:`, `perf:`, `style:`, `revert:`, `chore:`, with an optional
  scope and `!` for breaking changes, e.g. `fix(security): zeroize DEK after rekey`. The subject
  line stays within 100 characters (the `commit-msg` hook checks both).
- Keep a pull request to one topic. Fill in the checklist of the PR template.

## Room schema changes

Room schemas are exported to `app/schemas` and committed. When an entity changes: bump
`DB_VERSION` (`data/database/AppDatabase.kt`), add a `Migration`, commit the new schema JSON, and add a
`runMigrationsAndValidate` case to `AppDatabaseSchemaTest`. Destructive migration is disabled on
purpose.

## Translations

Every user-visible string lives in string resources. English is the default
(`app/src/main/res/values/`); each translation is a `values-<lang>/` folder with a `strings.xml`
(most of the UI) and a `strings_security.xml` (security settings).

**Fixing a translation:** edit the string in `values-<lang>/strings.xml` (or
`strings_security.xml`) and open a PR. Keep the key; only change the text.

**Adding a string (code change):** add it in English to `values/strings.xml` (or
`strings_security.xml`) and to the same file in *every* `values-<lang>/`, even if only with an
English placeholder you flag in the PR. Use it
with `stringResource(R.string.…)` in Compose. Text produced outside the UI (ViewModels,
managers, validators) must not be resolved there: return a resource id or a `UiText`
(`core/common/UiText.kt`) and resolve it in the UI, so it follows the in-app language. Never
resolve UI text with the application context — it ignores the in-app language on Android 12 and
lower.

**Adding a language:**

1. copy `values/strings.xml` and `values/strings_security.xml` to `values-<lang>/` and translate them,
   leaving out the entries marked `translatable="false"`;
2. add `<locale android:name="<lang>" />` to `res/xml/locales_config.xml`;
3. add an entry to `AppLanguage` (`core/locale/AppLanguage.kt`) and its own-language name as a
   non-translatable `language_name_<lang>` string in `values/strings.xml`;
4. add the language's CLDR plural categories to `REQUIRED_QUANTITIES` in
   `TranslationCompletenessTest`.

**Rules the build enforces** (`./gradlew :app:testFdroidDebugUnitTest :app:lintFdroidDebug`):
every locale has every key and no extra ones (lint `MissingTranslation` is an error, and
`TranslationCompletenessTest` checks it too); format arguments (`%1$s`, `%d`, …) match English;
each `<plurals>` has all categories its language uses (e.g. `one`/`few`/`many`/`other` for
Ukrainian and Polish); apostrophes and double quotes are escaped (`\'`, `\"`).

**Style:** natural, concise UI language rather than word-for-word translation. Keep security
terms precise and consistent within a language: *PIN*, *recovery phrase* / *seed phrase*
(the 12 BIP39 words), *vault*, *encryption*, *biometrics*. Never put seed words or other secrets
into resources.

## Releasing

(Maintainers.) Three channels ship the same source and the same application ID
`com.pasich.encly`: **GitHub Releases** (both flavors as APKs, automatic on a tag),
**Google Play** (the `play` flavor as an App Bundle) and **F-Droid** (F-Droid builds the `fdroid`
flavor itself from the tag). Installs can only upgrade each other when every channel is signed
with the same certificate, so all three use the `ENCLY_*` release key (in Play App Signing,
upload this key instead of letting Google generate one).

### One-time setup

- Create the release keystore (keep it outside the repository) and add the repository secrets
  `ENCLY_KEYSTORE_BASE64`, `ENCLY_KEYSTORE_PASSWORD`, `ENCLY_KEY_ALIAS`, `ENCLY_KEY_PASSWORD`;
  optionally `ENCLY_GPG_PRIVATE_KEY` and `ENCLY_GPG_PASSPHRASE` to sign `SHA256SUMS`. A tag
  pushed without the four signing secrets fails; it never publishes unsigned APKs.
- Publish the signing certificate's SHA-256 fingerprint in
  [README → Verify a release](README.md#verify-a-release) and set it as the repository variable
  `ENCLY_CERT_SHA256` (required: the release and Play workflows fail without it), so the
  workflows pin the certificate.
- Store screenshots and feature graphics are in place for every locale, in
  `fastlane/metadata/android/<locale>/images/` (8 phone screenshots, 1080×1920, demo data only,
  plus a 1024×500 `featureGraphic.png`). Keep all 9 locales in step when you replace them.
- The privacy policy the app and both store listings link to is published at
  <https://pasichdev.xyz/apps/encly/privacy-policy/>; keep it in step with `PRIVACY.md`.
- Make the repository public, then enable **Settings → Code security → Private vulnerability
  reporting** (SECURITY.md links to it).
- Google Play: create the app and fill in *App content*. The first bundle is uploaded by hand.
- F-Droid: open an inclusion merge request against
  [fdroiddata](https://gitlab.com/fdroid/fdroiddata) once `v2.0.0` is tagged.

### Every release

1. Bump `VERSION_MAJOR/MINOR/PATCH` in `version.properties`. `versionCode` follows
   automatically (`MAJOR*10000 + MINOR*100 + PATCH`) and must only grow.
2. Move `[Unreleased]` in `CHANGELOG.md` to a dated `## [X.Y.Z] - YYYY-MM-DD` section whose first
   line is `versionCode N.` F-Droid's update check reads the version name and code from these two
   lines, because it cannot evaluate `version.properties`; no test checks them, so compare them
   with `version.properties` by hand. The release workflow uses this section as the GitHub Release
   notes, so use absolute links in it.
3. Write the store release notes (≤ 500 characters each) to
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for **every** locale folder
   (`en-US`, `uk`, `de-DE`, `fr-FR`, `es-ES`, `it-IT`, `pl-PL`, `pt-PT`, `nl-NL`);
   `StoreMetadataTest` checks they exist and fit.
4. Test the release APK on a device.
5. Merge to `main`, then tag the merge commit `vX.Y.Z` and push the tag. The
   [release workflow](.github/workflows/release.yml) refuses a tag that does not match
   `version.properties`, fails when a signing secret or `ENCLY_CERT_SHA256` is missing, builds
   both flavors unsigned, signs them with `apksigner` in a separate step (Gradle never sees
   the keystore), checks the certificate against `ENCLY_CERT_SHA256`, and publishes the APKs
   with `SHA256SUMS` (signed as `SHA256SUMS.asc` when the GPG secrets are set) and the R8
   mapping files.
6. Google Play: either upload `app-play-release.aab` by hand
   (`./gradlew :app:bundlePlayRelease` with the `ENCLY_*` variables set), or, once enabled, run
   the [Publish to Google Play](.github/workflows/publish-play.yml) workflow with the tag; it
   uploads to the internal track, from where you promote it in Play Console.
7. F-Droid picks the new tag up by itself (`AutoUpdateMode: Version`), usually within a few days.

### F-Droid reproducibility

What keeps the `fdroid` flavor reproducible (do not undo): no baseline profile for `fdroid`
variants, `dependenciesInfo` excluded from APK and bundle, `vcsInfo` excluded from release
builds, no build timestamps or other machine-dependent values in `BuildConfig` or resources, and
a pinned Gradle wrapper and dependency versions.
