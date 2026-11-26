package com.pasich.encly.desktop.platform

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Tries each of [openers] in order; the first that takes the address wins, and one that throws is skipped. */
class FirstLinkOpener(private val openers: List<LinkOpener>) : LinkOpener {
    @Suppress("TooGenericExceptionCaught") // An opener's own failure is "no", whatever it throws.
    override fun open(uri: URI): Boolean = openers.any { opener ->
        try {
            opener.open(uri)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * `xdg-open` for a Linux outside a sandbox that has no portal. [start] runs a command and says whether it began (the
 * default starts the process and reaps it on a thread of its own, never on the UI thread).
 */
class XdgOpenLinkOpener(private val start: (List<String>) -> Boolean = ::startDetached) : LinkOpener {
    override fun open(uri: URI): Boolean = start(listOf("xdg-open", uri.toString()))
}

/** Starts [command] and reaps it later; false when it cannot be started (no such program, no permission). */
@Suppress("TooGenericExceptionCaught") // Whatever the start throws is "cannot".
internal fun startDetached(command: List<String>): Boolean = try {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    Thread({ runCatching { process.waitFor() } }, "encly-xdg-open").apply { isDaemon = true }.start()
    true
} catch (_: Exception) {
    false
}

/**
 * Whether this process runs in a sandbox that has no GTK and no `xdg-open` of the host: a Flatpak (`/.flatpak-info`,
 * `FLATPAK_ID`) or a Snap (`SNAP`). Only the portal can open a link there.
 */
fun isSandboxed(
    environment: (String) -> String? = System::getenv,
    exists: (Path) -> Boolean = Files::exists,
): Boolean = exists(Path.of("/.flatpak-info")) ||
    environment("FLATPAK_ID") != null ||
    environment("SNAP") != null

/**
 * The link opener of a Linux desktop: the portal ([portal]); outside a sandbox, and only then, `xdg-open` and AWT's
 * `Desktop.browse` after it, for a session with no portal. Inside a sandbox those two cannot work, and a link nobody
 * can open is told as "nothing can open this link".
 */
fun linuxLinkOpener(
    portal: LinkOpener,
    sandboxed: Boolean = isSandboxed(),
    xdgOpen: LinkOpener = XdgOpenLinkOpener(),
    awt: LinkOpener = AwtLinkOpener,
): LinkOpener = FirstLinkOpener(if (sandboxed) listOf(portal) else listOf(portal, xdgOpen, awt))
