# Encly — Production Roadmap

Мета: довести Encly до релізу в Play Store. Джерело — повний аудит по 5 напрямках
(безпека, техборг, build/білінг/CI, тести, E2E-флоу).

Легенда: **P0** = блокер релізу · **P1** = важливо · **P2** = гігієна.
Статус: `[ ]` не почато · `[~]` в роботі · `[x]` закрито.

### Продуктові рішення (узгоджено)
- **Offline-first / «справді офлайн».** Застосунок не ходить у мережу взагалі. Прибираємо
  мережеве прев'ю лінків (jsoup), Google Play in-app update, а тоді й `INTERNET`/`ACCESS_NETWORK_STATE`.
  Data Safety → «дані не залишають пристрій».
- **Білінг:** прибрати для v1, лишити Ko-fi/PayPal-посилання.
- **KDF:** Argon2id.
- **SDK:** compileSdk/targetSdk = 36 (Android 16, найновіший стабільний).
- **Zero-knowledge / recovery:** втратив seed → дані втрачено (жодного backdoor-відновлення).
  Recovery-флоу = re-entry сід-фрази АБО повний wipe + рестарт у onboarding.
- **Бекапи лише офлайн:** експорт зашифрованого файлу в локальне сховище + імпорт з файлу; жодної хмари.

---

## Phase 0 — Cleanup & safety net (розблоковує решту, низький ризик)

Спочатку прибираємо мертвий/дубльований код, щоб далі не тягнути другий крипто-шлях
і не аудити те, що піде під ніж.

- [x] **P1** Видалити повністю OLD-крипто-стек (це живий, слабший другий шлях, не сміття):
  - `core/security/old/` — `AuthenticationManagerOLD.kt`, `SecurityManagerOLD.kt`, `CryptoManagerOLD.kt`
    (містить hardcoded `FALLBACK_SEED_PHRASE` і device-key з `ANDROID_ID`).
  - `old/auth/` — `AuthActivity.kt`, `AuthScreen.kt`, `AuthViewModel.kt`, `SecurityViewModel.kt`.
  - `presentation/viewmodel/NewAuthViewModel.kt` (єдиний "живий" споживач OLD-менеджерів, сам нікуди не підключений).
  - Провайдери в `core/di/SecurityModule.kt:79-95` (+ `provideCryptoManager`) та імпорти.
  - `<activity .old.auth.AuthActivity>` з `AndroidManifest.xml:62-66`.
  - Порядок: спершу прибрати посилання (NewAuthViewModel), потім класи, потім DI/manifest.
- [x] **P1** Прибрати невживані Google-залежності (0 імпортів у коді):
  `google-auth-library-oauth2-http`, `googleid`, `androidx.credentials:credentials`,
  `credentials-play-services-auth`. (Save-to-Drive реалізовано звичайним share-intent.)
- [x] **P0** **Offline-first:** прибрати мережу повністю:
  - `LinkBlock`: замінити `Jsoup.connect(url).get()` прев'ю на введені користувачем URL+назву; викинути `jsoup`.
  - Прибрати Google Play in-app update (`AppUpdateManager`, `app-update`/`app-update-ktx`, виклики в `MainActivity`).
  - Після цього прибрати `INTERNET` і `ACCESS_NETWORK_STATE` з маніфесту.
  - Data Safety → «no data leaves device».
- [~] **P2** Прибрати debug-сміття: `println` у `EditNoteViewModel`, `AuthenticationManager` — прибрано;
  `println` у `NavHost.kt:95` лишається (це заглушка recovery → фіксується в Phase 2).
- [~] **P2** Замінити `printStackTrace()` на логер: `BlockConverter` (x2), `SeedPhraseManager` — прибрано;
  2 у `TaskReminderService` лишаються (закоментований reminder-subsystem → Phase 2). Proguard-правило — Phase 4.
- [ ] **P2** Загейтити `Log.d/v` (118 викликів, найтяжче — `SecureDatabaseManager` 19) за `BuildConfig.DEBUG` / Timber DebugTree.
- [~] **P2** Брендинг: `rootProject.name` → `Encly` — зроблено;
  hardcoded "My Notes - Seed Phrase Backup" (`rememberSeedPhraseActions.kt:112,115`) — лишається.

---

## Phase 1 — Security core (головний блок довіри)

- [ ] **P0** **Auth gate реально працює.** Зараз PIN зберігається, але **ніколи не перевіряється**
  (`SecurityManager.initializeSecurity` має `// TODO` і безумовно розблоковує БД на старті).
  - Keystore-ключ створювати з `setUserAuthenticationRequired(true)` + `setUserAuthenticationParameters(...)`.
  - Розблокування БД — тільки всередині biometric/PIN `CryptoObject`-флоу.
  - Прибрати авто-unlock у `SecurityManager.kt:54`.
  - Зробити `InitialStatus.AUTH` досяжним і обробленим у `MainActivity.kt:46` (→ реальний lock-екран, не Home).
- [ ] **P0** **Справжня сіль.** Замінити статичний `slay`/enum-салт (`SeedPhraseManager.kt:164-176`)
  на 16-байтний per-install `SecureRandom` (зберігати в prefs). Перейти з "SHA-256 → PBKDF2(static salt)" на **HKDF**;
  не переюзати seed-хеш і як верифікатор, і як ключ-матеріал.
- [ ] **P0** **Зміцнити PIN** (`AuthenticationManager.kt:48-52`): унікальна сіль + Argon2id/висока вартість
  замість unsalted SHA-256, і **lockout/rate-limit** після N спроб.
- [ ] **P0** `android:allowBackup="false"` + реальні exclude-правила для `database.db` і security-prefs
  (`AndroidManifest.xml:42`, `res/xml-v25/backup_rules.xml`, `data_extraction_rules.xml` — зараз шаблон-заглушка).
- [ ] **P1** KDF-вартість: Argon2id (64 MiB, t=3) або ≥600k ітерацій PBKDF2 (`SeedPhraseManager.kt:186`, зараз 10k).
- [ ] **P1** Консистентна зачистка секретів: очищати живий `SecretKeySpec`/`tmp.encoded`, `decryptedHash` у `verifyMnemonic`,
  `hash` у `storeSeedHash`; PIN тримати як `CharArray`/`ByteArray`, чистити у `finally`.
- [ ] **P1** Виправити інверсію `isUserManuallyCreatedKeyByDecryption()` (`SeedPhraseManager.kt:228-233`) —
  зараз повертає false для ручного і true для авто (навпаки задуму); і backwards-guard у `setOnboardingShown()`.
- [ ] **P2** `SQLCipherUtils.kt:83`: не інтерполювати passphrase у сирий `ATTACH … KEY '$password'` — keyed open/PRAGMA.
- [ ] **P2** Переоцінити `ENCRYPTED_BLOCK_KEY_TWO` "обманку" — це просто прапорець, не контроль; трактувати як untrusted.

---

## Phase 2 — Feature completeness (закрити TODO і зламані флоу)

- [ ] **P0** **Нагадування задач працюють.** Зараз alarm спрацьовує в порожні ресівери (тіла закоментовані,
  посилаються на видалений `AppDatabase.getInstance` — навіть не компілюються):
  - `TaskReminderReceiver.onReceive`, `TaskReminderService.checkAndShowReminders`,
    `TaskReminderScheduler.rescheduleAllReminders` (reboot), `TaskNotificationReceiver` (complete/snooze).
  - Явно вирішити проблему "БД залочена, коли спрацьовує alarm".
- [ ] **P0** **Справжнє відновлення.** `LossRecoveryScreen` confirm зараз = `println` (`NavHost.kt:95`).
  Має: wipe seed (`clearStoredSeed`) + файли БД (`deleteDatabaseFiles`/`reset`) + prefs → рестарт у onboarding.
  Бажано — re-entry сід-фрази (`verifyMnemonic`) перед деструктивним wipe. Обробити і `LOSS_CRYPTO`, і `LOSS_DATABASE`.
- [ ] **P0** **DI DB provisioning risk.** `LocalDataModule.provideAppDatabase` (@Singleton) один раз бере `getDatabase()`;
  коли залочено — повертається **незашифрована in-memory** БД і кешується на весь процес. Не роздавати
  `AppDatabase`/DAO як eager-singleton від `getDatabase()`; маршрутизувати доступ через менеджер за поточним станом unlock;
  ніколи не віддавати тихий in-memory fallback для реальних даних.
- [ ] **P1** **Serialization round-trip loss** (`BlockSerialization.kt:32-58`): зберігати рівень заголовка (H1–H4,
  зараз усе → H1) і тип списку (numbered vs check, зараз усе → LIST_CHECK).
- [ ] **P1** **Note copy** (`EditNoteViewModel.kt:191-208`): `updateNoteState(isCopy=true)` рахує copy і **викидає результат** —
  метадані копії губляться. Присвоїти в `_state`.
- [ ] **P1** Onboarding: додати крок вибору PIN/біометрії (зараз `SecurityChoiceSlide` лише seed vs skip).
- [ ] **P1** `SecuritySettingsViewModel.toggleAuthType` (`:66-68`) — порожня заглушка, що керує security-налаштуванням; реалізувати + оновлення стану.
- [ ] **P1** `EditNoteBottomSheet` Delete (`:178`) і Duplicate (`:156`) — порожні лямбди; підключити або сховати
  (Delete виглядає деструктивним, але нічого не робить).
- [ ] **P1** User-facing помилки для: невірний PIN, пошкоджена БД, фейл декрипту нотатки
  (зараз тихий empty-editor / dead-end — і наступний save може перезаписати нечитабельні дані).
- [ ] **P2** `skipSecuritySetup()` race: `nextPage()` викликається синхронно до завершення async-збереження ключів.
- [ ] **P2** `SettingsActivity` рендерить `SettingsScreen(navController = null)` — навігація звідти → NPE/no-op.
- [ ] **P2** `BiometricManager.kt:71` "TODO ПРИБРАТИ" — прибрати `isBiometricAvailable()` якщо його заміняє strong-варіант.

---

## Phase 3 — Billing & store readiness

- [ ] **P0** **Вирішити білінг.** Реалізації немає (лише permission + порожні лямбди в `SupportScreen`).
  Рекомендація для v1: **прибрати** `com.android.vending.BILLING` і Google-Play-таб донатів,
  лишити робочі Ko-fi/PayPal (виправити порожній `https://paypal.me/` → `SupportScreen.kt:363`).
  Мертвий in-app "buy" UI = ризик відхилення Play. (Альтернатива — повноцінний Play Billing, це кілька днів.)
- [ ] **P0** **Реальний privacy policy URL** замість Tally-заглушки з `// TODO` (`Constant.kt:4`, `LINK_PRIVACY_POLICE`).
- [ ] **P0** **Data Safety форма** чесно: мережа використовується (jsoup link-preview тягне довільні URL + in-app update),
  seed можна експортувати через share. Історія "повністю офлайн" — неточна.

---

## Phase 4 — Build, ProGuard & CI

- [ ] **P0** **Release `signingConfig`** — зараз `assembleRelease` дає **непідписаний APK**. Читати з `keystore.properties`/env
  (не комітити), або upload-key + Play App Signing.
- [ ] **P0** **ProGuard keep-правила для kotlinx.serialization і Gson** (обидва відсутні) — інакше краш/тихий фейл
  (де)серіалізації в release. Перевірити реальним `assembleRelease` + smoke-тест.
- [ ] **P1** GitHub Actions CI: `lintDebug` + `testDebugUnitTest` + `assembleRelease` (Java 21);
  androidTest — на емуляторі. Виставити `lint.abortOnError = true` (або окремий lint-гейт).
- [ ] **P2** Розв'язати конфлікт `-keepattributes SourceFile,LineNumberTable` vs `!SourceFile,!LineNumberTable`
  у proguard; вантажити mapping у Play. Прибрати мертві правила (`com.google.api.client.**`, `okhttp3`).
- [ ] **P2** Manifest hygiene: `BootReceiver` `exported=false`; прибрати `BIND_REMOTEVIEWS` (не тримається),
  `USE_FINGERPRINT` (deprecated), фейковий `ACTION_CREATE_SHORTCUT`; переоцінити `USE_EXACT_ALARM`
  (Play-restricted); звузити `<queries scheme="*">`; переглянути `SettingsActivity exported=true`.
- [ ] **P2** Автоматизувати `versionCode`/`versionName`.
- [ ] **P2** App-update: не форсити `IMMEDIATE` на будь-яке оновлення (re-trigger після dismiss) — FLEXIBLE + priority.

---

## Phase 5 — Verify (перед кожним merge у release-гілку)

- [ ] Реальний `./gradlew assembleRelease` + smoke-тест ключових флоу на пристрої.
- [ ] Прогнати весь тест-набір (див. `TEST_MATRIX.md`) зеленим на емуляторі.
- [ ] Перевірити, що в git-історії немає ключів/секретів/keystore.

---

## Критичний шлях (порядок виконання)

`Phase 0 (cleanup)` → `Phase 1 (auth gate + salt + backup)` → `Phase 2 (reminders + recovery + DI + serialization)`
→ `Phase 3 (billing/privacy)` → `Phase 4 (signing/proguard/CI)` → `Phase 5 (verify)`.

Три "головні дірки продукту", які маркетяться як фічі, але не працюють:
1. Launch auth gate (PIN/біометрія нічого не захищає) — Phase 1.
2. Доставка нагадувань задач (alarm → порожній ресівер) — Phase 2.
3. Recovery при пошкодженні (`println` no-op) — Phase 2.
