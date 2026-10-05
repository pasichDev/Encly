package com.pasich.encly.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
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

    /**
     * The wipe PIN was typed on the lock screen. The old vault's key slots are already gone
     * (phases 1 and 2, see [AuthenticationManager.unlockWithPin]); [dek] opens the new, empty
     * vault. The caller owns [dek], must wipe it, and finishes the erase (see [WipeStage]).
     */
    class Erased(val dek: ByteArray) : PinUnlock

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

/** The outcome of setting a wipe PIN. */
enum class WipePinChange {
    SET,

    /** The wipe PIN is the vault's PIN: it would never wipe anything. */
    SAME_AS_PIN,
    FAILED,
}

/**
 * The part of a wipe-PIN erase that is still to do, kept in the store so that a restart
 * finishes it (idempotently) whenever the process died.
 */
enum class WipeStage {
    /** The old database may still exist; the new, empty one is created at the next PIN unlock. */
    DATABASE,

    /** The empty database exists; the retired PIN key, the biometric key and the export date are left. */
    CLEANUP,
}

/**
 * Encly v3 PIN unlock slot, and the wipe-PIN slot next to it.
 *
 * There is no stored PIN hash. The PIN derives a KEK that must authenticate and decrypt the
 * random database DEK with AES-256-GCM:
 *
 *     hw   = HMAC-SHA256_keystore(salt ‖ pin)             (device-bound, see PinHardwareFactor)
 *     sw   = PBKDF2-HMAC-SHA256(pin, salt, 600 000)
 *     kek  = HKDF-SHA256(ikm = sw ‖ hw, salt = salt, info = "encly/pin/kek/v3")
 *     wkek = HKDF-SHA256(ikm = sw ‖ hw, salt = salt, info = "encly/pin/wipe/v3")
 *
 * `hw` means a copied slot is useless off the device: every guess needs this phone's secure
 * hardware, which also enforces "device unlocked" on API 28+. PBKDF2 is kept at full cost as
 * defence in depth for the case where the Keystore key itself were ever extracted.
 *
 * The wipe slot (`pin.wipe.slot`) is `version ‖ iv ‖ AES-GCM_wkek(random marker)`: no salt of
 * its own (a second salt equal to or different from the PIN slot's would tell whether it is
 * real), its AAD bound to the PIN slot's salt. Without a wipe PIN it holds random bytes of the
 * same length, so the store has the same keys and sizes either way and only the wipe PIN itself
 * can tell. Every attempt runs the KDF once and tries both slots, with no early exit.
 *
 * Failed attempts are counted durably *before* the KDF runs, under one lock, so killing the
 * app mid-check or racing two checks never gets a free guess. The lockout runs on
 * [LockoutClock.elapsedRealtime], not the wall clock, and escalates up to [MAX_LOCKOUT_MS].
 */
@Singleton
@Suppress("TooManyFunctions") // The complete PIN slot, wipe slot and lockout lifecycle in one place.
class AuthenticationManager @Inject constructor(
    private val store: VaultStore,
    private val factor: PinHardwareFactor,
    private val clock: LockoutClock,
) {
    private val attemptLock = Any()

    /**
     * Creates (or replaces) the PIN slot around [dek]. [pin] stays the caller's to wipe.
     *
     * A replaced slot keeps its salt, so a wipe PIN set earlier keeps working. The wipe slot is
     * replaced by a decoy (the wipe PIN turned off) when it can no longer open, because the
     * PIN key had to be reset or was gone and had to be made anew (Settings then says so), or
     * must not, because [pin] is the wipe PIN itself: one PIN never opens both slots.
     */
    @Suppress("ReturnCount") // Validation gates fail closed before any key is touched.
    fun configurePin(pin: CharArray, dek: ByteArray): Boolean {
        if (!isWellFormed(pin) || dek.size != DEK_LENGTH) return false
        val keySlot = activeKeySlot()
        val keptSalt = currentSalt()
        val salt = keptSalt ?: ByteArray(PIN_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        var keyReset = false
        val keys = try {
            // A key made here (deleted by the system, not just invalidated) is a reset too: the
            // wipe slot was sealed with the one that is gone.
            keyReset = factor.ensureKey(keySlot)
            try {
                derivePinKeys(pin, salt, keySlot)
            } catch (e: PinFactorException) {
                // A key the system invalidated can be replaced: the PIN it served is lost anyway.
                if (!e.lost) throw e
                factor.reset(keySlot)
                keyReset = true
                derivePinKeys(pin, salt, keySlot)
            }
        } catch (_: PinFactorException) {
            SensitiveDataCleaner.clear(salt)
            return false
        }
        return try {
            val change = when {
                keyReset && keptSalt != null -> WipeSlotChange.TURN_OFF_AND_TELL
                keyReset || keptSalt == null -> WipeSlotChange.TURN_OFF
                else -> wipeSlotChangeFor(keys.wipeKek, salt)
            }
            writePinSlot(dek, keys.kek, salt, keySlot, change)
        } catch (_: GeneralSecurityException) {
            false
        } finally {
            SensitiveDataCleaner.clear(salt)
            keys.wipe()
        }
    }

    /**
     * One PIN attempt. [pin] stays the caller's to wipe. On [PinUnlock.Success] and
     * [PinUnlock.Erased] the caller owns the DEK and must wipe it.
     *
     * The KDF runs once and both slots are always tried, so the PIN, the wipe PIN and a wrong
     * PIN cost the same up to here. The wipe PIN is counted before the KDF like any attempt and
     * is refused during a lockout; once it matched, the erase drops the lockout like a right
     * PIN does. In a PIN check inside the open vault ([verifyPinAuth]) it is a wrong PIN.
     *
     * On the wipe PIN, before returning: phase 1 makes a new key in the other Keystore slot
     * ([PinKeySlot]), a new DEK, a new PIN slot that the wipe PIN opens (same salt, and the
     * PBKDF2 result already computed, so the slow half does not run twice) and a fresh decoy
     * wipe slot; phase 2 swaps them in with one atomic store edit that also drops the recovery,
     * backup-key and biometric slots and the lockout, and records [WipeStage.DATABASE].
     */
    fun unlockWithPin(pin: CharArray): PinUnlock = attempt(pin, allowWipe = true)

    /**
     * Whether [pin] opens the slot; counts like an unlock attempt. [pin] stays the caller's.
     * The wipe PIN only wipes from the lock screen: here it is a wrong PIN.
     */
    fun verifyPinAuth(pin: CharArray): Boolean {
        val result = attempt(pin, allowWipe = false)
        if (result is PinUnlock.Success) SensitiveDataCleaner.clear(result.dek)
        return result is PinUnlock.Success
    }

    /** [unlockWithPin]; with [allowWipe] false a wipe-PIN match is a wrong PIN. */
    @Suppress("ReturnCount") // Fail-closed gates: lockout, missing slot, unrecorded attempt.
    private fun attempt(pin: CharArray, allowWipe: Boolean): PinUnlock = synchronized(attemptLock) {
        if (remainingLockoutMillis() > 0) return PinUnlock.LockedOut
        val slot = store.getBytes(PIN_SLOT_KEY) ?: return PinUnlock.WrongPin
        try {
            val previousFailures = store.getInt(FAILURES_KEY, 0)
            // Counted before the KDF: an attempt the app is killed during still counts.
            if (!chargeAttempt(previousFailures + 1)) return PinUnlock.Failed
            if (!isWellFormed(pin)) return PinUnlock.WrongPin

            val parsed = parseSlot(slot) ?: return PinUnlock.WrongPin
            try {
                val keys = try {
                    derivePinKeys(pin, parsed.salt, activeKeySlot())
                } catch (e: PinFactorException) {
                    // Not a guess: the hardware never answered. Give the attempt back.
                    refundAttempt(previousFailures)
                    return if (e.lost) PinUnlock.KeyLost else PinUnlock.Failed
                }
                try {
                    checkBothSlots(pin, parsed, keys, allowWipe)
                } finally {
                    keys.wipe()
                }
            } finally {
                parsed.wipe()
            }
        } finally {
            SensitiveDataCleaner.clear(slot)
        }
    }

    /**
     * Makes [wipePin] the wipe PIN, replacing any earlier one (whether there was one cannot be
     * known: a real wipe slot and the decoy look the same). [wipePin] stays the caller's to
     * wipe. Refused when it is the PIN itself. Runs the KDF once, like an unlock, and is not
     * counted as an attempt: the caller has the vault open and has checked the PIN.
     */
    @Suppress("ReturnCount") // Fail-closed gates before the slot is written.
    fun configureWipePin(wipePin: CharArray): WipePinChange = synchronized(attemptLock) {
        if (!isWellFormed(wipePin)) return WipePinChange.FAILED
        val slot = store.getBytes(PIN_SLOT_KEY) ?: return WipePinChange.FAILED
        val parsed = try {
            parseSlot(slot)
        } finally {
            SensitiveDataCleaner.clear(slot)
        } ?: return WipePinChange.FAILED
        try {
            val keys = try {
                derivePinKeys(wipePin, parsed.salt, activeKeySlot())
            } catch (_: PinFactorException) {
                return WipePinChange.FAILED
            }
            try {
                // A wipe PIN that opens the PIN slot would only ever unlock.
                val dek = openSlotOrNull(parsed, keys.kek)
                if (dek != null) {
                    SensitiveDataCleaner.clear(dek)
                    return WipePinChange.SAME_AS_PIN
                }
                writeWipeSlot(keys.wipeKek, parsed.salt)
            } finally {
                keys.wipe()
            }
        } finally {
            parsed.wipe()
        }
    }

    /** Turns the wipe PIN off: the wipe slot becomes a fresh decoy, as on a vault that never had one. */
    fun removeWipePin(): Boolean {
        val decoy = decoyWipeSlot()
        return try {
            store.edit {
                putBytes(WIPE_SLOT_KEY, decoy)
                remove(WIPE_NOTICE_KEY)
            }
        } finally {
            SensitiveDataCleaner.clear(decoy)
        }
    }

    /**
     * True after the PIN key had to be reset (see [configurePin]): any wipe PIN was turned off
     * and Settings asks to set it again. Cleared by setting or removing the wipe PIN.
     */
    fun wipePinTurnedOff(): Boolean = store.getBoolean(WIPE_NOTICE_KEY, false)

    /**
     * Startup migration for vaults made before the wipe PIN: adds the decoy wipe slot (random
     * bytes, so no PIN is needed) and records key slot A. `pin.slot` is left byte for byte as
     * it is; a store without a PIN slot, or with both entries already, is not written at all.
     */
    fun ensureWipeSlot(): Boolean {
        val hasPinSlot = hasPinSlot()
        val needsDecoy = hasPinSlot && !store.contains(WIPE_SLOT_KEY)
        val needsKeySlot = hasPinSlot && !store.contains(KEY_SLOT_KEY)
        if (!needsDecoy && !needsKeySlot) return true
        val decoy = decoyWipeSlot()
        return try {
            store.edit {
                if (needsDecoy) putBytes(WIPE_SLOT_KEY, decoy)
                if (needsKeySlot) putInt(KEY_SLOT_KEY, PinKeySlot.A.ordinal)
            }
        } finally {
            SensitiveDataCleaner.clear(decoy)
        }
    }

    /** The unfinished part of a wipe-PIN erase, or null when there is none. */
    fun pendingWipe(): WipeStage? = if (store.contains(WIPE_PENDING_KEY)) {
        // An unknown value redoes the most: the database step.
        WipeStage.entries.getOrNull(store.getInt(WIPE_PENDING_KEY, -1)) ?: WipeStage.DATABASE
    } else {
        null
    }

    /** The empty database of an erased vault exists; only the cleanup is left. */
    fun markWipeDatabaseCreated(): Boolean = store.edit { putInt(WIPE_PENDING_KEY, WipeStage.CLEANUP.ordinal) }

    /** Deletes the PIN key the erase moved away from (nothing when there is none). */
    fun deleteRetiredPinKey() = synchronized(attemptLock) { factor.delete(activeKeySlot().other) }

    /**
     * Startup: deletes a key in slot B that no committed state uses, left by a wipe-PIN erase
     * the process died in between phase 1 (the new key) and phase 2 (the store edit). Only with
     * slot A active and no erase pending; slot A itself is never touched here, it is the one key
     * older builds know. Under the attempt lock, so it never races an erase in progress.
     */
    fun deleteStrayPinKey() = synchronized(attemptLock) {
        if (pendingWipe() == null && activeKeySlot() == PinKeySlot.A) factor.delete(PinKeySlot.B)
    }

    /** The wipe-PIN erase is complete. */
    fun clearPendingWipe(): Boolean = store.edit { remove(WIPE_PENDING_KEY) }

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

    /** Deletes the PIN and wipe slots, the lockout and the device-bound PIN keys. */
    fun wipe() {
        store.edit {
            removePrefix(PIN_PREFIX)
            removePrefix(LOCKOUT_PREFIX)
            removePrefix(AUTH_PREFIX)
            remove(WIPE_PENDING_KEY)
        }
        PinKeySlot.entries.forEach(factor::delete)
    }

    /** Opens whichever slot [keys] open; see [unlockWithPin]. */
    private fun checkBothSlots(pin: CharArray, parsed: ParsedSlot, keys: PinKeys, allowWipe: Boolean): PinUnlock {
        val dek = openSlotOrNull(parsed, keys.kek)
        // Tried even when the PIN slot already opened: no early exit.
        val wipeSlot = store.getBytes(WIPE_SLOT_KEY)
        val wipeMatch = wipeSlot != null &&
            try {
                opensWipeSlot(wipeSlot, keys.wipeKek, parsed.salt)
            } finally {
                SensitiveDataCleaner.clear(wipeSlot)
            }
        return when {
            dek != null -> {
                store.edit { removePrefix(LOCKOUT_PREFIX) }
                PinUnlock.Success(dek)
            }

            wipeMatch && allowWipe -> eraseVault(pin, parsed.salt, keys)

            else -> PinUnlock.WrongPin
        }
    }

    /**
     * Phases 1 and 2 of the wipe-PIN erase (see [unlockWithPin]). If no key can be made in the
     * other Keystore slot, the new PIN slot uses the current key: the old slots are still
     * dropped and the old database still deleted, only the crypto-erase of the old PIN key is
     * lost. A store that cannot be written changes nothing and reports [PinUnlock.Failed]; the
     * key made for it is deleted again, so a failed erase leaves no extra Keystore key behind.
     */
    private fun eraseVault(pin: CharArray, salt: ByteArray, keys: PinKeys): PinUnlock {
        val keySlot = freshKeySlot(keys.keySlot)
        val result = sealErasedVault(pin, salt, keys, keySlot)
        if (result !is PinUnlock.Erased && keySlot != keys.keySlot) factor.delete(keySlot)
        return result
    }

    /** Phases 1 and 2 with the new key in [keySlot]; see [eraseVault]. */
    private fun sealErasedVault(pin: CharArray, salt: ByteArray, keys: PinKeys, keySlot: PinKeySlot): PinUnlock {
        val kek = try {
            val hardware = hardwareHalf(pin, salt, keySlot)
            try {
                expand(keys.stretched, hardware, salt, KEK_INFO)
            } finally {
                SensitiveDataCleaner.clear(hardware)
            }
        } catch (_: PinFactorException) {
            return PinUnlock.Failed
        }
        val dek = ByteArray(DEK_LENGTH).also { SecureRandom().nextBytes(it) }
        val decoy = decoyWipeSlot()
        return try {
            val slot = sealSlot(dek, kek, salt)
            val erased = try {
                store.edit {
                    putBytes(PIN_SLOT_KEY, slot)
                    putBytes(WIPE_SLOT_KEY, decoy)
                    putInt(KEY_SLOT_KEY, keySlot.ordinal)
                    putInt(WIPE_PENDING_KEY, WipeStage.DATABASE.ordinal)
                    remove(WIPE_NOTICE_KEY)
                    remove(BIOMETRIC_ENABLED_KEY)
                    removePrefix(LOCKOUT_PREFIX)
                    ERASED_PREFIXES.forEach { removePrefix(it) }
                }
            } finally {
                SensitiveDataCleaner.clear(slot)
            }
            if (erased) PinUnlock.Erased(dek.copyOf()) else PinUnlock.Failed
        } catch (_: GeneralSecurityException) {
            PinUnlock.Failed
        } finally {
            SensitiveDataCleaner.clear(dek)
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(decoy)
        }
    }

    /**
     * For a new PIN whose wipe KEK is [wipeKek] (same salt, same key): the wipe slot stays only
     * if it exists and the new PIN does not open it, i.e. is not the wipe PIN.
     */
    private fun wipeSlotChangeFor(wipeKek: ByteArray, salt: ByteArray): WipeSlotChange {
        val wipeSlot = store.getBytes(WIPE_SLOT_KEY) ?: return WipeSlotChange.TURN_OFF
        return try {
            if (opensWipeSlot(wipeSlot, wipeKek, salt)) WipeSlotChange.TURN_OFF else WipeSlotChange.KEEP
        } finally {
            SensitiveDataCleaner.clear(wipeSlot)
        }
    }

    /** A new key in the slot other than [current]; [current] itself when none can be made. */
    private fun freshKeySlot(current: PinKeySlot): PinKeySlot = try {
        factor.reset(current.other)
        current.other
    } catch (_: PinFactorException) {
        current
    }

    private fun writePinSlot(
        dek: ByteArray,
        kek: ByteArray,
        salt: ByteArray,
        keySlot: PinKeySlot,
        change: WipeSlotChange,
    ): Boolean {
        val slot = sealSlot(dek, kek, salt)
        val decoy = if (change == WipeSlotChange.KEEP) null else decoyWipeSlot()
        return try {
            store.edit {
                putBytes(PIN_SLOT_KEY, slot)
                decoy?.let { putBytes(WIPE_SLOT_KEY, it) }
                putInt(KEY_SLOT_KEY, keySlot.ordinal)
                putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal)
                if (change == WipeSlotChange.TURN_OFF_AND_TELL) putBoolean(WIPE_NOTICE_KEY, true)
                removePrefix(LOCKOUT_PREFIX)
            }
        } finally {
            SensitiveDataCleaner.clear(slot)
            decoy?.let(SensitiveDataCleaner::clear)
        }
    }

    private fun writeWipeSlot(wipeKek: ByteArray, salt: ByteArray): WipePinChange {
        val wipeSlot = try {
            sealWipeSlot(wipeKek, salt)
        } catch (_: GeneralSecurityException) {
            return WipePinChange.FAILED
        }
        return try {
            val written = store.edit {
                putBytes(WIPE_SLOT_KEY, wipeSlot)
                remove(WIPE_NOTICE_KEY)
            }
            if (written) WipePinChange.SET else WipePinChange.FAILED
        } finally {
            SensitiveDataCleaner.clear(wipeSlot)
        }
    }

    /** The key slot the PIN slot is sealed with: A unless an erase moved it. */
    private fun activeKeySlot(): PinKeySlot =
        PinKeySlot.entries.getOrNull(store.getInt(KEY_SLOT_KEY, PinKeySlot.A.ordinal)) ?: PinKeySlot.A

    /** The salt of the current PIN slot, kept across PIN changes; null without a readable slot. */
    private fun currentSalt(): ByteArray? {
        val slot = store.getBytes(PIN_SLOT_KEY) ?: return null
        val parsed = try {
            parseSlot(slot)
        } finally {
            SensitiveDataCleaner.clear(slot)
        }
        return parsed?.let {
            val salt = it.salt.copyOf()
            it.wipe()
            salt
        }
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

    /**
     * One KDF run for both slots. [PinFactorException] leaves nothing behind; the result is
     * the caller's to wipe.
     */
    private fun derivePinKeys(pin: CharArray, salt: ByteArray, keySlot: PinKeySlot): PinKeys {
        // The hardware half first: it is cheap, and a lost key fails before the PBKDF2 cost.
        val hardware = hardwareHalf(pin, salt, keySlot)
        val stretched = pbkdf2(pin, salt)
        val ikm = stretched + hardware
        return try {
            PinKeys(
                keySlot = keySlot,
                stretched = stretched,
                kek = Hkdf.sha256(ikm = ikm, salt = salt, info = KEK_INFO, length = DEK_LENGTH),
                wipeKek = Hkdf.sha256(ikm = ikm, salt = salt, info = WIPE_INFO, length = DEK_LENGTH),
            )
        } finally {
            SensitiveDataCleaner.clear(hardware)
            SensitiveDataCleaner.clear(ikm)
        }
    }

    /** HMAC of `salt ‖ pin` under the key in [keySlot]; the caller wipes the result. */
    private fun hardwareHalf(pin: CharArray, salt: ByteArray, keySlot: PinKeySlot): ByteArray {
        val macInput = ByteArray(salt.size + pin.size)
        System.arraycopy(salt, 0, macInput, 0, salt.size)
        pin.forEachIndexed { index, c -> macInput[salt.size + index] = c.code.toByte() }
        return try {
            factor.mac(keySlot, macInput)
        } finally {
            SensitiveDataCleaner.clear(macInput)
        }
    }

    private fun expand(stretched: ByteArray, hardware: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val ikm = stretched + hardware
        return try {
            Hkdf.sha256(ikm = ikm, salt = salt, info = info, length = DEK_LENGTH)
        } finally {
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

    /** The DEK in [slot], or null when [kek] does not open it. The caller wipes the result. */
    private fun openSlotOrNull(slot: ParsedSlot, kek: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, slot.iv))
        cipher.updateAAD(aad(slot.salt))
        cipher.doFinal(slot.ciphertext)
    } catch (_: GeneralSecurityException) {
        // AEADBadTagException included: a wrong PIN.
        null
    }

    private fun aad(salt: ByteArray): ByteArray = PIN_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** `version ‖ iv ‖ AES-GCM(random marker)`, bound to the PIN slot's [salt] as AAD. */
    private fun sealWipeSlot(wipeKek: ByteArray, salt: ByteArray): ByteArray {
        val marker = ByteArray(WIPE_MARKER_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wipeKek, "AES"))
        cipher.updateAAD(wipeAad(salt))
        val encrypted = try {
            cipher.doFinal(marker)
        } finally {
            SensitiveDataCleaner.clear(marker)
        }
        return try {
            byteArrayOf(SLOT_VERSION) + cipher.iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    /** Whether [wipeKek] authenticates [wipeSlot]; a decoy never does. */
    private fun opensWipeSlot(wipeSlot: ByteArray, wipeKek: ByteArray, salt: ByteArray): Boolean {
        if (wipeSlot.size != WIPE_SLOT_LENGTH || wipeSlot[0] != SLOT_VERSION) return false
        val iv = wipeSlot.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = wipeSlot.copyOfRange(1 + IV_LENGTH, wipeSlot.size)
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wipeKek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.updateAAD(wipeAad(salt))
            SensitiveDataCleaner.clear(cipher.doFinal(ciphertext))
            true
        } catch (_: GeneralSecurityException) {
            false
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    private fun wipeAad(salt: ByteArray): ByteArray = WIPE_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** Random bytes shaped like a wipe slot (the same version byte and length): no PIN opens it. */
    private fun decoyWipeSlot(): ByteArray = ByteArray(WIPE_SLOT_LENGTH).also {
        SecureRandom().nextBytes(it)
        it[0] = SLOT_VERSION
    }

    private class ParsedSlot(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray) {
        fun wipe() {
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    /** Both KEKs of one KDF run, and its PBKDF2 half (reused if the vault is erased). */
    private class PinKeys(
        val keySlot: PinKeySlot,
        val stretched: ByteArray,
        val kek: ByteArray,
        val wipeKek: ByteArray,
    ) {
        fun wipe() {
            SensitiveDataCleaner.clear(stretched)
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(wipeKek)
        }
    }

    /** What a new PIN slot does to the wipe slot (see [configurePin]). */
    private enum class WipeSlotChange { KEEP, TURN_OFF, TURN_OFF_AND_TELL }

    companion object {
        private const val PIN_PREFIX = "pin."
        private const val PIN_SLOT_KEY = "pin.slot"
        private const val WIPE_SLOT_KEY = "pin.wipe.slot"
        private const val WIPE_NOTICE_KEY = "pin.wipe.notice"
        private const val KEY_SLOT_KEY = "pin.key_slot"
        private const val WIPE_PENDING_KEY = "wipe.pending"
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
        private const val WIPE_MARKER_LENGTH = 32
        private const val WIPE_SLOT_LENGTH = 1 + IV_LENGTH + WIPE_MARKER_LENGTH + GCM_TAG_BYTES
        private const val AES_GCM = "AES/GCM/NoPadding"

        private val PIN_AAD = "encly/pin/slot/v3".toByteArray(Charsets.UTF_8)
        private val KEK_INFO = "encly/pin/kek/v3".toByteArray(Charsets.UTF_8)
        private val WIPE_AAD = "encly/pin/wipe-slot/v3".toByteArray(Charsets.UTF_8)
        private val WIPE_INFO = "encly/pin/wipe/v3".toByteArray(Charsets.UTF_8)

        /** Every other way back to an erased vault's DEK: recovery, backup-key and biometric slots. */
        private val ERASED_PREFIXES = listOf(
            SeedPhraseManager.RECOVERY_PREFIX,
            SeedPhraseManager.BACKUP_PREFIX,
            BiometricManager.SLOT_PREFIX,
        )

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
