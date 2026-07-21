package com.pasich.encly.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HmacIntegrityManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val KEY_ALIAS = "hmac_integrity_key"
        private const val PREFS_NAME = "integrity_prefs"
        private const val INTEGRITY_HMAC_KEY = "seed_hash_hmac"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    private fun generateKeyIfMissing() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).apply {
                setKeySize(256)
            }.build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }


    private fun getHmacKey(): SecretKey {
        generateKeyIfMissing()
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun storeHmac(hash: ByteArray) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getHmacKey())
        val hmacBytes = mac.doFinal(hash)

        val encodedHmac = Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        prefs.edit {
            putString(INTEGRITY_HMAC_KEY, encodedHmac)
        }
    }

    fun isHashTampered(hash: ByteArray): Boolean {
        val savedHmacBase64 = prefs.getString(INTEGRITY_HMAC_KEY, null) ?: return true
        val savedHmac = Base64.decode(savedHmacBase64, Base64.NO_WRAP)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getHmacKey())
        val expectedHmac = mac.doFinal(hash)

        return !expectedHmac.contentEquals(savedHmac)
    }

    /** Full wipe: clears stored HMAC and deletes the Keystore signing key. */
    fun wipe() {
        prefs.edit { clear() }
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }
}
