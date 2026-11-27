@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.platform.LinkOpener
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// The freedesktop desktop portal's `OpenURI` (xdg-desktop-portal, org.freedesktop.portal.OpenURI) over the local
// session bus: the default browser or mail application of the user's own desktop, and the only way a sandboxed
// (Flatpak) app can open a link: AWT's `Desktop.browse` needs GTK, which the freedesktop runtime does not have.
// D-Bus glue: it only runs against a real desktop and is excluded from the coverage gate.

@DBusInterfaceName("org.freedesktop.portal.OpenURI")
internal interface PortalOpenUri : DBusInterface {
    /** `OpenURI(s parent_window, s uri, a{sv} options) -> o handle`; an empty parent window is allowed. */
    @JvmSuppressWildcards
    fun OpenURI(parentWindow: String, uri: String, options: Map<String, Variant<*>>): DBusPath
}

/** A [LinkOpener] on the portal: true once the portal took the request, false when it is not there or refuses. */
class PortalLinkOpener : LinkOpener {
    override fun open(uri: URI): Boolean {
        // The portal answers with a handle at once, but connecting and the first call can stall on a broken bus.
        val asked = CompletableFuture.supplyAsync { ask(uri) }
        return try {
            asked.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: Exception) {
            asked.cancel(true)
            false
        }
    }

    private fun ask(uri: URI): Boolean = try {
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            connection.getRemoteObject(PORTAL_BUS, PORTAL_PATH, PortalOpenUri::class.java, true)
                .OpenURI("", uri.toString(), emptyMap())
        }
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val PORTAL_BUS = "org.freedesktop.portal.Desktop"
        const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        const val CALL_TIMEOUT_SECONDS = 5L
    }
}
