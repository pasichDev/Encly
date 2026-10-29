package com.pasich.encly.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.storage.FileKeyValueStore
import com.pasich.encly.desktop.window.DesktopShortcuts
import com.pasich.encly.desktop.window.SavedWindow
import com.pasich.encly.desktop.window.WindowBounds
import com.pasich.encly.desktop.window.WindowStateStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowStateStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val screen = WindowBounds(0, 0, 1920, 1080)
    private val second = WindowBounds(1920, 0, 1280, 1024)

    private fun flags() = FileKeyValueStore(temp.root.toPath().resolve("flags.properties"), Executor { it.run() })

    @Test
    fun `a first run has nothing saved`() {
        assertNull(WindowStateStore(flags()).load(listOf(screen)))
    }

    @Test
    fun `size and position survive a restart`() {
        WindowStateStore(flags()).save(WindowBounds(100, 80, 1000, 700), maximised = false)
        val restored = WindowStateStore(flags()).load(listOf(screen))
        assertEquals(SavedWindow(1000, 700, 100, 80, maximised = false), restored)
    }

    @Test
    fun `maximised keeps the size from before it`() {
        val store = WindowStateStore(flags())
        store.save(WindowBounds(10, 10, 900, 600), maximised = false)
        store.save(WindowBounds(0, 0, 1920, 1080), maximised = true)
        val restored = WindowStateStore(flags()).load(listOf(screen))
        assertEquals(SavedWindow(900, 600, 10, 10, maximised = true), restored)
        // Un-maximising after maximising only (no bounds) keeps the flag and the old size.
        store.save(null, maximised = false)
        assertFalse(WindowStateStore(flags()).load(listOf(screen))!!.maximised)
    }

    @Test
    fun `a size below the minimum or above the screen is clamped`() {
        val store = WindowStateStore(flags())
        store.save(WindowBounds(0, 0, 100, 100), maximised = false)
        val small = store.load(listOf(screen))!!
        assertEquals(WindowStateStore.MIN_WIDTH, small.width)
        assertEquals(WindowStateStore.MIN_HEIGHT, small.height)
        store.save(WindowBounds(0, 0, 9000, 9000), maximised = false)
        val big = store.load(listOf(screen))!!
        assertEquals(1920, big.width)
        assertEquals(1080, big.height)
    }

    @Test
    fun `a window on a monitor that is gone is moved back`() {
        val store = WindowStateStore(flags())
        store.save(WindowBounds(2500, 100, 1000, 700), maximised = false)
        assertEquals(2500, store.load(listOf(screen, second))!!.x)
        val withoutSecond = store.load(listOf(screen))!!
        assertNull(withoutSecond.x)
        assertNull(withoutSecond.y)
        assertEquals(1000, withoutSecond.width)
    }

    @Test
    fun `a barely visible window is moved back and no screens means no check`() {
        val store = WindowStateStore(flags())
        store.save(WindowBounds(1900, 1060, 1000, 700), maximised = false)
        assertNull(store.load(listOf(screen))!!.x)
        assertEquals(1900, store.load(emptyList())!!.x)
    }

    @Test
    fun `bounds know how much they overlap`() {
        val a = WindowBounds(0, 0, 10, 10)
        assertEquals(25L, a.overlapArea(WindowBounds(5, 5, 10, 10)))
        assertEquals(0L, a.overlapArea(WindowBounds(10, 0, 5, 5)))
        assertEquals(10, a.right)
        assertEquals(10, a.bottom)
    }
}

class DesktopCommandsAndShortcutsTest {
    @Test
    fun `the primary modifier is Cmd on macOS and Ctrl elsewhere`() {
        assertEquals(KeyShortcut(Key.N, ctrl = false, meta = true), DesktopShortcuts.primary(DesktopOs.MACOS, Key.N))
        for (os in listOf(DesktopOs.WINDOWS, DesktopOs.LINUX)) {
            assertEquals(
                KeyShortcut(Key.N, ctrl = true, meta = false, shift = true),
                DesktopShortcuts.primary(os, Key.N, shift = true),
            )
        }
    }

    @Test
    fun `every menu command has its own shortcut`() {
        for (os in DesktopOs.entries) {
            val shortcuts = listOf(
                DesktopShortcuts.newNote(os),
                DesktopShortcuts.importNotes(os),
                DesktopShortcuts.backup(os),
                DesktopShortcuts.lock(os),
                DesktopShortcuts.settings(os),
                DesktopShortcuts.minimise(os),
                DesktopShortcuts.quit(os),
            )
            assertEquals(shortcuts.size, shortcuts.toSet().size)
        }
        assertEquals(DesktopShortcuts.primary(DesktopOs.LINUX, Key.L), DesktopShortcuts.lock(DesktopOs.LINUX))
    }

    @Test
    fun `commands ask for the screens and the session`() {
        val rig = CommandsRig()
        with(rig.commands) {
            newNote()
            importNotes()
            backup()
            settings()
            about()
            licences()
            faq()
        }
        assertEquals(
            listOf(
                "EditNoteRoute/-1",
                "ImportNotesRoute",
                "BackupRoute",
                "SettingsRoute",
                "AboutRoute",
                "LicensesRoute",
                "FaqRoute",
            ),
            rig.requested(),
        )
    }

    @Test
    fun `language theme minimise and quit reach their owners`() {
        val rig = CommandsRig()
        try {
            rig.commands.setLanguage(AppLanguage.GERMAN)
            assertEquals(AppLanguage.GERMAN, rig.locales.current())
            assertEquals("de", java.util.Locale.getDefault().language)
            assertEquals(1, rig.locales.revision.value)
            rig.commands.setLanguage(AppLanguage.SYSTEM)
            assertEquals(AppLanguage.SYSTEM, rig.locales.current())
            assertEquals(rig.originalLocale, java.util.Locale.getDefault())
        } finally {
            rig.restoreLocale()
        }
        rig.commands.setTheme(com.pasich.encly.domain.model.ThemeType.DARK)
        rig.commands.minimise()
        rig.commands.quit()
        assertEquals(listOf("minimise", "quit"), rig.events)
        assertEquals(listOf(com.pasich.encly.domain.model.ThemeType.DARK), rig.themes)
    }

    @Test
    fun `lock now closes only an open vault`() {
        val rig = CommandsRig()
        rig.commands.lockNow()
        assertEquals(1, rig.lockNowCalls)
    }
}
