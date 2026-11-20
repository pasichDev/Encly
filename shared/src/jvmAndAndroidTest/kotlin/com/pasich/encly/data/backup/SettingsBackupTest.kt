package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupSettings
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.data.datasource.local.MAX_FONT_SIZE
import com.pasich.encly.data.datasource.local.MIN_FONT_SIZE
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.EditorToolbar
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.NotePreviews
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.model.ToolbarLayout
import com.pasich.encly.domain.model.ToolbarSection
import com.pasich.encly.domain.model.ToolbarTool
import com.pasich.encly.platform.AppLocaleController
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.TestSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val flags = InMemoryKeyValueStore()
    private val repository = TestSettingsRepository()
    private val locales = FakeLocales()
    private val autoLock = AutoLock(flags)
    private val keyboard = KeyboardPrivacy(flags)
    private val previews = NotePreviews(flags)
    private val toolbar = EditorToolbar(flags)
    private val backup = SettingsBackup(repository, autoLock, keyboard, previews, locales, toolbar)

    @Test
    fun theSnapshotHoldsExactlyTheAllowListedKeys() = runTest {
        assertEquals(BackupSettings.included.keys, backup.snapshot().keys)
    }

    @Test
    fun everySnapshotValueIsOneTheRestoreAccepts() = runTest {
        val snapshot = backup.snapshot()

        assertEquals(snapshot, BackupSettings.accepted(snapshot))
    }

    @Test
    fun whatIsExportedIsRestoredOnAnotherInstall() = runTest {
        repository.setDynamicTheme(true)
        repository.setThemeType(ThemeType.DARK)
        repository.setThemePalette(ThemePalette.OCEAN)
        repository.setFontStyle(FontStyleType.TECH_MINIMAL)
        repository.setFontSize(21)
        repository.setNoteLineSpacing(NoteLineSpacing.COMFORTABLE)
        repository.setGridNoteList(true)
        repository.setSortNotes(NoteSortOption.CREATED_ASC)
        repository.setShowTasks(false)
        repository.setSimpleEdit(true)
        repository.setSwipeEnabled(false)
        repository.setSwipeAction(SwipeSide.RIGHT, SwipeAction.DELETE)
        repository.setSwipeAction(SwipeSide.LEFT, SwipeAction.NONE)
        autoLock.setDelay(AutoLockDelay.MINUTES_5)
        keyboard.setStrict(true)
        previews.setShow(false)
        locales.apply(AppLanguage.GERMAN)
        val exported = backup.snapshot()

        val other = fresh()
        other.backup.apply(exported)

        assertEquals(exported, other.backup.snapshot())
        assertEquals(AppLanguage.GERMAN, other.locales.current())
        assertEquals(AutoLockDelay.MINUTES_5, other.autoLock.delay.value)
        assertTrue(other.keyboard.strict.value)
        assertFalse(other.previews.show.value)
    }

    @Test
    fun unknownKeysAndBadValuesAreIgnoredPerKey() = runTest {
        val target = fresh()

        target.backup.apply(
            mapOf(
                BackupSettings.THEME_TYPE to "DARK",
                BackupSettings.THEME_PALETTE to "NEON",
                BackupSettings.FONT_SIZE to "999",
                BackupSettings.AUTO_LOCK_DELAY to "NEVER",
                BackupSettings.APP_LANGUAGE to "KLINGON",
                "pin.slot" to "x",
                "from_the_future" to "1",
            ),
        )

        val after = target.backup.snapshot()
        assertEquals("DARK", after[BackupSettings.THEME_TYPE])
        assertEquals(ThemePalette.DEFAULT.name, after[BackupSettings.THEME_PALETTE])
        assertEquals("16", after[BackupSettings.FONT_SIZE])
        assertEquals(AutoLockDelay.DEFAULT.name, after[BackupSettings.AUTO_LOCK_DELAY])
        assertEquals(AppLanguage.SYSTEM, target.locales.current())
    }

    @Test
    fun securityStateInAFileIsNeverWrittenToTheFlagStore() = runTest {
        val target = fresh()

        target.backup.apply(
            mapOf(
                "onboarding_shown_v3" to "true",
                "whats_new_last_seen_version" to "9.9.9",
                "backup_last_export_at" to "5",
                "auth.biometric_enabled" to "true",
                "pin.wipe.slot" to "x",
                "lockout.failures" to "0",
            ),
        )

        listOf(
            "onboarding_shown_v3",
            "whats_new_last_seen_version",
            "backup_last_export_at",
            "auth.biometric_enabled",
            "pin.wipe.slot",
            "lockout.failures",
        ).forEach { assertFalse(it, target.flags.contains(it)) }
    }

    @Test
    fun aFailingStoreSkipsOnlyItsOwnKey() = runTest {
        val failing = object : TestSettingsRepository() {
            override suspend fun setThemeType(value: ThemeType) = error("disk full")
        }
        val target = fresh(failing)

        target.backup.apply(
            mapOf(BackupSettings.THEME_TYPE to "DARK", BackupSettings.FONT_SIZE to "20"),
        )

        assertEquals(20, failing.fontSizeFlow.value)
    }

    @Test
    fun everyBackupNameIsAnEntryOfItsEnum() {
        assertEquals(ThemeType.entries.map { it.name }, BackupSettings.THEME_TYPES)
        assertEquals(ThemePalette.entries.map { it.name }, BackupSettings.THEME_PALETTES)
        assertEquals(FontStyleType.entries.map { it.name }, BackupSettings.FONT_STYLES)
        assertEquals(NoteLineSpacing.entries.map { it.name }, BackupSettings.LINE_SPACINGS)
        assertEquals(NoteSortOption.entries.map { it.name }, BackupSettings.SORT_OPTIONS)
        assertEquals(ToolbarTool.entries.map { it.name }, BackupSettings.TOOLBAR_TOOLS)
        assertEquals(ToolbarSection.entries.map { it.name.lowercase() }, BackupSettings.TOOLBAR_SECTIONS)
        assertEquals(SwipeAction.entries.map { it.name }, BackupSettings.SWIPE_ACTIONS)
        assertEquals(AppLanguage.entries.map { it.name }, BackupSettings.LANGUAGES)
        assertEquals(MIN_FONT_SIZE, BackupSettings.MIN_FONT_SIZE)
        assertEquals(MAX_FONT_SIZE, BackupSettings.MAX_FONT_SIZE)
    }

    @Test
    fun theToolbarLayoutIsExportedAndRestored() = runTest {
        val custom = ToolbarLayout.DEFAULT.moved(ToolbarTool.TABLE, -20).toggled(ToolbarTool.QUOTE)
        toolbar.set(custom)
        val snapshot = backup.snapshot()

        val other = fresh()
        other.backup.apply(snapshot)

        assertEquals(custom, other.toolbar.layout.value)
    }

    @Test
    fun aToolbarLayoutThatNamesNoToolIsNotRestored() = runTest {
        val other = fresh()
        other.backup.apply(mapOf(BackupSettings.EDITOR_TOOLBAR to "main:NOT_A_TOOL"))
        other.backup.apply(mapOf(BackupSettings.EDITOR_TOOLBAR to "tools:HEADING"))

        assertEquals(ToolbarLayout.DEFAULT, other.toolbar.layout.value)
    }

    private fun fresh(settings: TestSettingsRepository = TestSettingsRepository()) = Target(settings)

    private class Target(settings: TestSettingsRepository) {
        val flags = InMemoryKeyValueStore()
        val locales = FakeLocales()
        val autoLock = AutoLock(flags)
        val keyboard = KeyboardPrivacy(flags)
        val previews = NotePreviews(flags)
        val toolbar = EditorToolbar(flags)
        val backup = SettingsBackup(settings, autoLock, keyboard, previews, locales, toolbar)
    }

    private class FakeLocales : AppLocaleController {
        private var language = AppLanguage.SYSTEM

        override fun current(): AppLanguage = language

        override fun apply(language: AppLanguage) {
            this.language = language
        }
    }
}
