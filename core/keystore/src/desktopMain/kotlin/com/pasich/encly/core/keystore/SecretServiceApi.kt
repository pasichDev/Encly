@file:Suppress("MagicNumber") // The @Position indexes of the D-Bus structs.

package com.pasich.encly.core.keystore

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

// The part of the freedesktop Secret Service API (https://specifications.freedesktop.org/secret-service-spec/)
// that LinuxSecretServiceStore uses, as dbus-java interfaces. The names of the methods and fields are the
// D-Bus names and are read by reflection; the structs and tuples are marshalled in @Position order.

internal const val SECRETS_BUS_NAME = "org.freedesktop.secrets"
internal const val SECRETS_SERVICE_PATH = "/org/freedesktop/secrets"

/** "No object", as the spec writes it: a prompt path of "/" means that no prompt is needed. */
internal const val NO_OBJECT_PATH = "/"

@DBusInterfaceName("org.freedesktop.Secret.Service")
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal interface SecretServiceApi : DBusInterface {
    fun OpenSession(algorithm: String, input: Variant<*>): OpenSessionResult

    @JvmSuppressWildcards
    fun SearchItems(attributes: Map<String, String>): SearchItemsResult

    @JvmSuppressWildcards
    fun Unlock(objects: List<DBusPath>): UnlockResult

    fun ReadAlias(name: String): DBusPath
}

@DBusInterfaceName("org.freedesktop.Secret.Collection")
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal interface SecretCollectionApi : DBusInterface {
    @JvmSuppressWildcards
    fun CreateItem(properties: Map<String, Variant<*>>, secret: SecretStruct, replace: Boolean): CreateItemResult
}

@DBusInterfaceName("org.freedesktop.Secret.Item")
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal interface SecretItemApi : DBusInterface {
    fun GetSecret(session: DBusPath): SecretStruct

    fun Delete(): DBusPath
}

@DBusInterfaceName("org.freedesktop.Secret.Session")
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
internal interface SecretSessionApi : DBusInterface {
    fun Close()
}

/** `(oayays)`: the session the secret belongs to, the algorithm's parameters, the value, its content type. */
internal class SecretStruct(
    @field:Position(0) @JvmField val session: DBusPath,
    @field:Position(1) @JvmField val parameters: ByteArray,
    @field:Position(2) @JvmField val value: ByteArray,
    @field:Position(3) @JvmField val contentType: String,
) : Struct()

/** `OpenSession` returns `(v output, o result)`. */
internal class OpenSessionResult(
    @field:Position(0) @JvmField val output: Variant<*>,
    @field:Position(1) @JvmField val session: DBusPath,
) : Tuple()

/** `SearchItems` returns `(ao unlocked, ao locked)`. */
internal class SearchItemsResult(
    @field:Position(0) @JvmField val unlocked: List<@JvmSuppressWildcards DBusPath>,
    @field:Position(1) @JvmField val locked: List<@JvmSuppressWildcards DBusPath>,
) : Tuple()

/** `Unlock` returns `(ao unlocked, o prompt)`. */
internal class UnlockResult(
    @field:Position(0) @JvmField val unlocked: List<@JvmSuppressWildcards DBusPath>,
    @field:Position(1) @JvmField val prompt: DBusPath,
) : Tuple()

/** `CreateItem` returns `(o item, o prompt)`. */
internal class CreateItemResult(
    @field:Position(0) @JvmField val item: DBusPath,
    @field:Position(1) @JvmField val prompt: DBusPath,
) : Tuple()
