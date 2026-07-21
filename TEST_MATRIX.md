# Encly — Test Matrix

Цей документ закриваєш **ти сам**. Roadmap-виправлення (`ROADMAP.md`) роблю я;
тести пишеш і "зелениш" ти. Поточний стан: **0 тестів**, немає `test/` і `androidTest/`,
Room-схема не експортована.

## Ключове обмеження (визначає JVM vs androidTest)

`SeedPhraseManager`, `HmacIntegrityManager`, `AuthenticationManager` залежать від **AndroidKeyStore**,
`android.util.Base64`, `SharedPreferences`. Чистий JVM JUnit їх не проганяє (Base64 "not mocked",
keystore GCM у Robolectric ненадійний). Тому крипто-round-trip → **androidTest на емуляторі/пристрої**.
`SecureDatabaseManager` на SQLCipher — DAO-тести робити на plain in-memory Room або фіксованому test-passphrase,
не чіпляючи `SeedPhraseManager`.

---

## Матриця

Type: **U** = JVM unit (`test/`) · **A** = instrumented (`androidTest/`) · **C** = Compose UI · **E** = E2E.

### Tier 1 — security core + чиста логіка (почати звідси)

| ID | Область | Тест-кейс | Type | Що перевіряє | Пріор. |
|----|---------|-----------|------|--------------|--------|
| T1-01 | SeedPhrase | `generateMnemonic_returns12ValidWords` | U | `generateMnemonic()` → 12 слів, що приймає `isValidMnemonic()` | P0 |
| T1-02 | SeedPhrase | `isValidMnemonic_rejectsTamperedPhrase` | U | змінене/прибране слово → `false`, без витоку винятку | P0 |
| T1-03 | SeedPhrase | `hashMnemonic_isDeterministicSha256` | U | однакова фраза → ідентичний 32-байт хеш; різні → різні | P0 |
| T1-04 | SeedPhrase | `storeThenVerifyMnemonic_roundTripsTrue` | A | `storeSeedHash` → `verifyMnemonic(same)`==true; wrong==false | P0 |
| T1-05 | SeedPhrase | `deriveKey_isDeterministicAndSaltScoped` | A | `getEncryptionKeyForData` стабільний для одного `SaltData`, різний для DATABASE/AUTH/MEDIA | P0 |
| T1-06 | SeedPhrase | `manualKeyDetection_distinguishesUserVsAuto` | A | рівні блоки ⇒ manual==false, різні ⇒ true (**звірити після фіксу інверсії!**) | P0 |
| T1-07 | HMAC | `hmac_verifyGenuineHashIsNotTampered` | A | після `storeHmac(hash)` → `isHashTampered(hash)`==false | P0 |
| T1-08 | HMAC | `hmac_detectsTamperedOrMissingHmac` | A | змінений хеш ⇒ true; відсутній HMAC ⇒ true | P0 |
| T1-09 | Serialization | `blockConverter_jsonRoundTripPreservesAllBlockTypes` | U | Text/H/Quote/Link/List/Separator serialize→deserialize рівні | P0 |
| T1-10 | Serialization | `blockSerializer_dropsBlankAndEmptyBlocks` | U | blank Text/H/Quote і порожній List → JsonNull; unknown type → JsonParseException | P1 |
| T1-11 | Serialization | `serializer_preservesHeadingLevelAndListType` | U | H1–H4 і numbered/check переживають round-trip (**після фіксу serialization**) | P0 |
| T1-12 | Serialization | `markdownConverter_rendersHeadingsQuotesListsLinks` | U | `[x]/[ ]`, `1.`, `>`, `[title](url)` коректні | P1 |
| T1-13 | UseCase | `authUseCase_savePin_failsWhenAlreadyEnabled_andOnActivationFailure` | U | already-enabled→failure; activation false→failure; інакше success | P0 |
| T1-14 | UseCase | `onboardingUseCase_saveKeysStore_failsWhenStoreHashFails` | U | будь-який `storeSeedHash`==false → failure, DB unlock не пробується; обидва true → success | P0 |
| T1-15 | Auth | `verifyPinAuth_matchesStoredHashAndClearsSensitiveData` | A | вірний PIN→true, невірний→false; `cancelPinAuth` скидає AUTH_TYPE→NONE, вимикає біометрію | P0 |
| T1-16 | Auth | `pinLockout_blocksAfterNFailedAttempts` | A | після N невірних спроб — lockout (**після фіксу P0 у Phase 1**) | P0 |

### Tier 2 — repositories, DAO, ViewModels

| ID | Область | Тест-кейс | Type | Що перевіряє | Пріор. |
|----|---------|-----------|------|--------------|--------|
| T2-01 | DAO | `notesDao_insertUpdateDelete_andTrashFilter` | A | CRUD + фільтр `isTrash` (in-memory Room) | P1 |
| T2-02 | DAO | `notesDao_getByTagId_handlesNullBranch` | A | фільтр по tagId + гілка NULL | P1 |
| T2-03 | DAO | `noteWithTag_transactionRelationLoads` | A | `@Transaction` relation `NoteWithTag` | P1 |
| T2-04 | DAO | `daos_emitFlowOnMutation` | A | Flow-емісії Notes/Tags/Tasks при зміні | P1 |
| T2-05 | Repo | `repositoryImpls_delegateToDataSourceWithArgs` | U | кожен метод делегує в datasource з правильними арг. і повертає результат | P1 |
| T2-06 | VM | `onboardingViewModel_pageNavBounds_andCreateSecurityStates` | U | межі сторінок, success/failure `createUserManagedSecurity`, seed-word verify, `completeVerification` | P1 |
| T2-07 | VM | `tasksViewModel_stateTransitionsAndErrorMapping` | U | create/edit/complete/clear + фільтри | P1 |
| T2-08 | VM | `noteListViewModel_loadSortFilter` | U | сортування/фільтр/стан списку | P2 |
| T2-09 | VM | `tagListViewModel_selectReorderVisibility` | U | selection/reorder/visibility | P2 |
| T2-10 | VM | `trashViewModel_restoreCleanAllCleanSelected` | U | restore/cleanAll/cleanSelected | P2 |
| T2-11 | VM | `securitySettingsViewModel_toggleAuthTypeUpdatesState` | U | стан оновлюється після enable/error (**після фіксу порожньої заглушки**) | P1 |

### Tier 3 — Compose UI + E2E

| ID | Область | Тест-кейс | Type | Що перевіряє | Пріор. |
|----|---------|-----------|------|--------------|--------|
| T3-01 | UI | `onboardingSlides_renderInputClick` | C | рендер + ввід + click→стан для onboarding-слайдів | P2 |
| T3-02 | UI | `pinCodeWidget_inputAndValidation` | C | ввід PIN, валідація, помилковий стан | P2 |
| T3-03 | UI | `editNote_dynamicBlocksFocusAndEdit` | C | blocks + `dynamicBlocks/focus` поведінка | P2 |
| T3-04 | E2E | `journey_onboarding_seed_createNote_lock_unlock` | E | onboarding→seed→нотатка→lock→unlock (PIN і seed) | P1 |
| T3-05 | E2E | `journey_recoverAfterTamper` | E | LOSS_CRYPTO/LOSS_DATABASE → recovery-флоу (**після фіксу recovery**) | P1 |
| T3-06 | E2E | `journey_taskReminderFires` | E | створити задачу з нагадуванням → alarm → нотифікація (**після фіксу reminders**) | P1 |
| T3-07 | E2E | `journey_autoManagedVsUserManaged` | E | обидва шляхи onboarding (skip vs create seed) | P2 |

---

## Залежності для додавання (Kotlin 2.2 / Compose 1.8.3 / Room 2.7.2 / Hilt 2.56.2)

```kotlin
// ---- Unit (test/) ----
testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
testImplementation("app.cash.turbine:turbine:1.2.1")
testImplementation("androidx.arch.core:core-testing:2.2.0")
testImplementation("org.robolectric:robolectric:4.14.1")            // Base64/SharedPreferences у JVM (НЕ keystore)
testImplementation("androidx.test.ext:junit:1.2.1")
testImplementation("com.google.truth:truth:1.4.4")                  // опц.
// (junit 4.13.2, androidx.test:core 1.6.1, mockito-core 5.18.0 — вже є)

// ---- Instrumented (androidTest/) ----
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test:runner:1.6.2")
androidTestImplementation("androidx.test:rules:1.6.1")
androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
androidTestImplementation("androidx.room:room-testing:2.7.2")
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
androidTestImplementation("app.cash.turbine:turbine:1.2.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.3")
debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.3")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.56.2")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.56.2")
```

У `android {}`:
```kotlin
defaultConfig { testInstrumentationRunner = "com.pasich.encly.CustomTestRunner" } // Hilt-aware runner (створити)
testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
```

І згенерувати/закомітити Room-схему в `app/schemas` (для майбутніх migration-тестів).

---

## Порядок закриття (рекомендація)

1. Підняти харнес на найдешевшому: **T1-09/10/12** (serialization, чистий JVM).
2. **T1-01/02/03** + use-cases **T1-13/14** (JVM).
3. Емулятор-набір: **T1-04..08, 15, 16** (крипто round-trip).
4. Tier 2 (DAO + repo + VM), потім Tier 3.

Кейси з позначкою "**після фіксу ...**" залежать від відповідних пунктів `ROADMAP.md` —
писати їх після того, як я закрию фікс, інакше вони червонітимуть по-правильному.
