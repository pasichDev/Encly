package com.pasich.encly.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.pasich.encly.core.AppLogger
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two places the device-bound PIN key can live. [A] is the one every vault starts with
 * (and the only one older builds know); a wipe-PIN unlock moves the PIN slot to the other one
 * and deletes the old key, so the erased slots can never be opened again (crypto-erase). The
 * vault records which one is active.
 */
enum class PinKeySlot {
    A,
    B,
    ;

    val other: PinKeySlot get() = if (this == A) B else A
}

/**
 * The device-bound half of the PIN key: an HMAC-SHA256 whose key never leaves this device's
 * secure hardware. Mixed into the PIN KEK, it makes every PIN guess run here, on the
 * Keystore, instead of on an attacker's GPU against a copied slot. An interface so JVM tests
 * can replace the Keystore.
 */
interface PinHardwareFactor {
    /** Creates the key in [slot] unless it already exists. */
    @Throws(PinFactorException::class)
    fun ensureKey(slot: PinKeySlot)

    /** Replaces the key in [slot] with a new one; slots sealed with the old one can no longer be opened. */
    @Throws(PinFactorException::class)
    fun reset(slot: PinKeySlot)

    /** HMAC-SHA256 of [data] under the key in [slot]. The caller wipes the result. */
    @Throws(PinFactorException::class)
    fun mac(slot: PinKeySlot, data: ByteArray): ByteArray

    /** Deletes the key in [slot], if any. Never throws. */
    fun delete(slot: PinKeySlot)
}

/**
 * The factor failed. [lost] means the key is gone for good (deleted, or invalidated by the
 * system): the PIN slot can never be opened again on this device and only the recovery phrase
 * (or the biometric slot) still unlocks. Otherwise the failure may be transient.
 */
class PinFactorException(val lost: Boolean, cause: Throwable? = null) : Exception(cause)

/**
 * [PinHardwareFactor] on AndroidKeyStore: a non-exportable 256-bit HMAC key, in StrongBox
 * when the device has one (falling back to the TEE), usable only while the device is unlocked
 * (API 28+).
 */
// Keystore reports several failures (StrongBox missing, rejected parameters, provider errors)
// as RuntimeExceptions; each one is mapped to a PinFactorException here, never let through.
@Suppress("TooGenericExceptionCaught")
@Singleton
class KeystorePinFactor @Inject constructor() : PinHardwareFactor {
    private val keyStore by lazy { KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) } }

    override fun ensureKey(slot: PinKeySlot) {
        val exists = try {
            keyStore.containsAlias(alias(slot))
        } catch (e: GeneralSecurityException) {
            throw PinFactorException(lost = false, cause = e)
        } catch (e: RuntimeException) {
            throw PinFactorException(lost = false, cause = e)
        }
        if (!exists) reset(slot)
    }

    override fun reset(slot: PinKeySlot) {
        delete(slot)
        try {
            generate(slot)
        } catch (e: GeneralSecurityException) {
            throw PinFactorException(lost = false, cause = e)
        } catch (e: RuntimeException) {
            throw PinFactorException(lost = false, cause = e)
        }
    }

    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        val key = try {
            keyStore.getKey(alias(slot), null) as? SecretKey
        } catch (e: UnrecoverableKeyException) {
            throw PinFactorException(lost = true, cause = e)
        } catch (e: GeneralSecurityException) {
            throw PinFactorException(lost = false, cause = e)
        } ?: throw PinFactorException(lost = true)
        return try {
            Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
                init(key)
                doFinal(data)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw PinFactorException(lost = true, cause = e)
        } catch (e: GeneralSecurityException) {
            throw PinFactorException(lost = false, cause = e)
        } catch (e: RuntimeException) {
            // Keystore reports some failures as ProviderException.
            throw PinFactorException(lost = false, cause = e)
        }
    }

    override fun delete(slot: PinKeySlot) {
        val alias = alias(slot)
        try {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (e: GeneralSecurityException) {
            AppLogger.w(TAG, "PIN factor key could not be deleted", e)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "PIN factor key could not be deleted", e)
        }
    }

    /**
     * StrongBox first; a device without one (or whose StrongBox lacks HMAC) falls back to the
     * TEE. The unlocked-device requirement is dropped only if the platform rejects it outright.
     */
    private fun generate(slot: PinKeySlot) {
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(Options(strongBox = true, unlockedOnly = true))
            add(Options(strongBox = false, unlockedOnly = true))
            add(Options(strongBox = false, unlockedOnly = false))
        }
        var last: Exception? = null
        for (options in attempts) {
            try {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER).run {
                    init(spec(alias(slot), options))
                    generateKey()
                }
                return
            } catch (e: GeneralSecurityException) {
                last = e
            } catch (e: RuntimeException) {
                // StrongBoxUnavailableException, and rejected parameters on some devices, are
                // ProviderExceptions.
                last = e
            }
            delete(slot)
        }
        throw last ?: error("no key generated")
    }

    private fun spec(alias: String, options: Options): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setKeySize(HMAC_KEY_BITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(options.strongBox)
            builder.setUnlockedDeviceRequired(options.unlockedOnly)
        }
        return builder.build()
    }

    private data class Options(val strongBox: Boolean, val unlockedOnly: Boolean)

    private companion object {
        const val TAG = "KeystorePinFactor"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        /** Slot A keeps the alias every vault has used since v3, so existing installs are untouched. */
        const val KEY_ALIAS_A = "encly_pin_factor_v3"
        const val KEY_ALIAS_B = "encly_pin_factor_v3b"
        const val HMAC_KEY_BITS = 256

        fun alias(slot: PinKeySlot): String = when (slot) {
            PinKeySlot.A -> KEY_ALIAS_A
            PinKeySlot.B -> KEY_ALIAS_B
        }
    }
}
