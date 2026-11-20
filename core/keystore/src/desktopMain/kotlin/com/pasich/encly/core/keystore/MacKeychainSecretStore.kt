package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.security.SecureRandom

/**
 * macOS: a generic password in the user's login Keychain (service `com.pasich.encly`, account `pin-factor-v1`,
 * `pin-factor-v1-b` for slot B), written and read through Security.framework with CoreFoundation dictionaries.
 * The calls are made through JNA; the `security` command line tool is not used, so the secret never passes
 * through a process listing or a pipe.
 *
 * The item is created by the running Java launcher, which the Keychain then trusts to read it without a
 * prompt; another launcher (a different JDK, an unsigned build) gets the system's access prompt. A locked
 * Keychain, or a refused prompt, is a failure that is not "lost": it may pass.
 *
 * Secure Enclave keys are not used: they need a signed app with entitlements, which Encly does not have on the
 * desktop yet.
 */
internal class MacKeychainSecretStore(
    private val service: String = APPLICATION_ID,
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    private val cf: CoreFoundation by lazy { Native.load(CF_PATH, CoreFoundation::class.java) }
    private val sec: SecurityFramework by lazy { Native.load(SECURITY_PATH, SecurityFramework::class.java) }
    private val cfLib: NativeLibrary by lazy { NativeLibrary.getInstance(CF_PATH) }
    private val secLib: NativeLibrary by lazy { NativeLibrary.getInstance(SECURITY_PATH) }

    override fun read(slot: PinKeySlot): ByteArray? = withRefs { refs ->
        val query = baseQuery(refs, slot)
        cf.CFDictionarySetValue(query, secGlobal("kSecReturnData"), cfGlobal("kCFBooleanTrue"))
        cf.CFDictionarySetValue(query, secGlobal("kSecMatchLimit"), secGlobal("kSecMatchLimitOne"))
        val result = PointerByReference()
        val status = sec.SecItemCopyMatching(query, result)
        when (status) {
            ERR_SEC_SUCCESS -> {
                val data = result.value ?: throw failure("The Keychain returned no data", status)
                refs.add(data)
                val length = cf.CFDataGetLength(data).toInt()
                if (length == 0) null else cf.CFDataGetBytePtr(data).getByteArray(0, length)
            }

            ERR_SEC_ITEM_NOT_FOUND -> null

            else -> throw failure("The Keychain item could not be read", status)
        }
    }

    override fun create(slot: PinKeySlot): ByteArray {
        val secret = ByteArray(SecretStore.SECRET_SIZE).also(random::nextBytes)
        try {
            withRefs { refs ->
                val attributes = baseQuery(refs, slot)
                val data = cf.CFDataCreate(null, secret, secret.size.toLong())
                    ?: throw failure("CoreFoundation could not wrap the secret", 0)
                refs.add(data)
                cf.CFDictionarySetValue(attributes, secGlobal("kSecValueData"), data)
                cf.CFDictionarySetValue(attributes, secGlobal("kSecAttrLabel"), string(refs, LABEL))
                var status = sec.SecItemAdd(attributes, null)
                if (status == ERR_SEC_DUPLICATE_ITEM) {
                    delete(slot)
                    status = sec.SecItemAdd(attributes, null)
                }
                if (status != ERR_SEC_SUCCESS) throw failure("The Keychain item could not be stored", status)
            }
            return secret
        } catch (e: SecretStoreException) {
            SensitiveDataCleaner.clear(secret)
            throw e
        }
    }

    override fun delete(slot: PinKeySlot) = withRefs { refs ->
        val status = sec.SecItemDelete(baseQuery(refs, slot))
        if (status != ERR_SEC_SUCCESS && status != ERR_SEC_ITEM_NOT_FOUND) {
            throw failure("The Keychain item could not be deleted", status)
        }
    }

    /** class = generic password, service, account: what identifies the item. */
    private fun baseQuery(refs: MutableList<Pointer>, slot: PinKeySlot): Pointer {
        val dictionary = cf.CFDictionaryCreateMutable(
            null,
            0,
            cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
            cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
        ) ?: throw failure("CoreFoundation could not create a dictionary", 0)
        refs.add(dictionary)
        cf.CFDictionarySetValue(dictionary, secGlobal("kSecClass"), secGlobal("kSecClassGenericPassword"))
        cf.CFDictionarySetValue(dictionary, secGlobal("kSecAttrService"), string(refs, service))
        cf.CFDictionarySetValue(dictionary, secGlobal("kSecAttrAccount"), string(refs, slot.entryName()))
        return dictionary
    }

    private fun string(refs: MutableList<Pointer>, value: String): Pointer {
        val string = cf.CFStringCreateWithCString(null, value, K_CF_STRING_ENCODING_UTF8)
            ?: throw failure("CoreFoundation could not create a string", 0)
        refs.add(string)
        return string
    }

    /** The value of a `CFTypeRef` constant exported by CoreFoundation. */
    private fun cfGlobal(name: String): Pointer = cfLib.getGlobalVariableAddress(name).getPointer(0)

    /** The value of a `CFTypeRef` constant exported by Security.framework. */
    private fun secGlobal(name: String): Pointer = secLib.getGlobalVariableAddress(name).getPointer(0)

    /** Runs [block] and releases every CoreFoundation object it registered, whatever happens. */
    private fun <T> withRefs(block: (MutableList<Pointer>) -> T): T {
        val refs = mutableListOf<Pointer>()
        try {
            return block(refs)
        } finally {
            refs.forEach(cf::CFRelease)
        }
    }

    private fun failure(message: String, status: Int) =
        SecretStoreException(lost = false, message = if (status == 0) message else "$message (OSStatus $status)")

    @Suppress("FunctionNaming", "ktlint:standard:function-naming") // Names are the C symbols.
    private interface CoreFoundation : Library {
        fun CFRelease(cf: Pointer)

        fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer?

        fun CFDataCreate(alloc: Pointer?, bytes: ByteArray, length: Long): Pointer?

        fun CFDataGetLength(data: Pointer): Long

        fun CFDataGetBytePtr(data: Pointer): Pointer

        fun CFDictionaryCreateMutable(
            alloc: Pointer?,
            capacity: Long,
            keyCallBacks: Pointer,
            valueCallBacks: Pointer,
        ): Pointer?

        fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)
    }

    @Suppress("FunctionNaming", "ktlint:standard:function-naming")
    private interface SecurityFramework : Library {
        fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int

        fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int

        fun SecItemDelete(query: Pointer): Int
    }

    private companion object {
        const val CF_PATH = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
        const val SECURITY_PATH = "/System/Library/Frameworks/Security.framework/Security"
        const val LABEL = "Encly PIN factor"
        const val K_CF_STRING_ENCODING_UTF8 = 0x08000100

        const val ERR_SEC_SUCCESS = 0
        const val ERR_SEC_ITEM_NOT_FOUND = -25300
        const val ERR_SEC_DUPLICATE_ITEM = -25299
    }
}
