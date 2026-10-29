package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.Variant
import java.security.SecureRandom

/**
 * Linux: the freedesktop Secret Service (`org.freedesktop.secrets`, served by GNOME Keyring, KWallet's
 * Secret Service bridge, KeePassXC and others) over the D-Bus session bus. The secret is an item of the
 * default collection (the alias `default`, which is the `login` keyring on GNOME) with the attributes
 * `application = com.pasich.encly`, `purpose = pin-factor-v1` (`pin-factor-v1-b` for slot B) and `vault` = the
 * absolute data directory of the install. The keyring is one per OS user while a user can have several
 * installs with their own vaults (a Flatpak keeps its data under `~/.var/app`, a `.deb` under `~/.local/share`);
 * without [vault] they would share one secret, and a PIN change or a wipe in one would cut the other off its PIN.
 *
 * The D-Bus client is dbus-java (pure JVM, over the JDK's own Unix domain sockets), chosen over JNA to
 * libsecret because it needs no native library on the system and no GLib structures through JNA. The session
 * uses the `plain` transfer algorithm: the secret crosses the session bus, a socket that only the same user
 * can open, unencrypted; the encrypted `dh-ietf1024-sha256-aes128-cbc-pkcs7` algorithm is not implemented.
 *
 * There is no file fallback. Without a session bus or a running Secret Service the operations fail with a
 * [SecretStoreException] whose cause is a [SecretStoreUnavailableException]. A locked collection is not
 * unlocked through the user's password prompt (an unlock that needs a prompt fails the same way); the login
 * keyring is normally unlocked by the session login.
 */
@Suppress("TooGenericExceptionCaught") // dbus-java reports remote errors as various runtime exceptions.
internal class LinuxSecretServiceStore(
    private val vault: String,
    private val application: String = APPLICATION_ID,
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    override fun read(slot: PinKeySlot): ByteArray? = session { connection, service, sessionPath ->
        val items = findItems(connection, service, slot)
        val item = items.firstOrNull() ?: return@session null
        val secret = item.GetSecret(sessionPath)
        SensitiveDataCleaner.clear(secret.parameters)
        secret.value
    }

    override fun create(slot: PinKeySlot): ByteArray {
        val secret = ByteArray(SecretStore.SECRET_SIZE).also(random::nextBytes)
        val sent = secret.copyOf()
        try {
            session { connection, service, sessionPath ->
                val collectionPath = service.ReadAlias(DEFAULT_ALIAS)
                if (collectionPath.path == NO_OBJECT_PATH) {
                    throw unavailable("The Secret Service has no default collection")
                }
                requireNoPrompt(service.Unlock(listOf(collectionPath)).prompt)
                val collection = connection.getRemoteObject(
                    SECRETS_BUS_NAME,
                    collectionPath.path,
                    SecretCollectionApi::class.java,
                    true,
                )
                val properties = mapOf<String, Variant<*>>(
                    ITEM_LABEL to Variant(LABEL),
                    ITEM_ATTRIBUTES to Variant(attributesOf(slot), "a{ss}"),
                )
                val struct = SecretStruct(sessionPath, ByteArray(0), sent, CONTENT_TYPE)
                requireNoPrompt(collection.CreateItem(properties, struct, true).prompt)
            }
            return secret
        } catch (e: Exception) {
            SensitiveDataCleaner.clear(secret)
            throw e
        } finally {
            SensitiveDataCleaner.clear(sent)
        }
    }

    override fun delete(slot: PinKeySlot) {
        session { connection, service, _ ->
            findItems(connection, service, slot).forEach { requireNoPrompt(it.Delete()) }
        }
    }

    /** The items with the slot's attributes, their collections unlocked. */
    private fun findItems(
        connection: DBusConnection,
        service: SecretServiceApi,
        slot: PinKeySlot,
    ): List<SecretItemApi> {
        val found = service.SearchItems(attributesOf(slot))
        if (found.locked.isNotEmpty()) requireNoPrompt(service.Unlock(found.locked).prompt)
        return (found.unlocked + found.locked).map {
            connection.getRemoteObject(SECRETS_BUS_NAME, it.path, SecretItemApi::class.java, true)
        }
    }

    /** Opens a bus connection and a `plain` Secret Service session, runs [block], and closes both. */
    private fun <T> session(block: (DBusConnection, SecretServiceApi, DBusPath) -> T): T {
        try {
            DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
                val service = connection.getRemoteObject(
                    SECRETS_BUS_NAME,
                    SECRETS_SERVICE_PATH,
                    SecretServiceApi::class.java,
                    true,
                )
                val opened = service.OpenSession(PLAIN_ALGORITHM, Variant(""))
                try {
                    return block(connection, service, opened.session)
                } finally {
                    closeQuietly(connection, opened.session)
                }
            }
        } catch (e: SecretStoreException) {
            throw e
        } catch (e: Exception) {
            throw unavailable("The Secret Service could not be used: ${e.javaClass.simpleName}", e)
        }
    }

    private fun closeQuietly(connection: DBusConnection, session: DBusPath) {
        try {
            connection.getRemoteObject(SECRETS_BUS_NAME, session.path, SecretSessionApi::class.java, true).Close()
        } catch (_: Exception) {
            // The session ends with the connection anyway.
        }
    }

    private fun requireNoPrompt(prompt: DBusPath) {
        if (prompt.path != NO_OBJECT_PATH) {
            throw unavailable("The Secret Service needs the user to unlock the keyring first")
        }
    }

    private fun unavailable(message: String, cause: Throwable? = null) = SecretStoreException(
        lost = false,
        message = message,
        cause = SecretStoreUnavailableException(message, cause),
    )

    private fun attributesOf(slot: PinKeySlot): Map<String, String> =
        mapOf(ATTRIBUTE_APPLICATION to application, ATTRIBUTE_PURPOSE to slot.entryName(), ATTRIBUTE_VAULT to vault)

    private companion object {
        const val DEFAULT_ALIAS = "default"
        const val PLAIN_ALGORITHM = "plain"
        const val CONTENT_TYPE = "application/octet-stream"
        const val LABEL = "Encly PIN factor"
        const val ITEM_LABEL = "org.freedesktop.Secret.Item.Label"
        const val ITEM_ATTRIBUTES = "org.freedesktop.Secret.Item.Attributes"
        const val ATTRIBUTE_APPLICATION = "application"
        const val ATTRIBUTE_PURPOSE = "purpose"
        const val ATTRIBUTE_VAULT = "vault"
    }
}
