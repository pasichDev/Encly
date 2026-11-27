package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.NoReply
import org.freedesktop.dbus.errors.ServiceUnknown
import org.freedesktop.dbus.errors.Timeout
import org.freedesktop.dbus.exceptions.DBusConnectionException
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.security.GeneralSecurityException
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
 * uses the encrypted `dh-ietf1024-sha256-aes128-cbc-pkcs7` transfer algorithm (see [SecretServiceDh]), so the
 * secret does not cross the session bus in the clear, where anything that can read the bus (a debugging tool,
 * `dbus-monitor` run as the same user) could see it. There is no fallback to `plain`: a service that cannot do
 * the exchange is reported as unavailable rather than silently asked for less protection.
 *
 * There is no file fallback. Without a session bus or a running Secret Service the operations fail with a
 * [SecretStoreException] whose cause is a [SecretStoreUnavailableException] and whose problem says which. A locked
 * collection is unlocked, and a missing default collection made, through the service's own prompt (see
 * [KeyringPrompts]): the user answers it in the service's dialog, with the wait bounded and ended by a lock. An item
 * that is not found is `null`, and [missingMeansLost] is false: another provider may own the bus, the keyring that
 * holds the item may be closed, or the data directory may be reached by another path, so the caller must not read it
 * as gone.
 */
@Suppress("TooGenericExceptionCaught") // dbus-java reports remote errors as various runtime exceptions.
internal class LinuxSecretServiceStore(
    private val vault: String,
    private val application: String = APPLICATION_ID,
    private val random: SecureRandom = SecureRandom(),
    private val prompts: KeyringPrompts = KeyringPrompts(),
) : SecretStore {
    override val missingMeansLost: Boolean get() = false

    override fun abandonPending() = prompts.abandonPending()

    override fun prepare() {
        session { connection, service, _ -> prompts.openDefaultCollection(DbusKeyring(connection, service), true) }
    }

    override fun read(slot: PinKeySlot): ByteArray? = session { connection, service, opened ->
        var items = findItems(connection, service, slot, prompt = true)
        if (items.isEmpty()) {
            // Nothing found. A locked default collection hides its items: it is unlocked first (with the service's
            // prompt), and only then is nothing "not found". A missing one is not found too: not made here.
            val bus = DbusKeyring(connection, service)
            if (prompts.openDefaultCollection(bus, createMissing = false) != null) {
                items = findItems(connection, service, slot, prompt = true)
            }
        }
        val item = items.firstOrNull() ?: return@session null
        val secret = item.GetSecret(opened.path)
        try {
            opened.cipher.decrypt(secret.parameters, secret.value)
        } catch (e: GeneralSecurityException) {
            throw unavailable("The Secret Service sent a secret that could not be decrypted", KeyStoreProblem.OTHER, e)
        } finally {
            SensitiveDataCleaner.clear(secret.value)
        }
    }

    override fun create(slot: PinKeySlot): ByteArray {
        val secret = ByteArray(SecretStore.SECRET_SIZE).also(random::nextBytes)
        val sent = secret.copyOf()
        try {
            session { connection, service, opened ->
                val bus = DbusKeyring(connection, service)
                // A missing default collection is made through the service (which prompts), a locked one unlocked.
                val collectionPath = prompts.openDefaultCollection(bus, createMissing = true)
                    ?: throw unavailable("The Secret Service has no default collection", KeyStoreProblem.NO_DEFAULT)
                val collection = connection.getRemoteObject(
                    SECRETS_BUS_NAME,
                    collectionPath,
                    SecretCollectionApi::class.java,
                    true,
                )
                val properties = mapOf<String, Variant<*>>(
                    ITEM_LABEL to Variant(LABEL),
                    ITEM_ATTRIBUTES to Variant(attributesOf(slot), "a{ss}"),
                )
                val sealed = opened.cipher.encrypt(sent, random)
                val struct = SecretStruct(opened.path, sealed.iv, sealed.encrypted, CONTENT_TYPE)
                prompts.complete(bus, collection.CreateItem(properties, struct, true).prompt.asPrompt())
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
            // No prompt: a startup clean-up must not ask the user to unlock a keyring; what cannot be deleted stays
            // (and a new vault replaces it, see DesktopPinFactor.reset).
            findItems(connection, service, slot, prompt = false).forEach { requireNoPrompt(it.Delete()) }
        }
    }

    /** The items with the slot's attributes, their collections unlocked. */
    private fun findItems(
        connection: DBusConnection,
        service: SecretServiceApi,
        slot: PinKeySlot,
        prompt: Boolean,
    ): List<SecretItemApi> {
        val found = service.SearchItems(attributesOf(slot))
        if (found.locked.isNotEmpty()) {
            val unlocking = service.Unlock(found.locked).prompt
            if (prompt) {
                prompts.complete(DbusKeyring(connection, service), unlocking.asPrompt())
            } else {
                requireNoPrompt(unlocking)
            }
        }
        return (found.unlocked + found.locked).map {
            connection.getRemoteObject(SECRETS_BUS_NAME, it.path, SecretItemApi::class.java, true)
        }
    }

    /**
     * An open Secret Service session: its object [path] and the [cipher] of the secrets that go through it. Closing it
     * ends the session on the service (the session ends with the connection anyway) and wipes the cipher's key.
     */
    private class OpenSession(
        val path: DBusPath,
        val cipher: SecretServiceCipher,
        private val connection: DBusConnection,
    ) : AutoCloseable {
        override fun close() {
            cipher.close()
            try {
                connection.getRemoteObject(SECRETS_BUS_NAME, path.path, SecretSessionApi::class.java, true).Close()
            } catch (_: Exception) {
                // The session ends with the connection anyway.
            }
        }
    }

    /** Opens a bus connection and an encrypted Secret Service session, runs [block], and closes both. */
    private fun <T> session(block: (DBusConnection, SecretServiceApi, OpenSession) -> T): T {
        try {
            DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
                val service = connection.getRemoteObject(
                    SECRETS_BUS_NAME,
                    SECRETS_SERVICE_PATH,
                    SecretServiceApi::class.java,
                    true,
                )
                val dh = SecretServiceDh.create(random)
                val opened = service.OpenSession(DH_ALGORITHM, Variant(dh.publicKey))
                val cipher = dh.cipher(serverPublicKeyOf(opened.output))
                return OpenSession(opened.session, cipher, connection).use { block(connection, service, it) }
            }
        } catch (e: SecretStoreException) {
            throw e
        } catch (e: Exception) {
            throw unavailable("The Secret Service could not be used: ${e.javaClass.simpleName}", problemOf(e), e)
        }
    }

    private fun requireNoPrompt(prompt: DBusPath) {
        if (prompt.path != NO_OBJECT_PATH) {
            throw unavailable("The Secret Service needs the user to unlock the keyring first", KeyStoreProblem.LOCKED)
        }
    }

    /** What [KeyringPrompts] needs of the Secret Service on [connection]. */
    private class DbusKeyring(private val connection: DBusConnection, private val service: SecretServiceApi) :
        KeyringBus {
        override fun defaultCollection(): String? =
            service.ReadAlias(DEFAULT_ALIAS).path.takeIf { it != NO_OBJECT_PATH }

        override fun isLocked(collection: String): Boolean {
            val properties = connection.getRemoteObject(
                SECRETS_BUS_NAME,
                collection,
                org.freedesktop.dbus.interfaces.Properties::class.java,
                true,
            )
            return properties.Get<Boolean>(COLLECTION_INTERFACE, "Locked")
        }

        override fun unlock(objects: List<String>): String? =
            service.Unlock(objects.map(::DBusPath)).prompt.path.takeIf { it != NO_OBJECT_PATH }

        override fun createDefaultCollection(): CreatedCollection {
            val properties = mapOf<String, Variant<*>>(COLLECTION_LABEL to Variant(COLLECTION_NAME))
            val created = service.CreateCollection(properties, DEFAULT_ALIAS)
            return CreatedCollection(
                collection = created.collection.path.takeIf { it != NO_OBJECT_PATH },
                prompt = created.prompt.path.takeIf { it != NO_OBJECT_PATH },
            )
        }

        override fun watchPrompt(prompt: String, completed: (PromptOutcome) -> Unit): AutoCloseable {
            val rule = DBusMatchRule("signal", PROMPT_INTERFACE, "Completed", prompt)
            val handler = DBusSigHandler<DBusSignal> { signal ->
                val parameters = signal.parameters
                val dismissed = parameters.getOrNull(0) as? Boolean ?: true
                completed(PromptOutcome(dismissed, pathOf((parameters.getOrNull(1) as? Variant<*>)?.value)))
            }
            connection.addGenericSigHandler(rule, handler)
            return AutoCloseable { runCatching { connection.removeGenericSigHandler(rule, handler) } }
        }

        override fun startPrompt(prompt: String) {
            promptObject(prompt).Prompt(PROMPT_WINDOW_ID)
        }

        override fun dismissPrompt(prompt: String) {
            runCatching { promptObject(prompt).Dismiss() }
        }

        private fun promptObject(prompt: String) =
            connection.getRemoteObject(SECRETS_BUS_NAME, prompt, SecretPromptApi::class.java, true)

        private fun pathOf(value: Any?): String? = when (value) {
            is DBusPath -> value.path
            is String -> value
            else -> null
        }?.takeIf { it != NO_OBJECT_PATH }
    }

    private fun attributesOf(slot: PinKeySlot): Map<String, String> =
        mapOf(ATTRIBUTE_APPLICATION to application, ATTRIBUTE_PURPOSE to slot.entryName(), ATTRIBUTE_VAULT to vault)

    private companion object {
        const val DEFAULT_ALIAS = "default"
        const val COLLECTION_LABEL = "org.freedesktop.Secret.Collection.Label"
        const val COLLECTION_NAME = "Encly"
        const val PROMPT_INTERFACE = "org.freedesktop.Secret.Prompt"

        // The spec allows an empty window id (the service shows its dialog on its own); an X11 id is not reachable
        // from the JVM without reaching into the AWT peers.
        const val PROMPT_WINDOW_ID = ""
        const val COLLECTION_INTERFACE = "org.freedesktop.Secret.Collection"
        const val CONTENT_TYPE = "application/octet-stream"
        const val LABEL = "Encly PIN factor"
        const val ITEM_LABEL = "org.freedesktop.Secret.Item.Label"
        const val ITEM_ATTRIBUTES = "org.freedesktop.Secret.Item.Attributes"
        const val ATTRIBUTE_APPLICATION = "application"
        const val ATTRIBUTE_PURPOSE = "purpose"
        const val ATTRIBUTE_VAULT = "vault"
    }
}

private fun DBusPath.asPrompt(): String? = path.takeIf { it != NO_OBJECT_PATH }

/** No bus, or nobody owning the Secret Service name, is "not running"; anything else is not told apart. */
private fun problemOf(e: Exception): KeyStoreProblem = when (e) {
    is DBusConnectionException, is ServiceUnknown, is NoReply, is Timeout -> KeyStoreProblem.NOT_RUNNING
    else -> KeyStoreProblem.OTHER
}
