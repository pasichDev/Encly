package com.pasich.encly.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

const val PIN_LENGTH = 6

enum class AuthType {
    NONE,
    PIN,
    SEED_PHRASE,
}

enum class AuthStrategy {
    NONE,
    PIN,
    PIN_BIOMETRIC,
    SEED_PHRASE,
    SEED_PHRASE_BIOMETRIC,
    RECOVERY_DATA,
}

/** The outcome of one PIN attempt. */
sealed interface PinUnlock {
    /** The PIN opened the slot. [dek] belongs to the caller, who must wipe it. */
    class Success(val dek: ByteArray) : PinUnlock

    /** A wrong (or malformed) PIN; the attempt was counted. */
    data object WrongPin : PinUnlock

    /** A lockout is running; nothing was checked or counted. */
    data object LockedOut : PinUnlock

    /**
     * The device-bound PIN key is gone for good (see [PinHardwareFactor]): this PIN slot can
     * never open again on this device. The recovery phrase (or fingerprint) still unlocks.
     */
    data object KeyLost : PinUnlock

    /** The attempt could not be made (Keystore or storage failure); nothing was counted. */
    data object Failed : PinUnlock
}

/**
 * Encly v3 PIN unlock slot.
 *
 * There is no stored PIN hash. The PIN derives a KEK that must authenticate and decrypt the
 * random database DEK with AES-256-GCM:
 *
 *     hw  = HMAC-SHA256_keystore(salt ‖ pin)             (device-bound, see PinHardwareFactor)
 *     sw  = PBKDF2-HMAC-SHA256(pin, salt, 600 000)
 *     kek = HKDF-SHA256(ikm = sw ‖ hw, salt = salt, info = "encly/pin/kek/v3")
 *
 * `hw` means a copied slot is useless off the device: every guess needs this phone's secure
 * hardware, which also enforces "device unlocked" on API 28+. PBKDF2 is kept at full cost as
 * defence in depth for the case where the Keystore key itself were ever extracted.
 *
 * Failed attempts are counted durably *before* the KDF runs, under one lock, so killing the
 * app mid-check or racing two checks never gets a free guess. The lockout runs on
 * [LockoutClock.elapsedRealtime], not the wall clock, and escalates up to [MAX_LOCKOUT_MS].
 */
@Singleton
@Suppress("TooManyFunctions") // The complete PIN slot and lockout lifecycle in one place.
class AuthenticationManager @Inject constructor(
    private val store: VaultStore,
    private val factor: PinHardwareFactor,
    private val clock: LockoutClock,
) {
    private val attemptLock = Any()

    /** Creates (or replaces) the PIN slot around [dek]. [pin] stays the caller's to wipe. */
    @Suppress("ReturnCount") // Validation gates fail closed before any key is touched.
    fun configurePin(pin: CharArray, dek: ByteArray): Boolean {
        if (!isWellFormed(pin) || dek.size != DEK_LENGTH) return false
        val salt = ByteArray(PIN_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val kek = try {
            factor.ensureKey()
            try {
                derivePinKek(pin, salt)
            } catch (e: PinFactorException) {
                // A key the system invalidated can be replaced: the PIN it served is lost anyway.
                if (!e.lost) throw e
                factor.reset()
                derivePinKek(pin, salt)
            }
        } catch (_: PinFactorException) {
            SensitiveDataCleaner.clear(salt)
            return false
        }
        return try {
            val slot = sealSlot(dek, kek, salt)
            try {
                store.edit {
                    putBytes(PIN_SLOT_KEY, slot)
                    putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal)
                    removePrefix(LOCKOUT_PREFIX)
                }
            } finally {
                SensitiveDataCleaner.clear(slot)
            }
        } catch (_: GeneralSecurityException) {
            false
        } finally {
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(kek)
        }
    }

    /**
     * One PIN attempt. [pin] stays the caller's to wipe. On [PinUnlock.Success] the caller
     * owns the DEK and must wipe it.
     */
    @Suppress("ReturnCount") // Fail-closed gates: lockout, missing slot, unrecorded attempt.
    fun unlockWithPin(pin: CharArray): PinUnlock = synchronized(attemptLock) {
        if (remainingLockoutMillis() > 0) return PinUnlock.LockedOut
        val slot = store.getBytes(PIN_SLOT_KEY) ?: return PinUnlock.WrongPin
        try {
            val previousFailures = store.getInt(FAILURES_KEY, 0)
            // Counted before the KDF: an attempt the app is killed during still counts.
            if (!chargeAttempt(previousFailures + 1)) return PinUnlock.Failed
            if (!isWellFormed(pin)) return PinUnlock.WrongPin

            val parsed = parseSlot(slot) ?: return PinUnlock.WrongPin
            try {
                val kek = try {
                    derivePinKek(pin, parsed.salt)
                } catch (e: PinFactorException) {
                    // Not a guess: the hardware never answered. Give the attempt back.
                    refundAttempt(previousFailures)
                    return if (e.lost) PinUnlock.KeyLost else PinUnlock.Failed
                }
                try {
                    val dek = openSlot(parsed, kek)
                    store.edit { removePrefix(LOCKOUT_PREFIX) }
                    PinUnlock.Success(dek)
                } catch (_: AEADBadTagException) {
                    PinUnlock.WrongPin
                } catch (_: GeneralSecurityException) {
                    PinUnlock.WrongPin
                } finally {
                    SensitiveDataCleaner.clear(kek)
                }
            } finally {
                parsed.wipe()
            }
        } finally {
            SensitiveDataCleaner.clear(slot)
        }
    }

    /** Whether [pin] opens the slot; counts like an unlock attempt. [pin] stays the caller's. */
    fun verifyPinAuth(pin: CharArray): Boolean {
        val result = unlockWithPin(pin)
        if (result is PinUnlock.Success) SensitiveDataCleaner.clear(result.dek)
        return result is PinUnlock.Success
    }

    fun hasPinSlot(): Boolean = store.contains(PIN_SLOT_KEY)

    fun getAuthType(): AuthType {
        val ordinal = store.getInt(AUTH_TYPE_KEY, AuthType.NONE.ordinal)
        return AuthType.entries.getOrNull(ordinal) ?: AuthType.NONE
    }

    fun isAuthStrategy(): AuthStrategy {
        val biometric = isBiometricEnabled()
        return when (getAuthType()) {
            AuthType.NONE -> AuthStrategy.NONE

            AuthType.PIN -> when {
                !hasPinSlot() -> AuthStrategy.RECOVERY_DATA
                biometric -> AuthStrategy.PIN_BIOMETRIC
                else -> AuthStrategy.PIN
            }

            AuthType.SEED_PHRASE ->
                if (biometric) AuthStrategy.SEED_PHRASE_BIOMETRIC else AuthStrategy.SEED_PHRASE
        }
    }

    fun markBiometricEnabled(enabled: Boolean) {
        store.edit { putBoolean(BIOMETRIC_ENABLED_KEY, enabled) }
    }

    fun isBiometricEnabled(): Boolean = store.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun deactivateBiometricAuth() = markBiometricEnabled(false)

    /**
     * What is left of the running lockout. The store keeps the penalty that was left at an
     * `elapsedRealtime` anchor in a given boot. After a reboot (another boot count, or the
     * clock behind the anchor) the time since the anchor is unknown, so the whole remaining
     * penalty starts again from now: a reboot never shortens a lockout.
     */
    @Suppress("ReturnCount")
    fun remainingLockoutMillis(): Long {
        // Not under [attemptLock]: the UI polls this every second, also while an attempt runs.
        val penalty = store.getLong(PENALTY_KEY, 0L)
        if (penalty <= 0L) return 0L
        val anchor = store.getLong(ANCHOR_KEY, 0L)
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        if (store.getInt(BOOT_KEY, boot) != boot || now < anchor) {
            store.edit {
                putLong(ANCHOR_KEY, now)
                putInt(BOOT_KEY, boot)
            }
            return penalty
        }
        return (penalty - (now - anchor)).coerceAtLeast(0L)
    }

    /** Deletes the PIN slot, the lockout and the device-bound PIN key. */
    fun wipe() {
        store.edit {
            removePrefix(PIN_PREFIX)
            removePrefix(LOCKOUT_PREFIX)
            removePrefix(AUTH_PREFIX)
        }
        factor.delete()
    }

    private fun chargeAttempt(failures: Int): Boolean = store.edit {
        putInt(FAILURES_KEY, failures)
        val penalty = penaltyFor(failures)
        if (penalty > 0L) {
            putLong(PENALTY_KEY, penalty)
            putLong(ANCHOR_KEY, clock.elapsedRealtime())
            putInt(BOOT_KEY, clock.bootCount())
        } else {
            remove(PENALTY_KEY)
        }
    }

    private fun refundAttempt(previousFailures: Int) {
        store.edit {
            putInt(FAILURES_KEY, previousFailures)
            remove(PENALTY_KEY)
        }
    }

    private fun isWellFormed(pin: CharArray): Boolean = pin.size == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** [PinFactorException] leaves nothing behind; the result is the caller's to wipe. */
    private fun derivePinKek(pin: CharArray, salt: ByteArray): ByteArray {
        val macInput = ByteArray(salt.size + pin.size)
        System.arraycopy(salt, 0, macInput, 0, salt.size)
        pin.forEachIndexed { index, c -> macInput[salt.size + index] = c.code.toByte() }
        // The hardware half first: it is cheap, and a lost key fails before the PBKDF2 cost.
        val hardware = try {
            factor.mac(macInput)
        } finally {
            SensitiveDataCleaner.clear(macInput)
        }
        val stretched = pbkdf2(pin, salt)
        val ikm = stretched + hardware
        return try {
            Hkdf.sha256(ikm = ikm, salt = salt, info = KEK_INFO, length = DEK_LENGTH)
        } finally {
            SensitiveDataCleaner.clear(hardware)
            SensitiveDataCleaner.clear(stretched)
            SensitiveDataCleaner.clear(ikm)
        }
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, DEK_LENGTH * Byte.SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** `version ‖ salt ‖ iv ‖ AES-GCM(dek)`, with the version and salt authenticated as AAD. */
    private fun sealSlot(dek: ByteArray, kek: ByteArray, salt: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(aad(salt))
        val encrypted = cipher.doFinal(dek)
        val iv = cipher.iv
        return try {
            byteArrayOf(SLOT_VERSION) + salt + iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    private fun parseSlot(slot: ByteArray): ParsedSlot? {
        if (slot.size != SLOT_LENGTH || slot[0] != SLOT_VERSION) return null
        var offset = 1
        val salt = slot.copyOfRange(offset, offset + PIN_SALT_SIZE)
        offset += PIN_SALT_SIZE
        val iv = slot.copyOfRange(offset, offset + IV_LENGTH)
        offset += IV_LENGTH
        return ParsedSlot(salt, iv, slot.copyOfRange(offset, slot.size))
    }

    private fun openSlot(slot: ParsedSlot, kek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, slot.iv))
        cipher.updateAAD(aad(slot.salt))
        return cipher.doFinal(slot.ciphertext)
    }

    private fun aad(salt: ByteArray): ByteArray = PIN_AAD + byteArrayOf(SLOT_VERSION) + salt

    private class ParsedSlot(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray) {
        fun wipe() {
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    companion object {
        private const val PIN_PREFIX = "pin."
        private const val PIN_SLOT_KEY = "pin.slot"
        private const val AUTH_PREFIX = "auth."
        private const val AUTH_TYPE_KEY = "auth.type"
        private const val BIOMETRIC_ENABLED_KEY = "auth.biometric_enabled"
        private const val LOCKOUT_PREFIX = "lockout."
        private const val FAILURES_KEY = "lockout.failures"
        private const val PENALTY_KEY = "lockout.penalty_ms"
        private const val ANCHOR_KEY = "lockout.anchor_elapsed"
        private const val BOOT_KEY = "lockout.boot"

        const val MAX_ATTEMPTS = 5
        const val FIRST_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 24 * 60 * 60_000L
        private const val MAX_DOUBLINGS = 12

        private const val PIN_SALT_SIZE = 16
        private const val PBKDF2_ITERATIONS = 600_000
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_TAG_BYTES = GCM_TAG_LENGTH / 8
        private const val IV_LENGTH = 12
        private const val DEK_LENGTH = 32
        private const val SLOT_VERSION: Byte = 3
        private const val SLOT_LENGTH = 1 + PIN_SALT_SIZE + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES
        private const val AES_GCM = "AES/GCM/NoPadding"

        private val PIN_AAD = "encly/pin/slot/v3".toByteArray(Charsets.UTF_8)
        private val KEK_INFO = "encly/pin/kek/v3".toByteArray(Charsets.UTF_8)

        /**
         * The lockout after [failures] consecutive misses: none before [MAX_ATTEMPTS], then
         * 30 s, doubling each miss (1 min, 2, 4, 8, 16, 32 min, ~1 h, ~2 h ...) up to 24 h.
         */
        fun penaltyFor(failures: Int): Long {
            if (failures < MAX_ATTEMPTS) return 0L
            val doublings = (failures - MAX_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
        }
    }
}
