@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught", "ReturnCount")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.platform.FileDialogs
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

// The freedesktop desktop portal's file chooser (xdg-desktop-portal, org.freedesktop.portal.FileChooser) over
// the local session bus: the dialog of the user's own desktop (GNOME, KDE) instead of AWT's Motif-style one,
// and the only way a sandboxed (Flatpak) app can open files. D-Bus glue: it only runs against a real desktop
// and is excluded from the coverage gate. Any failure before the dialog is shown falls back to [fallback].

@DBusInterfaceName("org.freedesktop.portal.FileChooser")
internal interface PortalFileChooser : DBusInterface {
    @JvmSuppressWildcards
    fun OpenFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath

    @JvmSuppressWildcards
    fun SaveFile(parentWindow: String, title: String, options: Map<String, Variant<*>>): DBusPath
}

/** A [FileDialogs] on the portal that uses [fallback] when the portal is not there or fails to start. */
class PortalFileDialogs(private val fallback: FileDialogs) : FileDialogs {
    private class Unavailable : Exception()

    override fun save(suggestedName: String, onResult: (Path?) -> Unit) {
        val options = mutableMapOf<String, Variant<*>>("current_name" to Variant(suggestedName))
        request(
            method = { it.SaveFile("", "", options) },
            options = options,
            onUris = { paths -> onResult(paths.firstOrNull()) },
            fallback = { fallback.save(suggestedName, onResult) },
        )
    }

    override fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit) {
        val options = mutableMapOf<String, Variant<*>>("multiple" to Variant(multiple))
        request(
            method = { it.OpenFile("", "", options) },
            options = options,
            onUris = onResult,
            fallback = { fallback.open(multiple, extensions, onResult) },
        )
    }

    override fun folder(onResult: (Path?) -> Unit) {
        val options = mutableMapOf<String, Variant<*>>("directory" to Variant(true))
        request(
            method = { it.OpenFile("", "", options) },
            options = options,
            onUris = { paths -> onResult(paths.firstOrNull()) },
            fallback = { fallback.folder(onResult) },
        )
    }

    private fun request(
        method: (PortalFileChooser) -> DBusPath,
        options: MutableMap<String, Variant<*>>,
        onUris: (List<Path>) -> Unit,
        fallback: () -> Unit,
    ) {
        Thread({
            val outcome = try {
                Result.success(ask(method, options))
            } catch (e: Exception) {
                Result.failure(e)
            }
            SwingUtilities.invokeLater {
                outcome.fold(onUris) { fallback() }
            }
        }, "encly-portal-dialog").apply { isDaemon = true }.start()
    }

    /** Calls the portal and waits for the user's answer; throws if the portal is not available. */
    private fun ask(method: (PortalFileChooser) -> DBusPath, options: MutableMap<String, Variant<*>>): List<Path> =
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            val token = "encly" + java.lang.Long.toHexString(SecureRandom().nextLong())
            val sender = connection.uniqueName.removePrefix(":").replace('.', '_')
            val handle = "/org/freedesktop/portal/desktop/request/$sender/$token"
            val answer = CompletableFuture<List<Path>>()
            connection.addGenericSigHandler(
                DBusMatchRule("signal", "org.freedesktop.portal.Request", "Response", handle),
                DBusSigHandler<DBusSignal> { signal -> answer.complete(pathsOf(signal.parameters)) },
            )
            options["handle_token"] = Variant(token)
            val chooser = connection.getRemoteObject(
                "org.freedesktop.portal.Desktop",
                "/org/freedesktop/portal/desktop",
                PortalFileChooser::class.java,
                true,
            )
            try {
                method(chooser)
            } catch (e: Exception) {
                throw Unavailable().initCause(e)
            }
            answer.get()
        }

    /** `Response(u code, a{sv} results)`: code 0 is an answer, anything else a cancel; `uris` are file: URIs. */
    private fun pathsOf(parameters: Array<Any?>): List<Path> {
        val code = (parameters.getOrNull(0) as? UInt32)?.toLong() ?: return emptyList()
        if (code != 0L) return emptyList()
        val results = parameters.getOrNull(1) as? Map<*, *> ?: return emptyList()
        val uris = (results["uris"] as? Variant<*>)?.value as? List<*> ?: return emptyList()
        return uris.mapNotNull { (it as? String)?.let { text -> runCatching { Paths.get(URI(text)) }.getOrNull() } }
    }
}
