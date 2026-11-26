@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught")

package com.pasich.encly.desktop.nativeos

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.TimeUnit

// A start that fails with no display cannot show a dialog: the message goes to stderr, and, best effort, to the user's
// desktop as a notification, which needs no display of our own: the portal's `Notification` first (it works inside a
// Flatpak sandbox), then `notify-send`. D-Bus and process glue, excluded from the coverage gate.

@DBusInterfaceName("org.freedesktop.portal.Notification")
internal interface PortalNotification : DBusInterface {
    /** `AddNotification(s id, a{sv} notification)`: the `title` and `body` keys are all this uses. */
    @JvmSuppressWildcards
    fun AddNotification(id: String, notification: Map<String, Variant<*>>)
}

/** Tells the user, without a display, why Encly did not start. Never throws; gives up after a few seconds. */
object NoDisplayNotice {
    private const val WAIT_SECONDS = 3L

    fun send(title: String, body: String) {
        if (viaPortal(title, body)) return
        viaNotifySend(title, body)
    }

    private fun viaPortal(title: String, body: String): Boolean = try {
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            connection.getRemoteObject(
                "org.freedesktop.portal.Desktop",
                "/org/freedesktop/portal/desktop",
                PortalNotification::class.java,
                true,
            ).AddNotification("encly-start-failed", mapOf("title" to Variant(title), "body" to Variant(body)))
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun viaNotifySend(title: String, body: String) {
        try {
            val process = ProcessBuilder("notify-send", "--app-name=Encly", title, body)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (_: Exception) {
            // No notify-send, or no bus: the message on stderr is all there is.
        }
    }
}
