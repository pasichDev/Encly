# :core:resources

Every string and plural the UI shows, in English and eight translations (de es fr it nl pl pt uk),
as Compose Multiplatform resources. Android, desktop JVM and iOS targets.

- Files: `src/commonMain/strings/values[-<lang>]/{strings,strings_handoff,strings_import,strings_security}.xml`,
  in Android's `strings.xml` format, exactly what Weblate reads and writes. The build step
  `convertAndroidStrings` (`ConvertAndroidStrings` in `build-logic`) turns them into the
  `composeResources` directory Compose resources read: it drops Android's backslash escapes (`\'`) and
  numbers `%d` / `%s`, and `%%` becomes `%`.
- API: the generated, public `com.pasich.encly.core.resources.Res` (`Res.string.<key>`,
  `Res.plurals.<key>`). In Compose use `stringResource` / `pluralStringResource`; elsewhere the suspend
  `getString` / `getPluralString`, or `UiText` / `AppStrings` in the app.
- Language: the resources follow the process's default locale. On Android `AppLocales.apply` keeps it on
  the in-app language.
- Tests: `AndroidStringsConversionTest` holds the converted resources to what Android would have shown,
  for every string and plural of every language.
- Not here: the launcher label (`app_name`), which Android reads from `app/src/main/res/values/`.
- Depends on nothing of Encly.

See [CONTRIBUTING.md → Translations](../../CONTRIBUTING.md#translations).
