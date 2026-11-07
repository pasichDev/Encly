package com.pasich.encly.core.backup

import com.pasich.encly.core.security.AutoLockDelay

/**
 * Which user preferences travel in a backup, and what each may hold.
 *
 * This is an **allow-list**. A preference is only exported if it is in [included], and only
 * restored if its key is there and its value passes the key's [SettingKind]. Everything that
 * is not user taste is listed in [excluded] with the reason, so adding a preference key anywhere
 * in the app forces a decision: a test scans the sources for every key the app reads or writes
 * in its flag store and DataStore and fails for one that is in neither list.
 *
 * Never add security state here: no PIN, biometric, duress or wipe slot or flag, lockout
 * counter, onboarding or vault-commit flag, screenshot allowance, device-bound key or pending
 * wipe or restore marker. A restored backup must not be able to weaken the vault it lands in.
 * Those live in the vault state file, the Keystore or memory, not in the preference stores;
 * the keys below are only what the preference stores hold.
 *
 * Wire format (`settings` of [BackupPayload], schema 5): a map from the preference's storage
 * key to a string. A boolean is `true` or `false`, a number its decimal digits, a choice the
 * **name** of the enum entry (never its ordinal, which an app may reorder).
 */
public object BackupSettings {
    /** What a value of one setting may look like. */
    public class SettingKind internal constructor(private val check: (String) -> Boolean) {
        public fun accepts(value: String): Boolean = check(value)
    }

    /** The ranges and names mirror the app's own enums; a test in `:shared` keeps them in step. */
    public const val MIN_FONT_SIZE: Int = 10
    public const val MAX_FONT_SIZE: Int = 32

    public val THEME_TYPES: List<String> = listOf("SYSTEM", "LIGHT", "DARK")
    public val THEME_PALETTES: List<String> = listOf("PAPER", "FOREST", "OCEAN", "GRAPHITE", "MIDNIGHT")
    public val FONT_STYLES: List<String> = listOf("MODERN_SIMPLE", "COZY_EDITOR", "TECH_MINIMAL")
    public val LINE_SPACINGS: List<String> = listOf("DEFAULT", "COMPACT", "COMFORTABLE")
    public val SORT_OPTIONS: List<String> = listOf("UPDATED_ASC", "UPDATED_DESC", "CREATED_ASC", "CREATED_DESC")
    public val SWIPE_ACTIONS: List<String> = listOf("PIN", "ARCHIVE", "NEXT_COLOR", "DELETE", "NONE")
    public val LANGUAGES: List<String> = listOf(
        "SYSTEM",
        "ENGLISH",
        "UKRAINIAN",
        "GERMAN",
        "FRENCH",
        "SPANISH",
        "ITALIAN",
        "POLISH",
        "PORTUGUESE",
        "DUTCH",
    )

    public const val IS_DYNAMIC_THEME: String = "is_dynamic_theme"
    public const val THEME_TYPE: String = "theme_type"
    public const val THEME_PALETTE: String = "theme_palette"
    public const val IS_GRID_NOTE_LIST: String = "is_grid_note_list"
    public const val SORT: String = "sort"
    public const val SHOW_TASKS: String = "show_tasks"
    public const val SIMPLE_EDIT: String = "simple_edit"
    public const val FONT_SIZE: String = "font_size"
    public const val FONT_STYLE: String = "font_style"
    public const val NOTE_LINE_SPACING: String = "note_line_spacing"
    public const val SWIPE_ENABLED: String = "swipe_enabled"
    public const val SWIPE_RIGHT: String = "swipe_right"
    public const val SWIPE_LEFT: String = "swipe_left"
    public const val AUTO_LOCK_DELAY: String = "auto_lock_delay"
    public const val KEYBOARD_STRICT_PRIVACY: String = "keyboard_strict_privacy"
    public const val SHOW_NOTE_PREVIEWS: String = "show_note_previews"
    public const val APP_LANGUAGE: String = "app_language"

    private val bool = SettingKind { it == "true" || it == "false" }

    private fun choice(names: Collection<String>) = SettingKind { it in names }

    private fun number(range: IntRange) = SettingKind { value -> value.toIntOrNull()?.let { it in range } == true }

    /** Every preference that is exported and restored, with the values it accepts. */
    public val included: Map<String, SettingKind> = linkedMapOf(
        // Appearance.
        IS_DYNAMIC_THEME to bool,
        THEME_TYPE to choice(THEME_TYPES),
        THEME_PALETTE to choice(THEME_PALETTES),
        FONT_STYLE to choice(FONT_STYLES),
        FONT_SIZE to number(MIN_FONT_SIZE..MAX_FONT_SIZE),
        NOTE_LINE_SPACING to choice(LINE_SPACINGS),
        // Lists and editor.
        IS_GRID_NOTE_LIST to bool,
        SORT to choice(SORT_OPTIONS),
        SHOW_TASKS to bool,
        SIMPLE_EDIT to bool,
        SWIPE_ENABLED to bool,
        SWIPE_RIGHT to choice(SWIPE_ACTIONS),
        SWIPE_LEFT to choice(SWIPE_ACTIONS),
        // Privacy preferences the user chose. They describe how the app behaves, never a
        // credential. Auto-lock is the one a backup could loosen; it is the user's own choice.
        AUTO_LOCK_DELAY to choice(AutoLockDelay.entries.map { it.name }),
        KEYBOARD_STRICT_PRIVACY to bool,
        SHOW_NOTE_PREVIEWS to bool,
        // Language.
        APP_LANGUAGE to choice(LANGUAGES),
    )

    /**
     * Keys the preference stores hold that are **not** backed up, with why. Window geometry is
     * the desktop's, one install's own. The rest is state about this install or this vault.
     */
    public val excluded: Map<String, String> = linkedMapOf(
        "onboarding_shown_v3" to "vault-commit flag: setup is finished on this install only",
        "whats_new_last_seen_version" to "what's-new marker of this install",
        "backup_last_export_at" to "describes an export made from this install",
        "appearance_migrated_v1" to "one-time migration marker of this install",
        "window_width" to "device-bound desktop window geometry",
        "window_height" to "device-bound desktop window geometry",
        "window_x" to "device-bound desktop window geometry",
        "window_y" to "device-bound desktop window geometry",
        "window_maximised" to "device-bound desktop window geometry",
    )

    /** Most entries a restore looks at; a file with more is a hand-made one and the rest is dropped. */
    public const val MAX_ENTRIES: Int = 64

    /**
     * The entries of [raw] a restore may apply: a known key whose value that key accepts. An
     * unknown key (a newer app's), a bad value and anything past [MAX_ENTRIES] are dropped
     * one by one; this never throws, so a bad setting can never fail a restore.
     */
    public fun accepted(raw: Map<String, String>): Map<String, String> = raw.entries.asSequence()
        .take(MAX_ENTRIES)
        .filter { (key, value) -> included[key]?.accepts(value) == true }
        .associate { it.key to it.value }
}
