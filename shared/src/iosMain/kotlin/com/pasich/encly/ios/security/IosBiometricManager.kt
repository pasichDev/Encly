@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.ios.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.keystore.BiometricKeychain
import com.pasich.encly.core.keystore.BiometricKeychainException
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.biometric_prompt_general_subtitle
import com.pasich.encly.core.resources.biometric_prompt_general_title
import com.pasich.encly.core.resources.biometric_prompt_key_subtitle
import com.pasich.encly.core.resources.biometric_prompt_key_title
import com.pasich.encly.core.resources.biometric_prompt_settings_subtitle
import com.pasich.encly.core.resources.biometric_prompt_settings_title
import com.pasich.encly.core.resources.biometric_prompt_unlock_subtitle
import com.pasich.encly.core.resources.biometric_prompt_unlock_title
import com.pasich.encly.core.resources.biometric_prompt_use_pin
import com.pasich.encly.core.resources.biometric_unavailable
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.BiometricManager.BiometricType
import com.pasich.encly.core.security.BiometricManager.PromptConfig
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.VaultStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile

/** What a biometric prompt is shown on. On iOS the system draws it over the app, so there is nothing to hold. */
internal object IosBiometricHost : BiometricHost

/** The texts of the system biometric prompts, as plain strings in the in-app language. */
internal class BiometricTexts(
    val unavailable: String,
    val cancel: String,
    val unlock: PromptConfig,
    val masterKey: PromptConfig,
    val settings: PromptConfig,
    val general: PromptConfig,
)

/**
 * Holds the [BiometricTexts] for [IosBiometricManager], which builds the system prompt outside any coroutine and is
 * handed finished strings instead of resolving resources itself. The app calls [refresh] before the first screen.
 */
internal class IosBiometricTexts {
    @Volatile
    private var texts: BiometricTexts? = null

    suspend fun refresh() {
        texts = BiometricTexts(
            unavailable = getString(Res.string.biometric_unavailable),
            cancel = getString(Res.string.cancel),
            unlock = PromptConfig(
                title = getString(Res.string.biometric_prompt_unlock_title),
                subtitle = getString(Res.string.biometric_prompt_unlock_subtitle),
                negativeButtonText = getString(Res.string.biometric_prompt_use_pin),
            ),
            masterKey = PromptConfig(
                title = getString(Res.string.biometric_prompt_key_title),
                subtitle = getString(Res.string.biometric_prompt_key_subtitle),
            ),
            settings = PromptConfig(
                title = getString(Res.string.biometric_prompt_settings_title),
                subtitle = getString(Res.string.biometric_prompt_settings_subtitle),
            ),
            general = PromptConfig(
                title = getString(Res.string.biometric_prompt_general_title),
                subtitle = getString(Res.string.biometric_prompt_general_subtitle),
            ),
        )
    }

    /** The texts of the last [refresh]; read here, once, if a prompt is asked for before any refresh. */
    fun current(): BiometricTexts = texts ?: runBlocking { refresh() }.let { checkNotNull(texts) }
}

/**
 * The biometric unlock slot on iOS, with the semantics of Android's `KeystoreBiometricManager`.
 *
 * - Android: an AES key in the AndroidKeyStore, auth-per-use. iOS: a random 32-byte wrap key in a Keychain item
 *   whose access control is `biometryCurrentSet`.
 * - Android: a BIOMETRIC_STRONG CryptoObject releases the key. iOS: the Secure Enclave releases the item only after
 *   a Face ID or Touch ID match for that very read.
 * - Android: the key is invalidated by biometric enrollment. iOS: the system drops the item when the biometric set
 *   changes.
 * - Android: no device-credential fallback. iOS: no `devicePasscode` flag, and the fallback button is hidden.
 * - Both: the slot `bio.slot` in the [VaultStore] is iv ‖ AES-GCM(key, DEK) (the key is Android's Keystore key or
 *   the iOS wrap key), and [hasSlot] needs the key and the slot entry.
 *
 * As on Android, the key alone opens nothing and the slot entry alone opens nothing, so the wipe and duress paths,
 * which drop every `bio.` entry of the [VaultStore] in one atomic write, leave no usable DEK behind. A "prompt
 * succeeded" answer never opens the vault: [unlock] returns the DEK only from the Keychain read that the biometric
 * match authorizes, followed by an AES-GCM open. [enroll] stores a fresh wrap key and then reads it back through the
 * biometric prompt before it wraps the DEK, the iOS form of wrapping through an authenticated cipher. The Keychain
 * read blocks until the user answers, so it runs on a background queue; every callback is delivered on the main
 * thread, as on Android.
 */
@Suppress("TooManyFunctions") // The whole slot lifecycle, as in KeystoreBiometricManager.
internal class IosBiometricManager(
    private val store: VaultStore,
    private val keychain: BiometricKeychain,
    private val texts: IosBiometricTexts,
) : BiometricManager {
    override fun isStrongBiometricAvailable(): Boolean = strongBiometricStatus() == BiometricStatus.AVAILABLE

    override fun strongBiometricStatus(): BiometricStatus = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        // Face ID and Touch ID are both Class 3; this policy is biometrics only, with no passcode fallback.
        if (LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)) {
            BiometricStatus.AVAILABLE
        } else if (error.value?.code == LAErrorBiometryNotEnrolled) {
            BiometricStatus.NOT_ENROLLED
        } else {
            BiometricStatus.UNAVAILABLE
        }
    }

    override fun hasSlot(): Boolean {
        if (!store.contains(SLOT_KEY)) {
            // A Keychain item without its slot entry is left over from a wipe that only dropped the entries.
            if (keychain.exists()) keychain.delete()
            return false
        }
        return keychain.exists()
    }

    override fun enroll(host: BiometricHost, dek: ByteArray, onResult: (Boolean) -> Unit) {
        if (host !is IosBiometricHost || !isStrongBiometricAvailable() || dek.size != DEK_LENGTH) {
            onResult(false)
            return
        }
        val dekCopy = dek.copyOf()
        store.edit { remove(SLOT_KEY) }
        keychain.delete()
        val fresh = Crypto.randomBytes(KEY_LENGTH)
        val kept = keychain.store(fresh)
        SensitiveDataCleaner.clear(fresh)
        if (!kept) {
            SensitiveDataCleaner.clear(dekCopy)
            disable()
            onResult(false)
            return
        }
        val prompt = promptFor(BiometricType.SETTINGS_TOGGLE, null)
        readWrapKey(prompt, cancelTitle = null) { wrapKey, _ ->
            val stored = wrapKey != null && wrap(wrapKey, dekCopy)
            wrapKey?.let(SensitiveDataCleaner::clear)
            SensitiveDataCleaner.clear(dekCopy)
            if (!stored) disable()
            onResult(stored)
        }
    }

    override fun unlock(host: BiometricHost, onResult: (ByteArray?) -> Unit) {
        val slot = store.getBytes(SLOT_KEY)
        val usable = host is IosBiometricHost && slot != null && slot.size > IV_LENGTH
        if (slot == null || !usable || !isStrongBiometricAvailable()) {
            slot?.let(SensitiveDataCleaner::clear)
            onResult(null)
            return
        }
        val prompt = promptFor(BiometricType.APP_UNLOCK, null)
        readWrapKey(prompt, cancelTitle = prompt.negativeButtonText) { wrapKey, error ->
            if (wrapKey == null) {
                SensitiveDataCleaner.clear(slot)
                // A dropped item is what Android's KeyPermanentlyInvalidatedException is: the slot is over.
                if (error?.gone == true) disable()
                onResult(null)
                return@readWrapKey
            }
            val dek = Crypto.aesGcmOpen(
                wrapKey,
                slot.copyOfRange(0, IV_LENGTH),
                SLOT_AAD,
                slot.copyOfRange(IV_LENGTH, slot.size),
            )
            SensitiveDataCleaner.clear(wrapKey)
            SensitiveDataCleaner.clear(slot)
            // A slot that the key does not open is damaged for good.
            if (dek == null) disable()
            onResult(dek)
        }
    }

    /** Wraps [dek] under [wrapKey] into the slot entry of the store; false if it was not stored. */
    private fun wrap(wrapKey: ByteArray, dek: ByteArray): Boolean {
        val iv = Crypto.randomBytes(IV_LENGTH)
        val sealed = Crypto.aesGcmSeal(wrapKey, iv, SLOT_AAD, dek)
        val slot = iv + sealed
        val stored = store.edit { putBytes(SLOT_KEY, slot) }
        SensitiveDataCleaner.clear(sealed)
        SensitiveDataCleaner.clear(slot)
        return stored
    }

    /**
     * Reads the wrap key through a biometric prompt on a background queue and hands the result to [done] on the
     * main thread: the key, or null with the reason (cancelled, or the item is gone).
     */
    private fun readWrapKey(
        config: PromptConfig,
        cancelTitle: String?,
        done: (ByteArray?, BiometricKeychainException?) -> Unit,
    ) {
        val context = context(config, cancelTitle)
        // The read blocks while the system prompt is up: never on the main thread.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            var key: ByteArray? = null
            var failure: BiometricKeychainException? = null
            try {
                key = keychain.read(context)
            } catch (e: BiometricKeychainException) {
                failure = e
            }
            onMain { done(key, failure) }
        }
    }

    override fun authenticate(
        host: BiometricHost,
        type: BiometricType,
        callback: BiometricManager.BiometricCallback,
        customConfig: PromptConfig?,
    ) {
        if (host !is IosBiometricHost || !isStrongBiometricAvailable()) {
            callback.onError(ERROR_UNAVAILABLE, texts.current().unavailable)
            return
        }
        val config = promptFor(type, customConfig)
        evaluate(context(config, cancelTitle = config.negativeButtonText)) { success, error ->
            when {
                success -> callback.onSuccess()

                error?.code in CANCELLED -> callback.onCancelled()

                // A failed match ends this prompt on iOS; the caller treats it like any other terminal error.
                else -> callback.onError(
                    error?.code?.toInt() ?: ERROR_UNAVAILABLE,
                    error?.localizedDescription.orEmpty(),
                )
            }
        }
    }

    override fun disable() {
        store.edit { removePrefix(BiometricManager.SLOT_PREFIX) }
        keychain.delete()
    }

    private fun promptFor(type: BiometricType, custom: PromptConfig?): PromptConfig = custom ?: when (type) {
        BiometricType.APP_UNLOCK -> texts.current().unlock
        BiometricType.MASTER_KEY_ACCESS -> texts.current().masterKey
        BiometricType.SETTINGS_TOGGLE -> texts.current().settings
        BiometricType.GENERAL -> texts.current().general
    }

    /** A fresh context per prompt: no reuse of an earlier match, and the texts of this prompt. */
    private fun context(config: PromptConfig, cancelTitle: String?): LAContext = LAContext().apply {
        localizedReason = config.description ?: config.subtitle.ifBlank { config.title }
        localizedCancelTitle = cancelTitle ?: texts.current().cancel
        // No "Enter Password" button: BIOMETRIC_STRONG only, the user falls back to Encly's own PIN.
        localizedFallbackTitle = ""
        touchIDAuthenticationAllowableReuseDuration = 0.0
    }

    private fun evaluate(context: LAContext, done: (Boolean, NSError?) -> Unit) {
        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = context.localizedReason,
        ) { success, error -> onMain { done(success, error) } }
    }

    private fun onMain(action: () -> Unit) {
        dispatch_async(dispatch_get_main_queue(), action)
    }

    private companion object {
        const val SLOT_KEY = BiometricManager.SLOT_PREFIX + "slot"
        const val DEK_LENGTH = 32
        const val KEY_LENGTH = 32
        const val IV_LENGTH = 12

        /** Binds the envelope to this slot, as the other envelopes of the vault are bound to theirs. */
        val SLOT_AAD = "encly/biometric/slot/v2".encodeToByteArray()

        /** The code the callers already treat as "no biometrics" (BiometricPrompt's ERROR_HW_NOT_PRESENT). */
        const val ERROR_UNAVAILABLE = 12

        val CANCELLED = setOf(LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel, LAErrorUserFallback)
    }
}
