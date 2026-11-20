package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** What a keyboard shortcut asks the app to do. */
enum class AppCommand {
    /** Start a new note. */
    NewNote,

    /** Find in the note that is open. */
    FindInNote,

    /** Lock the vault now. */
    LockApp,

    /** Close the current note tab. */
    CloseTab,

    /** Go to the next open tab. */
    NextTab,

    /** Go to the previous open tab. */
    PreviousTab,

    /** Move the current note to the trash (the app asks first). */
    DeleteNote,

    /** Close what is open on top: a dialog, a sheet, the find bar. */
    Dismiss,

    /** Print the open note. Asked by the host's menu, which owns its chord. */
    PrintNote,

    /** Save the open note as a PDF. Asked by the host's menu, which owns its chord. */
    ExportPdf,
}

/**
 * The keyboard shortcuts of the desktop app, as one map from key chords to [AppCommand]s. The command
 * key is Meta (⌘) on macOS and Ctrl elsewhere ([commandKeyIsMeta]); a chord with the other one, or with
 * Alt, is not ours, so text editing, the window manager and the OS keep theirs.
 *
 * | Command | macOS | Windows and Linux |
 * |---|---|---|
 * | [AppCommand.NewNote] | ⌘N | Ctrl+N |
 * | [AppCommand.FindInNote] | ⌘F | Ctrl+F |
 * | [AppCommand.LockApp] | ⌘L | Ctrl+L |
 * | [AppCommand.CloseTab] | ⌘W | Ctrl+W |
 * | [AppCommand.NextTab] | ⌃Tab, ⌘⇧] | Ctrl+Tab, Ctrl+PageDown |
 * | [AppCommand.PreviousTab] | ⌃⇧Tab, ⌘⇧[ | Ctrl+Shift+Tab, Ctrl+PageUp |
 * | [AppCommand.DeleteNote] | ⌘⇧⌫ | Ctrl+Shift+Backspace |
 * | [AppCommand.Dismiss] | Esc | Esc |
 * | [AppCommand.PrintNote] | ⌘P (menu) | Ctrl+P (menu) |
 * | [AppCommand.ExportPdf] | ⌘⇧P (menu) | Ctrl+Shift+P (menu) |
 *
 * The last two are asked by the host's menu only; the map has no key for them, so the host never sees their chord.
 *
 * Delete is Shift-qualified on purpose: plain ⌘⌫ / Ctrl+Backspace belong to the text field (delete to
 * the line start / the previous word), and the app confirms before it trashes a note anyway.
 *
 * Wire it once at the app root with [enclyShortcuts] or [EnclyShortcutHost]; the commands are the app's to run.
 * A host that has its own menu with accelerators (the desktop menu bar) must not register a chord of this map
 * as well as the host: [keyOf] gives the menu the key of a command it owns, and the host is told to decline that
 * command, so a chord has exactly one owner.
 */
class EnclyShortcuts(private val commandKeyIsMeta: Boolean = platformUsesCommandKey()) {
    /**
     * The command a key press asks for, or null when it is not one of ours (or not a key down).
     * [AppCommand.Dismiss] is Esc alone.
     */
    fun commandFor(event: KeyEvent): AppCommand? {
        if (event.type != KeyEventType.KeyDown) return null
        return when {
            event.key == Key.Escape -> AppCommand.Dismiss.takeIf { !anyModifier(event) }
            event.isAltPressed -> null
            tabSwitch(event) != null -> tabSwitch(event)
            !commandPressed(event) || otherCommandPressed(event) -> null
            else -> chordCommand(event)
        }
    }

    /**
     * The key of [command] when it is the command key plus one letter (New, Find, Lock, Close tab), else null.
     * A menu that registers the accelerator itself builds it from this, so the map stays the one source.
     */
    fun keyOf(command: AppCommand): Key? = LETTER_COMMANDS[command]?.key

    /** Whether the command key is Meta (⌘) rather than Ctrl, as this map was made for. */
    val usesMetaKey: Boolean get() = commandKeyIsMeta

    /** The shortcut of [command] as the user reads it: "⌘N" on macOS, "Ctrl+N" elsewhere. */
    fun label(command: AppCommand): String {
        val mod = if (commandKeyIsMeta) "⌘" else "Ctrl+"
        return when (command) {
            AppCommand.NewNote, AppCommand.FindInNote, AppCommand.LockApp, AppCommand.CloseTab ->
                "$mod${LETTER_COMMANDS.getValue(command).label}"

            AppCommand.NextTab -> if (commandKeyIsMeta) "⌃Tab" else "Ctrl+Tab"

            AppCommand.PreviousTab -> if (commandKeyIsMeta) "⌃⇧Tab" else "Ctrl+Shift+Tab"

            AppCommand.DeleteNote -> if (commandKeyIsMeta) "⌘⇧⌫" else "Ctrl+Shift+Backspace"

            AppCommand.Dismiss -> "Esc"

            AppCommand.PrintNote -> "${mod}P"

            AppCommand.ExportPdf -> if (commandKeyIsMeta) "⌘⇧P" else "Ctrl+Shift+P"
        }
    }

    private fun commandPressed(event: KeyEvent) = if (commandKeyIsMeta) event.isMetaPressed else event.isCtrlPressed

    private fun otherCommandPressed(event: KeyEvent) =
        if (commandKeyIsMeta) event.isCtrlPressed else event.isMetaPressed

    private fun anyModifier(event: KeyEvent) =
        event.isCtrlPressed || event.isMetaPressed || event.isAltPressed || event.isShiftPressed

    /** The Mac habit for tabs, ⌘⇧] and ⌘⇧[; elsewhere Page Down and Up. */
    private fun tabChord(event: KeyEvent): AppCommand? = when {
        event.key == Key.RightBracket && event.isShiftPressed -> AppCommand.NextTab
        event.key == Key.LeftBracket && event.isShiftPressed -> AppCommand.PreviousTab
        event.key == Key.PageDown && !event.isShiftPressed -> AppCommand.NextTab
        event.key == Key.PageUp && !event.isShiftPressed -> AppCommand.PreviousTab
        else -> null
    }

    /** Ctrl+Tab and Ctrl+Shift+Tab switch tabs on every platform (a browser habit), also on a Mac. */
    private fun tabSwitch(event: KeyEvent): AppCommand? = when {
        event.key == Key.Tab && event.isCtrlPressed && !event.isMetaPressed ->
            if (event.isShiftPressed) AppCommand.PreviousTab else AppCommand.NextTab

        else -> null
    }

    private fun chordCommand(event: KeyEvent): AppCommand? = when {
        event.key == Key.Backspace -> AppCommand.DeleteNote.takeIf { event.isShiftPressed }
        tabChord(event) != null -> tabChord(event)
        event.isShiftPressed -> null
        else -> LETTER_COMMANDS.entries.firstOrNull { it.value.key == event.key }?.key
    }

    private class Letter(val key: Key, val label: String)

    private companion object {
        /** The commands that are the command key plus a letter: the one table both the keys and the labels read. */
        val LETTER_COMMANDS: Map<AppCommand, Letter> = mapOf(
            AppCommand.NewNote to Letter(Key.N, "N"),
            AppCommand.FindInNote to Letter(Key.F, "F"),
            AppCommand.LockApp to Letter(Key.L, "L"),
            AppCommand.CloseTab to Letter(Key.W, "W"),
        )
    }
}

/** The shortcut map of the window: the host provides its own, so every label under it reads the same keys. */
val LocalEnclyShortcuts = staticCompositionLocalOf { EnclyShortcuts() }

/**
 * Handles the app's shortcuts for everything below this modifier: [onCommand] gets the command and
 * returns whether it did something (the key press is then consumed). Chords are seen before the focused
 * field, so ⌘N works while typing; Esc is seen only after the focused element passed on it, so a dialog,
 * a menu or a field that uses Esc keeps it. Key events reach it only while something below holds focus:
 * see [EnclyShortcutHost], which also makes sure something does.
 */
fun Modifier.enclyShortcuts(
    shortcuts: EnclyShortcuts = EnclyShortcuts(),
    onCommand: (AppCommand) -> Boolean,
): Modifier = this
    .onPreviewKeyEvent { event ->
        shortcuts.commandFor(event)?.takeIf { it != AppCommand.Dismiss }?.let(onCommand) ?: false
    }
    .onKeyEvent { event ->
        shortcuts.commandFor(event)?.takeIf { it == AppCommand.Dismiss }?.let(onCommand) ?: false
    }

/**
 * The app root's key handler: a [Box] around [content] that handles [enclyShortcuts] and holds the
 * keyboard focus until something inside asks for it, so a shortcut works right after the window opens
 * and again after a click on a part of the screen that takes no focus. Wire it once, around the whole
 * app (`EnclyApp`'s content), and run the [AppCommand]s in [onCommand].
 */
@Composable
fun EnclyShortcutHost(
    onCommand: (AppCommand) -> Boolean,
    modifier: Modifier = Modifier,
    shortcuts: EnclyShortcuts = remember { EnclyShortcuts() },
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier = modifier
            .enclyShortcuts(shortcuts, onCommand)
            .focusRequester(focus)
            .focusable(),
    ) {
        CompositionLocalProvider(LocalEnclyShortcuts provides shortcuts, content = content)
    }
}
