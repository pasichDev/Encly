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
- **KDF:** дані — HKDF-SHA256 (IKM = високоентропійний seed-хеш); PIN — PBKDF2-HMAC-SHA256 600k
  (OWASP), без нативних залежностей заради повного офлайну. Argon2id — опційно пізніше.
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

- [~] **P0** **Auth gate.** App-level gate реалізовано й компілюється:
  - `initializeSecurity` більше НЕ авто-розблоковує БД, якщо налаштовано PIN — повертає `InitialStatus.AUTH`.
  - `InitialStatus.AUTH` → новий `LockScreen` (`LockViewModel`): PIN-verify з lockout + опційна біометрія,
    розблокування БД лише після успіху (`SecurityManager.unlockAfterAuth`).
  - `SecureDatabaseManager.getDatabase()` тепер fail-loud (кидає), а не тихий in-memory (закриває Phase 2 DI-ризик).
  - ⚠️ ЛИШАЄТЬСЯ: криптографічна прив'язка Keystore-ключа (`setUserAuthenticationRequired` + `CryptoObject`) —
    свідомо НЕ зроблено наосліп (ризик необоротного локауту даних); потребує реалізації + валідації на пристрої.
  - ⚠️ Весь флоу потребує device-тесту (біометрія в емуляторі недоступна) — див. T3-04 у TEST_MATRIX.
  - TODO: re-lock при поверненні з фону; gate для SEED_PHRASE-стратегії (зараз лише PIN/PIN_BIOMETRIC).
- [x] **P0** **Справжня сіль.** Статичний `slay`/enum-салт замінено на 16-байтний per-install `SecureRandom`
  (зберігається в prefs). Деривація даних переведена на **HKDF-SHA256** (info = тип даних).
- [x] **P0** **Зміцнити PIN**: per-PIN сіль + **PBKDF2-HMAC-SHA256 600k** (замість unsalted SHA-256) +
  прогресивний **lockout** після 5 спроб (`remainingLockoutMillis()` для UI). Argon2id свідомо НЕ взято
  (нативна .so, яку не можна перевірити без пристрою) — лишаємось повністю офлайн; легко підмінити пізніше.
- [x] **P0** `android:allowBackup="false"` + реальні exclude-правила (`database.db`, `security_prefs`,
  `secure_prefs`, `integrity_prefs`) для cloud-backup і device-transfer.
- [x] **P1** KDF-вартість: дані — HKDF (IKM високоентропійний); PIN — PBKDF2 600k (OWASP). Стару 10k прибрано.
- [~] **P1** Зачистка секретів: додано у `storeSeedHash`, `verifyMnemonic`, HKDF (prk/okm/ikm);
  живий `SecretKeySpec` лишається (потребує рефактору API повернення).
- [x] **P1** Виправлено інверсію `isUserManuallyCreatedKeyByDecryption()` (equal ⇒ user-managed) + docstring.
  Backwards-guard у `setOnboardingShown()` — лишається (Phase 2).
- [ ] **P2** `SQLCipherUtils.kt:83`: не інтерполювати passphrase у сирий `ATTACH … KEY '$password'` — keyed open/PRAGMA.
- [ ] **P2** Переоцінити `ENCRYPTED_BLOCK_KEY_TWO` "обманку" — це просто прапорець, не контроль; трактувати як untrusted.

---

## Phase 2 — Feature completeness (закрити TODO і зламані флоу)

- [x] **P0** **Нагадування задач — «без тіла нотатки».** `ReminderStore` (plaintext) тримає мінімум
  (id/заголовок/опис/час); `TaskReminderReceiver` будує нотифікацію з extras (без БД);
  reboot-reschedule зі store; snooze = переплан з extras; complete = черга pending-complete,
  застосовується в `TasksViewModel` після unlock. Нотифікації → device-тест.
- [x] **P0** **Справжнє відновлення.** `LossRecoveryScreen` → `LossRecoveryViewModel.wipeAllData()`:
  повний wipe (БД-файли, seed-prefs, Keystore-ключі, integrity HMAC, auth-prefs, ReminderStore) →
  рестарт у onboarding. TODO: опційний re-entry сід-фрази перед wipe.
- [x] **P0** **DI DB provisioning risk** — закрито в Phase 1b: `getDatabase()` тепер fail-loud, тихого in-memory нема.
- [x] **P1** **Serialization round-trip loss**: зберігаються рівень заголовка (H1–H4) і тип списку (numbered/check).
- [x] **P1** **Note copy**: `updateNoteState(isCopy=true)` тепер присвоює копію в `_state`.
- [ ] **P1** Onboarding: додати крок вибору PIN/біометрії (зараз `SecurityChoiceSlide` лише seed vs skip).
- [ ] **P1** `SecuritySettingsViewModel.toggleAuthType` — порожня заглушка; реалізувати + оновлення стану.
- [x] **P1** `EditNoteBottomSheet` Delete/Duplicate підключено (Delete → в кошик, Duplicate → нова копія).
- [~] **P1** User-facing помилки: додано для невірного PIN (LockScreen) і recovery. Фейл декрипту нотатки —
  додано **guard** (`contentLoadFailed`): якщо вміст не розпарсився, `saveNote` не перезапише оригінал.
  Явне UI-повідомлення користувачу — ще лишається.
  (зараз тихий empty-editor / dead-end — і наступний save може перезаписати нечитабельні дані).
- [x] **P2** `skipSecuritySetup()` race виправлено: перехід сторінки тепер лише після успішного збереження ключів.
- [ ] **P2** `SettingsActivity` рендерить `SettingsScreen(navController = null)` — навігація звідти → NPE/no-op.
- [ ] **P2** `BiometricManager.kt:71` "TODO ПРИБРАТИ" — прибрати `isBiometricAvailable()` якщо його заміняє strong-варіант.

---

## Phase 3 — Billing & store readiness

- [x] **P0** **Білінг прибрано.** `BILLING` permission і Google-Play-таб донатів видалено (мертвий in-app "buy"
  UI = ризик Play). Лишився робочий Ko-fi; зламану PayPal-картку прибрано (додати з реальним handle за бажанням).
- [ ] **P0** **Реальний privacy policy URL** замість Tally-заглушки (`Constant.kt`, `LINK_PRIVACY_POLICE`).
  ⚠️ ТВОЯ ДІЯ: потрібен реальний лінк (тепер простий — застосунок офлайн, даних не збирає).
- [x] **P0** **Data Safety** спрощено: застосунок повністю офлайн, дані не залишають пристрій (мережу прибрано в Phase 0).

---

## Phase 4 — Build, ProGuard & CI

- [x] **P0** **Release `signingConfig`** — читається з untracked `keystore.properties` (gitignored);
  без нього білд лишається unsigned (CI без секретів працює). ⚠️ ТВОЯ ДІЯ: створити keystore + `keystore.properties`
  (або Play App Signing з upload-key).
- [x] **P0** **ProGuard keep-правила для kotlinx.serialization і Gson** додано; прибрано небезпечний
  `-assumenosideeffects` на приватних методах `SecurityManager` (R8 міг викинути виклик unlock).
  Перевірено `assembleRelease` (зелено). Device smoke-тест — за тобою.
- [x] **P1** GitHub Actions CI: `lintDebug` + `testDebugUnitTest` + `assembleRelease` (Java 21) — `.github/workflows/android.yml`.
- [x] **P2** Конфлікт `-keepattributes` розв'язано (лишили SourceFile/LineNumberTable + renamesourcefile); мертві правила прибрано.
- [~] **P2** Manifest: `BootReceiver exported=false`, прибрано `BIND_REMOTEVIEWS`/`USE_FINGERPRINT`/`ACTION_CREATE_SHORTCUT`.
  Лишається: `USE_EXACT_ALARM` (Play-restricted — рішення за тобою), `<queries scheme="*">`, `SettingsActivity exported`.
- [ ] **P2** Автоматизувати `versionCode`/`versionName`.
- [x] **P2** App-update прибрано повністю (Phase 0, offline) — форсований IMMEDIATE зник разом із ним.

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
