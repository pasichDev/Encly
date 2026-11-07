package com.pasich.encly.data.backup

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.backup.BackupSettings
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.NotePreviews
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.platform.AppLocaleController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The user's preferences as a backup carries them: [BackupSettings.included] keys, string values.
 * Kept apart from [BackupManager] so the vault logic does not know where each preference lives.
 */
interface BackupPreferences {
    /** The current value of every backed-up preference. Never holds security state. */
    suspend fun snapshot(): Map<String, String>

    /**
     * Applies the entries of [raw] this version understands ([BackupSettings.accepted]). One
     * that cannot be applied is skipped; this never throws for a bad value.
     */
    suspend fun apply(raw: Map<String, String>)
}

/** The preferences to export; none, rather than a failed export, if they cannot be read. Allow-listed keys only. */
internal suspend fun BackupPreferences.snapshotOrEmpty(): Map<String, String> = try {
    snapshot().filterKeys { it in BackupSettings.included }
} catch (e: CancellationException) {
    throw e
} catch (_: RuntimeException) {
    AppLogger.w("BackupPreferences", "Backup without settings: they could not be read")
    emptyMap()
}

/** Restores [raw]; a failure is logged and never fails the restore that called it. */
internal suspend fun BackupPreferences.applyQuietly(raw: Map<String, String>) {
    try {
        apply(raw)
    } catch (e: CancellationException) {
        throw e
    } catch (_: RuntimeException) {
        AppLogger.w("BackupPreferences", "Settings were not restored")
    }
}

/** No preferences: tests, and a host that has none. */
object NoBackupPreferences : BackupPreferences {
    override suspend fun snapshot(): Map<String, String> = emptyMap()

    override suspend fun apply(raw: Map<String, String>) = Unit
}

/**
 * The app's preference stores behind [BackupPreferences]: the settings repository (DataStore), the
 * auto-lock delay, strict keyboard privacy and note previews (flag store, held in memory by their
 * own classes, so they are set through those), and the in-app language.
 */
@Inject
@ContributesBinding(AppScope::class)
@Suppress("LongParameterList") // One collaborator per place a preference lives.
class SettingsBackup(
    private val settings: SettingsRepository,
    private val autoLock: AutoLock,
    private val keyboardPrivacy: KeyboardPrivacy,
    private val notePreviews: NotePreviews,
    private val locales: AppLocaleController,
) : BackupPreferences {

    override suspend fun snapshot(): Map<String, String> {
        val theme = settings.themeSettingsFlow.first()
        val swipe = settings.swipeSettingsFlow.first()
        val all = linkedMapOf(
            BackupSettings.IS_DYNAMIC_THEME to theme.dynamic.toString(),
            BackupSettings.THEME_TYPE to theme.type.name,
            BackupSettings.THEME_PALETTE to theme.palette.name,
            BackupSettings.FONT_STYLE to theme.fontStyle.name,
            BackupSettings.FONT_SIZE to settings.fontSizeFlow.first().toString(),
            BackupSettings.NOTE_LINE_SPACING to settings.noteLineSpacingFlow.first().name,
            BackupSettings.IS_GRID_NOTE_LIST to settings.isGridNoteList.first().toString(),
            BackupSettings.SORT to settings.getSortNotes.first().name,
            BackupSettings.SHOW_TASKS to settings.showTasksFlow.first().toString(),
            BackupSettings.SIMPLE_EDIT to settings.simpleEditFlow.first().toString(),
            BackupSettings.SWIPE_ENABLED to swipe.enabled.toString(),
            BackupSettings.SWIPE_RIGHT to swipe.right.name,
            BackupSettings.SWIPE_LEFT to swipe.left.name,
            BackupSettings.AUTO_LOCK_DELAY to autoLock.delay.value.name,
            BackupSettings.KEYBOARD_STRICT_PRIVACY to keyboardPrivacy.strict.value.toString(),
            BackupSettings.SHOW_NOTE_PREVIEWS to notePreviews.show.value.toString(),
            BackupSettings.APP_LANGUAGE to locales.current().name,
        )
        // Only what the allow-list names, whatever else is added above by mistake.
        return all.filterKeys { it in BackupSettings.included }
    }

    override suspend fun apply(raw: Map<String, String>) {
        val values = BackupSettings.accepted(raw)
        applyEach(values)
        // Last: on Android a language change recreates the running screen.
        values[BackupSettings.APP_LANGUAGE]?.let(::languageOf)?.let { language ->
            skippingFailure(BackupSettings.APP_LANGUAGE) { if (language != locales.current()) locales.apply(language) }
        }
    }

    private suspend fun applyEach(values: Map<String, String>) {
        values.forEach { (key, value) ->
            skippingFailure(key) { applyOne(key, value) }
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per backed-up preference.
    private suspend fun applyOne(key: String, value: String) {
        when (key) {
            BackupSettings.IS_DYNAMIC_THEME -> settings.setDynamicTheme(value.toBoolean())

            BackupSettings.THEME_TYPE -> named<ThemeType>(value)?.let { settings.setThemeType(it) }

            BackupSettings.THEME_PALETTE -> named<ThemePalette>(value)?.let { settings.setThemePalette(it) }

            BackupSettings.FONT_STYLE -> named<FontStyleType>(value)?.let { settings.setFontStyle(it) }

            BackupSettings.FONT_SIZE -> value.toIntOrNull()?.let { settings.setFontSize(it) }

            BackupSettings.NOTE_LINE_SPACING -> named<NoteLineSpacing>(value)?.let { settings.setNoteLineSpacing(it) }

            BackupSettings.IS_GRID_NOTE_LIST -> settings.setGridNoteList(value.toBoolean())

            BackupSettings.SORT -> named<NoteSortOption>(value)?.let { settings.setSortNotes(it) }

            BackupSettings.SHOW_TASKS -> settings.setShowTasks(value.toBoolean())

            BackupSettings.SIMPLE_EDIT -> settings.setSimpleEdit(value.toBoolean())

            BackupSettings.SWIPE_ENABLED -> settings.setSwipeEnabled(value.toBoolean())

            BackupSettings.SWIPE_RIGHT -> swipeAction(SwipeSide.RIGHT, value)

            BackupSettings.SWIPE_LEFT -> swipeAction(SwipeSide.LEFT, value)

            BackupSettings.AUTO_LOCK_DELAY -> named<AutoLockDelay>(value)?.let(autoLock::setDelay)

            BackupSettings.KEYBOARD_STRICT_PRIVACY -> keyboardPrivacy.setStrict(value.toBoolean())

            BackupSettings.SHOW_NOTE_PREVIEWS -> notePreviews.setShow(value.toBoolean())

            // The language is applied after the others (see [apply]); any other key is not ours.
            else -> Unit
        }
    }

    private suspend fun swipeAction(side: SwipeSide, value: String) {
        named<SwipeAction>(value)?.let { settings.setSwipeAction(side, it) }
    }

    private fun languageOf(value: String): AppLanguage? = named<AppLanguage>(value)

    private inline fun <reified T : Enum<T>> named(value: String): T? = enumValues<T>().firstOrNull { it.name == value }

    private suspend fun skippingFailure(key: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            // A preference that cannot be written is only a preference; the restore goes on.
            AppLogger.w(TAG, "Skipped restoring setting $key")
        }
    }

    private companion object {
        const val TAG = "SettingsBackup"
    }
}
