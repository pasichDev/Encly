# Next Version Implementation Plan

> **For agentic workers:** Work through the checked tasks in order. The decoy vault requires a security design review before implementation.

**Goal:** Deliver the open dependency updates and issues #59, #60, #61 and #63 as a reviewed next release.

**Architecture:** Keep existing encrypted-vault behavior as the baseline. Land the build-tool updates together, add the small user preferences without changing stored note data, and design the decoy vault as a genuinely separate encrypted database before connecting it to the lock screen. Weblate remains a contributor service and never becomes an app dependency.

**Tech Stack:** Kotlin, Jetpack Compose, Room, SQLCipher, Gradle, GitHub Actions, Android resources, Weblate.

**Spec:** GitHub issues #59, #60, #61, #63 and Dependabot PRs #32, #34–#41, #48, #49, #57, #58.

## Global Constraints

- Keep the existing 15-second auto-lock default and immediate locking on screen-off/device lock.
- Keep the app offline; release manifests must not contain `android.permission.INTERNET`.
- Keep strict translation completeness, placeholder and Android lint checks.
- Preserve primary-vault keys, existing encrypted notes, wipe-PIN behavior, and recovery compatibility.
- Do not advertise forensic-proof plausible deniability.

## Task 1: Dependency and CI updates

**Files:** `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `.github/workflows/{android,publish-play,release}.yml`, and build scripts that need AGP 9 adaptation.

- [ ] Combine all open Dependabot version changes, including the Gradle 9 / AGP 9 / Hilt set that fails when updated separately.
- [ ] Raise `compileSdk` to 37 for SQLCipher 4.19.0 while keeping the shipping target SDK and minimum SDK unchanged.
- [ ] Adapt Kotlin, KSP, Kover, Hilt and Android build configuration to the final toolchain.
- [ ] Run the existing format, unit test, coverage, detekt, release build, lint and no-INTERNET gates.
- [ ] Exercise an upgrade of an existing encrypted database on an Android device or emulator before considering SQLCipher complete.

## Task 2: Auto-lock options (#60)

**Files:** `core/security/AutoLock.kt`, `presentation/screen/settings/AutoLockSetting.kt`, all `strings_security.xml` locales, `AutoLockTest.kt`, `SessionLockManagerTest.kt`.

- [ ] Add persistent 15- and 30-minute choices to the existing policy and Settings → Security selector.
- [ ] Explain the exposure from a longer unlocked background session beside those choices.
- [ ] Verify persisted values, the unchanged default, timeout boundary, screen-off and device-lock behavior.

## Task 3: Editor line spacing (#59)

**Files:** editor settings storage and ViewModel, `EditNoteScreen.kt`, `EditorBlocksHost.kt`, paragraph/list/quote blocks, appearance or editor settings UI, all locale strings and editor tests.

- [ ] Add a stored Default/Compact selector reachable from the note editor.
- [ ] Apply compact line height to wrapped text and reduce the gap between consecutive paragraph blocks in editing and read-only views.
- [ ] Keep note text and block serialization unchanged; verify selection survives an app restart.

## Task 4: Decoy-vault security design and implementation (#61)

**Files:** security RFC, `SecurityManager`, `SessionLockManager`, `SecureDatabaseManager`, `VaultStore`, key-slot handling, repositories/ViewModels, lock screen, settings, security documentation and integration tests.

- [ ] Write and review the key hierarchy, three-PIN dispatch, crash recovery, database ownership, backup/recovery/biometric rules, migration and forensic limits.
- [ ] Implement a separate random decoy DEK, wrapped key and SQLCipher database; never share an unlocked primary DB or repository handle with a decoy session.
- [ ] Add opt-in setup with fresh primary authentication and collision checks against primary and wipe PINs.
- [ ] Route all three PINs through the existing keypad with uniform visible behavior and retain wipe-PIN destruction semantics.
- [ ] Isolate notes, search, tags, tasks, trash, backup/export controls, recents and pending writes across sessions.
- [ ] Test relock, two-way switching, process death during every setup stage, failed unlock, OS lock, upgrade and downgrade behavior; complete security review.

## Task 5: Weblate contributor workflow (#63)

**Files:** Weblate project/components, GitHub integration and branch permissions, README, CONTRIBUTING, translator guidance, glossary and CI smoke tests.

- [ ] Verify hosted eligibility or choose a maintained hosting arrangement; confirm Android XML and plurals round-trip without data loss.
- [ ] Configure two components using the existing main and security string resource paths and import all supported locales.
- [ ] Connect Weblate to a dedicated translation branch and PR review flow without direct `main` writes.
- [ ] Demonstrate an English-key sync, translation edit, generated PR, complete CI pass and clean resync.
- [ ] Publish translator entry point and maintainer conflict/rollback instructions; keep release validation unchanged.

## Release gate

- [ ] Re-read all four issues against the implementation and record any unmet acceptance criterion in the draft PR.
- [ ] Run the full CI command and verify a release artifact without INTERNET permission.
- [ ] Merge only after the security review and the Weblate round trip are complete.
