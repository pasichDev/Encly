package com.pasich.encly.desktop

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.desktop.platform.CaptureExclusion
import com.pasich.encly.desktop.platform.DesktopAppLocaleController
import com.pasich.encly.desktop.platform.DesktopEnvironment
import com.pasich.encly.desktop.platform.FileDialogs
import com.pasich.encly.desktop.platform.LinkOpener
import com.pasich.encly.desktop.security.BootClockSource
import com.pasich.encly.desktop.security.OsLockSignals
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.window.DefaultDesktopCommands
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.platform.NavigationRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Window
import java.awt.datatransfer.Clipboard
import java.net.URI
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A [PinHardwareFactor] in memory: the same HMAC contract as the OS secret store, with no OS involved. */
class FakePinFactor : PinHardwareFactor {
    private val keys = mutableMapOf<PinKeySlot, ByteArray>()

    override fun ensureKey(slot: PinKeySlot): Boolean {
        if (keys.containsKey(slot)) return false
        reset(slot)
        return true
    }

    override fun reset(slot: PinKeySlot) {
        keys[slot] = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)
    }

    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        val key = keys[slot] ?: throw com.pasich.encly.core.security.PinFactorException(lost = true)
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }

    override fun delete(slot: PinKeySlot) {
        keys.remove(slot)
    }

    private companion object {
        const val KEY_SIZE = 32
    }
}

/** A [KeyValueStore] in memory. */
class MemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String, default: String?): String? = values[key] ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key]?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long): Long = values[key]?.toLongOrNull() ?: default

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        object : KeyValueStore.Editor {
            override fun putString(key: String, value: String) {
                values[key] = value
            }

            override fun putBoolean(key: String, value: Boolean) {
                values[key] = value.toString()
            }

            override fun putLong(key: String, value: Long) {
                values[key] = value.toString()
            }

            override fun remove(key: String) {
                values.remove(key)
            }

            override fun clear() {
                values.clear()
            }
        }.block()
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        edit(block)
        return true
    }
}

/** The pieces `DefaultDesktopCommands` needs, with what they were asked recorded. */
class CommandsRig {
    val navigation = NavigationRequests()
    val originalLocale: Locale = Locale.getDefault()
    val locales = DesktopAppLocaleController(MemoryKeyValueStore(), originalLocale)
    val events = mutableListOf<String>()
    val themes = mutableListOf<ThemeType>()
    var lockNowCalls = 0
    val commands = DefaultDesktopCommands(
        navigation = navigation,
        lockSession = { lockNowCalls++ },
        locales = locales,
        saveThemeType = { themes += it },
        scope = CoroutineScope(Dispatchers.Unconfined),
        onMinimise = { events += "minimise" },
        onQuit = { events += "quit" },
        openWindow = { events += "window" },
    )

    /** The screens asked for so far, in order. */
    fun requested(): List<String> = drainRoutes(navigation)

    fun restoreLocale() = Locale.setDefault(originalLocale)
}

/** The routes requested through [navigation] and not yet taken, in order. */
fun drainRoutes(navigation: NavigationRequests): List<String> {
    val out = mutableListOf<String>()
    runBlocking { withTimeoutOrNull(WAIT_MS) { navigation.routes.collect { out += it } } }
    return out
}

/** The commands requested through [navigation] and not yet taken, in order. */
fun drainCommands(navigation: NavigationRequests): List<com.pasich.encly.presentation.designsystem.AppCommand> {
    val out = mutableListOf<com.pasich.encly.presentation.designsystem.AppCommand>()
    runBlocking { withTimeoutOrNull(WAIT_MS) { navigation.commands.collect { out += it } } }
    return out
}

private const val WAIT_MS = 200L

/** A timer that runs nothing by itself: the test fires what was scheduled. */
class ManualTimer : MainThreadTimer {
    class Task(val delayMs: Long, val action: () -> Unit) {
        var cancelled = false
        var fired = false
    }

    val tasks = mutableListOf<Task>()

    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val task = Task(delayMs, action)
        tasks += task
        val cancel: () -> Unit = { task.cancelled = true }
        return cancel
    }

    /** The tasks that are still waiting. */
    val pending: List<Task> get() = tasks.filter { !it.cancelled && !it.fired }

    /** Runs every waiting task once (the tasks they schedule wait for the next call). */
    fun fireAll() {
        pending.forEach {
            it.fired = true
            it.action()
        }
    }
}

class FakeBootClock(var uptime: Long? = 1_000L, var id: String? = "boot-1") : BootClockSource {
    override fun uptimeMillis(): Long? = uptime

    override fun bootId(): String? = id
}

class RecordingCapture : CaptureExclusion {
    val windows = mutableListOf<Window>()
    override val supported: Boolean = true

    override fun exclude(window: Window): Boolean {
        windows += window
        return true
    }
}

class RecordingLinks(var accepts: Boolean = true) : LinkOpener {
    val opened = mutableListOf<URI>()

    override fun open(uri: URI): Boolean {
        opened += uri
        return accepts
    }
}

/** Dialogs that answer at once with what the test set up. */
class FakeFileDialogs : FileDialogs {
    var saveAnswer: Path? = null
    var openAnswer: List<Path> = emptyList()
    var folderAnswer: Path? = null
    val calls = mutableListOf<String>()

    override fun save(suggestedName: String, onResult: (Path?) -> Unit) {
        calls += "save:$suggestedName"
        onResult(saveAnswer)
    }

    override fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit) {
        calls += "open:$multiple:${extensions.sorted().joinToString(",")}"
        onResult(openAnswer)
    }

    override fun folder(onResult: (Path?) -> Unit) {
        calls += "folder"
        onResult(folderAnswer)
    }
}

/** An environment in [directory] with no OS access at all: nothing touches the real data folder or Keychain. */
fun testEnvironment(
    directory: Path,
    timer: ManualTimer = ManualTimer(),
    clipboard: Clipboard = Clipboard("test"),
    dialogs: FileDialogs = FakeFileDialogs(),
    links: LinkOpener = RecordingLinks(),
    capture: CaptureExclusion = RecordingCapture(),
    lockSignals: OsLockSignals = OsLockSignals.None,
    bootClock: BootClockSource = FakeBootClock(),
): DesktopEnvironment = DesktopEnvironment(
    os = DesktopOs.LINUX,
    dataDirectory = directory,
    pinFactor = FakePinFactor(),
    lockSignals = lockSignals,
    bootClock = bootClock,
    captureExclusion = capture,
    fileDialogs = dialogs,
    linkOpener = links,
    systemClipboard = { clipboard },
    timer = timer,
)
