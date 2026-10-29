package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.crypto.CryptoException
import com.pasich.encly.core.security.DuressSlots.DECOY_KEY_AAD
import com.pasich.encly.core.security.DuressSlots.DECOY_KEY_OFF_AAD
import com.pasich.encly.core.security.DuressSlots.decoyKeyKek
import com.pasich.encly.core.security.DuressSlots.decoySlotAad
import com.pasich.encly.core.security.DuressSlots.openDecoy
import com.pasich.encly.core.security.DuressSlots.randomSlot
import com.pasich.encly.core.security.DuressSlots.sealDecoy
import com.pasich.encly.core.security.PinSlots.DEK_LENGTH
import com.pasich.encly.core.security.PinSlots.PIN_SALT_SIZE
import com.pasich.encly.core.security.PinSlots.ParsedSlot
import com.pasich.encly.core.security.PinSlots.decoyWipeSlot
import com.pasich.encly.core.security.PinSlots.openSlotOrNull
import com.pasich.encly.core.security.PinSlots.opensWipeSlot
import com.pasich.encly.core.security.PinSlots.parseSlot
import com.pasich.encly.core.security.PinSlots.sealSlot
import com.pasich.encly.core.security.PinSlots.sealWipeSlot
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

    /**
     * The duress PIN was typed on the lock screen: [dek] opens the decoy vault, a database of
     * its own. The caller owns [dek] and must wipe it. Nothing of the vault was changed.
     */
    class Decoy(val dek: ByteArray) : PinUnlock

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

    /** The wipe PIN is the duress PIN: one PIN never opens two slots. */
    IN_USE,
    FAILED,
}

/** The outcome of setting the vault's PIN from Settings. */
enum class PinChange {
    SET,

    /** The new PIN is the duress PIN: one PIN never opens two slots. */
    IN_USE,
    FAILED,
}

/** The outcome of setting the duress PIN. */
enum class DuressPinChange {
    /** Set; the decoy vault it opens keeps the notes it had. */
    SET,

    /** Set for a new, empty decoy vault (there was none, or it was removed). */
    SET_NEW_VAULT,

    /** The duress PIN is the vault's PIN. */
    SAME_AS_PIN,

    /** The duress PIN is the wipe PIN. */
    IN_USE,
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
    fun configurePin(pin: CharArray, dek: ByteArray): Boolean = changePin(pin, dek) == PinChange.SET

    /**
     * [configurePin], telling why it was refused. The duress slot is handled like the wipe slot,
     * except that a new PIN equal to the duress PIN is refused ([PinChange.IN_USE]) rather than
     * turning it off: the decoy vault holds notes, which a PIN change must not silently orphan.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Validation gates fail closed before any key is touched.
    fun changePin(pin: CharArray, dek: ByteArray): PinChange = synchronized(attemptLock) {
        if (!isWellFormed(pin) || dek.size != DEK_LENGTH) return PinChange.FAILED
        val keySlot = activeKeySlot()
        val keptSalt = currentSalt()
        val salt = keptSalt ?: Crypto.randomBytes(PIN_SALT_SIZE)
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
            return PinChange.FAILED
        }
        return try {
            // Same key and salt: a duress slot this PIN opens would make it open two vaults.
            if (!keyReset && keptSalt != null && opensDecoySlot(keys.decoyKek, salt)) {
                return if (chargeMiss()) PinChange.IN_USE else PinChange.FAILED
            }
            val change = when {
                keyReset && keptSalt != null -> WipeSlotChange.TURN_OFF_AND_TELL
                keyReset || keptSalt == null -> WipeSlotChange.TURN_OFF
                else -> wipeSlotChangeFor(keys.wipeKek, salt)
            }
            // The duress slot survives only what the wipe slot survives: same salt, same key.
            val decoyChange = when {
                keyReset && keptSalt != null -> WipeSlotChange.TURN_OFF_AND_TELL
                keyReset || keptSalt == null -> WipeSlotChange.TURN_OFF
                else -> WipeSlotChange.KEEP
            }
            val written = writePinSlot(dek, keys.kek, salt, keySlot, SlotChanges(change, decoyChange))
            if (written) PinChange.SET else PinChange.FAILED
        } catch (_: CryptoException) {
            PinChange.FAILED
        } finally {
            SensitiveDataCleaner.clear(salt)
            keys.wipe()
        }
    }

    /**
     * One PIN attempt. [pin] stays the caller's to wipe. On [PinUnlock.Success] and
     * [PinUnlock.Erased] the caller owns the DEK and must wipe it.
     *
     * The KDF runs once and all three slots (PIN, wipe, duress) are always tried, so the PIN,
     * the wipe PIN, the duress PIN and a wrong PIN cost the same up to here. The duress PIN
     * returns [PinUnlock.Decoy] and changes nothing but the lockout, which it ends like the
     * PIN; the misses since the PIN was last right stay counted (see [penaltyFor]). The wipe
     * PIN is counted before the KDF like any attempt and is refused during a lockout; once it
     * matched, the erase drops the lockout like a right PIN does. In a PIN check inside the
     * open vault ([verifyPinAuth]) it is a wrong PIN.
     *
     * On the wipe PIN, before returning: phase 1 makes a new key in the other Keystore slot
     * ([PinKeySlot]), a new DEK, a new PIN slot that the wipe PIN opens (same salt, and the
     * PBKDF2 result already computed, so the slow half does not run twice) and a fresh decoy
     * wipe slot; phase 2 swaps them in with one atomic store edit that also drops the recovery,
     * backup-key and biometric slots and the lockout, and records [WipeStage.DATABASE].
     */
    fun unlockWithPin(pin: CharArray): PinUnlock = attempt(pin, Purpose.UNLOCK)

    /**
     * Whether [pin] is the PIN of the open session's vault; counts like an unlock attempt. [pin]
     * stays the caller's. With [decoy] (a session the duress PIN opened) only the duress PIN
     * matches, so a decoy session can not be used to test guesses of the real PIN. The wipe PIN
     * only wipes from the lock screen: here it is a wrong PIN.
     */
    fun verifyPinAuth(pin: CharArray, decoy: Boolean = false): Boolean {
        val result = attempt(pin, if (decoy) Purpose.VERIFY_DECOY else Purpose.VERIFY)
        return when (result) {
            is PinUnlock.Success -> {
                SensitiveDataCleaner.clear(result.dek)
                true
            }

            is PinUnlock.Decoy -> {
                SensitiveDataCleaner.clear(result.dek)
                true
            }

            else -> false
        }
    }

    /** One counted attempt; see [unlockWithPin] and [verifyPinAuth]. */
    @Suppress("ReturnCount") // Fail-closed gates: lockout, missing slot, unrecorded attempt.
    private fun attempt(pin: CharArray, purpose: Purpose): PinUnlock = synchronized(attemptLock) {
        if (remainingLockoutMillis() > 0) return PinUnlock.LockedOut
        val slot = store.getBytes(PIN_SLOT_KEY) ?: return PinUnlock.WrongPin
        try {
            val previous = Misses(store.getInt(FAILURES_KEY, 0), store.getInt(STRIKES_KEY, 0))
            // Counted before the KDF: an attempt the app is killed during still counts.
            if (!chargeAttempt(previous.next())) return PinUnlock.Failed
            if (!isWellFormed(pin)) return PinUnlock.WrongPin

            val parsed = parseSlot(slot) ?: return PinUnlock.WrongPin
            try {
                val keys = try {
                    derivePinKeys(pin, parsed.salt, activeKeySlot())
                } catch (e: PinFactorException) {
                    // Not a guess: the hardware never answered. Give the attempt back.
                    refundAttempt(previous)
                    return if (e.lost) PinUnlock.KeyLost else PinUnlock.Failed
                }
                try {
                    checkSlots(pin, parsed, keys, Check(purpose, previous.strikes))
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
     * counted as an attempt (the caller has the vault open and has checked the PIN), except
     * when refused for being another PIN; refused outright during a lockout.
     */
    @Suppress("ReturnCount") // Fail-closed gates before the slot is written.
    fun configureWipePin(wipePin: CharArray): WipePinChange = synchronized(attemptLock) {
        if (!isWellFormed(wipePin) || remainingLockoutMillis() > 0) return WipePinChange.FAILED
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
                    return if (chargeMiss()) WipePinChange.SAME_AS_PIN else WipePinChange.FAILED
                }
                // Nor may it be the duress PIN, which opens the decoy vault first.
                if (opensDecoySlot(keys.decoyKek, parsed.salt)) {
                    return if (chargeMiss()) WipePinChange.IN_USE else WipePinChange.FAILED
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
     * Makes [duressPin] the duress PIN of the vault whose DEK is [primaryDek] (the open session's
     * own; the caller checked the PIN). [duressPin] stays the caller's to wipe. Refused when it
     * is the PIN or the wipe PIN. The decoy vault's DEK is kept in `decoy.key`, sealed under a
     * key derived from [primaryDek], so a changed duress PIN opens the same decoy notes; when no
     * decoy vault exists yet a new DEK is made and [onNewVault] runs first (it deletes what may
     * be left of an earlier decoy database, which nothing can open any more). Counted and refused
     * during a lockout like [configureWipePin].
     */
    @Suppress("ReturnCount") // Fail-closed gates before the slot is written.
    fun configureDuressPin(duressPin: CharArray, primaryDek: ByteArray, onNewVault: () -> Boolean): DuressPinChange =
        synchronized(attemptLock) {
            if (!isWellFormed(duressPin) || primaryDek.size != DEK_LENGTH || remainingLockoutMillis() > 0) {
                return DuressPinChange.FAILED
            }
            val slot = store.getBytes(PIN_SLOT_KEY) ?: return DuressPinChange.FAILED
            val parsed = try {
                parseSlot(slot)
            } finally {
                SensitiveDataCleaner.clear(slot)
            } ?: return DuressPinChange.FAILED
            try {
                val keys = try {
                    derivePinKeys(duressPin, parsed.salt, activeKeySlot())
                } catch (_: PinFactorException) {
                    return DuressPinChange.FAILED
                }
                try {
                    val dek = openSlotOrNull(parsed, keys.kek)
                    if (dek != null) {
                        SensitiveDataCleaner.clear(dek)
                        return if (chargeMiss()) DuressPinChange.SAME_AS_PIN else DuressPinChange.FAILED
                    }
                    val wipeSlot = store.getBytes(WIPE_SLOT_KEY)
                    val isWipePin = wipeSlot != null &&
                        try {
                            opensWipeSlot(wipeSlot, keys.wipeKek, parsed.salt)
                        } finally {
                            SensitiveDataCleaner.clear(wipeSlot)
                        }
                    if (isWipePin) return if (chargeMiss()) DuressPinChange.IN_USE else DuressPinChange.FAILED
                    writeDuressSlot(keys.decoyKek, parsed.salt, primaryDek, onNewVault)
                } finally {
                    keys.wipe()
                }
            } finally {
                parsed.wipe()
            }
        }

    /**
     * Turns the duress PIN off and forgets the decoy vault's key: both entries become random
     * bytes again, as on a vault that never had one. The caller then deletes the decoy database.
     */
    fun removeDuressPin(): Boolean = synchronized(attemptLock) {
        val slot = randomSlot()
        val key = randomSlot()
        try {
            store.edit {
                putBytes(DECOY_SLOT_KEY, slot)
                putBytes(DECOY_KEY_KEY, key)
            }
        } finally {
            SensitiveDataCleaner.clear(slot)
            SensitiveDataCleaner.clear(key)
        }
    }

    /**
     * Whether the vault whose DEK is [primaryDek] has a decoy vault. Only that vault's own session
     * can tell: `decoy.key` is random bytes otherwise, and a decoy session has no [primaryDek].
     */
    fun hasDecoyVault(primaryDek: ByteArray): Boolean = decoyKeyState(primaryDek) != null

    /**
     * True when the vault whose DEK is [primaryDek] has a decoy vault whose duress PIN stopped
     * working: a new PIN slot with another key or salt (see [configurePin]) turned it off.
     * Setting it again opens the same decoy notes. Kept inside `decoy.key` itself (its AAD), so
     * the store shows nothing.
     */
    fun duressPinTurnedOff(primaryDek: ByteArray): Boolean = decoyKeyState(primaryDek) == false

    /** Whether `decoy.key` opens under [primaryDek] and the duress slot is live; null: no decoy vault. */
    private fun decoyKeyState(primaryDek: ByteArray): Boolean? {
        val key = openDecoyKeyOrNull(primaryDek) ?: return null
        SensitiveDataCleaner.clear(key.dek)
        return key.active
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
        // The duress slot and the decoy key the same way: random bytes until a duress PIN is set.
        val needsDuress = hasPinSlot && (!store.contains(DECOY_SLOT_KEY) || !store.contains(DECOY_KEY_KEY))
        if (!needsDecoy && !needsKeySlot && !needsDuress) return true
        val decoy = decoyWipeSlot()
        val duressSlot = randomSlot()
        val decoyKey = randomSlot()
        return try {
            store.edit {
                if (needsDecoy) putBytes(WIPE_SLOT_KEY, decoy)
                if (needsKeySlot) putInt(KEY_SLOT_KEY, PinKeySlot.A.ordinal)
                if (needsDuress) {
                    putBytes(DECOY_SLOT_KEY, duressSlot)
                    putBytes(DECOY_KEY_KEY, decoyKey)
                }
            }
        } finally {
            SensitiveDataCleaner.clear(decoy)
            SensitiveDataCleaner.clear(duressSlot)
            SensitiveDataCleaner.clear(decoyKey)
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
            removePrefix(DECOY_PREFIX)
            remove(WIPE_PENDING_KEY)
        }
        PinKeySlot.entries.forEach(factor::delete)
    }

    /**
     * Opens whichever slot [keys] open; see [unlockWithPin]. Settings never lets one PIN open two
     * slots; should it happen anyway, the order is PIN, duress, wipe: never the erase while a
     * PIN could also open a vault.
     */
    private fun checkSlots(pin: CharArray, parsed: ParsedSlot, keys: PinKeys, check: Check): PinUnlock {
        val dek = openSlotOrNull(parsed, keys.kek)
        // Both tried even when the PIN slot already opened: no early exit.
        val wipeSlot = store.getBytes(WIPE_SLOT_KEY)
        val wipeMatch = wipeSlot != null &&
            try {
                opensWipeSlot(wipeSlot, keys.wipeKek, parsed.salt)
            } finally {
                SensitiveDataCleaner.clear(wipeSlot)
            }
        val decoyDek = openDecoySlotOrNull(keys.decoyKek, parsed.salt)
        val matched = match(dek, decoyDek, wipeMatch, check.purpose)
        // Whatever was opened and not handed out is wiped here.
        if (dek != null && matched !is PinUnlock.Success) SensitiveDataCleaner.clear(dek)
        if (decoyDek != null && matched !is PinUnlock.Decoy) SensitiveDataCleaner.clear(decoyDek)
        return when (matched) {
            null -> eraseVault(pin, parsed.salt, keys)

            is PinUnlock.Success -> {
                store.edit {
                    removePrefix(LOCKOUT_PREFIX)
                    remove(STRIKES_KEY)
                }
                matched
            }

            // The running lockout ends as on the PIN, but the misses since the PIN was last right
            // stay: typing the duress PIN between guesses never buys more guesses at the PIN.
            is PinUnlock.Decoy -> {
                store.edit {
                    removePrefix(LOCKOUT_PREFIX)
                    putInt(STRIKES_KEY, check.strikes)
                }
                matched
            }

            else -> matched
        }
    }

    /** What [checkSlots] hands out for [purpose]; null is the wipe PIN's erase. */
    private fun match(dek: ByteArray?, decoyDek: ByteArray?, wipeMatch: Boolean, purpose: Purpose): PinUnlock? = when {
        dek != null && purpose != Purpose.VERIFY_DECOY -> PinUnlock.Success(dek)
        decoyDek != null && purpose != Purpose.VERIFY -> PinUnlock.Decoy(decoyDek)
        wipeMatch && purpose == Purpose.UNLOCK && dek == null && decoyDek == null -> null
        else -> PinUnlock.WrongPin
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
        val dek = Crypto.randomBytes(DEK_LENGTH)
        val decoy = decoyWipeSlot()
        // The decoy vault goes with the erase: its slot and key become random bytes, and its
        // database is deleted with the old one (see SecurityManager.completePendingWipe).
        val duressSlot = randomSlot()
        val decoyKey = randomSlot()
        return try {
            val slot = sealSlot(dek, kek, salt)
            val erased = try {
                store.edit {
                    putBytes(PIN_SLOT_KEY, slot)
                    putBytes(WIPE_SLOT_KEY, decoy)
                    putBytes(DECOY_SLOT_KEY, duressSlot)
                    putBytes(DECOY_KEY_KEY, decoyKey)
                    putInt(KEY_SLOT_KEY, keySlot.ordinal)
                    putInt(WIPE_PENDING_KEY, WipeStage.DATABASE.ordinal)
                    remove(WIPE_NOTICE_KEY)
                    remove(BIOMETRIC_ENABLED_KEY)
                    removePrefix(LOCKOUT_PREFIX)
                    remove(STRIKES_KEY)
                    ERASED_PREFIXES.forEach { removePrefix(it) }
                }
            } finally {
                SensitiveDataCleaner.clear(slot)
            }
            if (erased) PinUnlock.Erased(dek.copyOf()) else PinUnlock.Failed
        } catch (_: CryptoException) {
            PinUnlock.Failed
        } finally {
            SensitiveDataCleaner.clear(dek)
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(decoy)
            SensitiveDataCleaner.clear(duressSlot)
            SensitiveDataCleaner.clear(decoyKey)
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
        changes: SlotChanges,
    ): Boolean {
        val (change, decoyChange) = changes
        val slot = sealSlot(dek, kek, salt)
        val decoy = if (change == WipeSlotChange.KEEP) null else decoyWipeSlot()
        val duressSlot = if (decoyChange == WipeSlotChange.KEEP) null else randomSlot()
        // A dead duress slot is recorded in `decoy.key` (sealed again as turned off); without a
        // decoy vault it gets fresh random bytes instead, so the entry changes either way. A first
        // PIN (no salt yet) starts a vault without any decoy key either.
        val decoyKey = if (duressSlot == null && store.contains(DECOY_KEY_KEY)) null else turnedOffDecoyKey(dek)
        return try {
            store.edit {
                putBytes(PIN_SLOT_KEY, slot)
                decoy?.let { putBytes(WIPE_SLOT_KEY, it) }
                duressSlot?.let { putBytes(DECOY_SLOT_KEY, it) }
                decoyKey?.let { putBytes(DECOY_KEY_KEY, it) }
                putInt(KEY_SLOT_KEY, keySlot.ordinal)
                putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal)
                if (change == WipeSlotChange.TURN_OFF_AND_TELL) putBoolean(WIPE_NOTICE_KEY, true)
                removePrefix(LOCKOUT_PREFIX)
                remove(STRIKES_KEY)
            }
        } finally {
            SensitiveDataCleaner.clear(slot)
            decoy?.let(SensitiveDataCleaner::clear)
            duressSlot?.let(SensitiveDataCleaner::clear)
            decoyKey?.let(SensitiveDataCleaner::clear)
        }
    }

    /**
     * Seals the duress slot (and the decoy key) for the PIN whose duress KEK is [decoyKek]; see
     * [configureDuressPin].
     */
    @Suppress("ReturnCount")
    private fun writeDuressSlot(
        decoyKek: ByteArray,
        salt: ByteArray,
        primaryDek: ByteArray,
        onNewVault: () -> Boolean,
    ): DuressPinChange {
        val kept = openDecoyKeyOrNull(primaryDek)?.dek
        // No decoy vault yet: whatever an earlier one left can never open again, so it goes first.
        if (kept == null && !onNewVault()) return DuressPinChange.FAILED
        val decoyDek = kept ?: Crypto.randomBytes(DEK_LENGTH)
        return try {
            val duressSlot = sealDecoy(decoyDek, decoyKek, decoySlotAad(salt))
            val decoyKey = sealDecoyKey(decoyDek, primaryDek)
            val written = try {
                store.edit {
                    putBytes(DECOY_SLOT_KEY, duressSlot)
                    putBytes(DECOY_KEY_KEY, decoyKey)
                }
            } finally {
                SensitiveDataCleaner.clear(duressSlot)
                SensitiveDataCleaner.clear(decoyKey)
            }
            when {
                !written -> DuressPinChange.FAILED
                kept == null -> DuressPinChange.SET_NEW_VAULT
                else -> DuressPinChange.SET
            }
        } catch (_: CryptoException) {
            DuressPinChange.FAILED
        } finally {
            SensitiveDataCleaner.clear(decoyDek)
        }
    }

    private fun writeWipeSlot(wipeKek: ByteArray, salt: ByteArray): WipePinChange {
        val wipeSlot = try {
            sealWipeSlot(wipeKek, salt)
        } catch (_: CryptoException) {
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

    private fun chargeAttempt(misses: Misses): Boolean = store.edit {
        putInt(FAILURES_KEY, misses.failures)
        putInt(STRIKES_KEY, misses.strikes)
        val penalty = penaltyFor(misses.failures, misses.strikes)
        if (penalty > 0L) {
            putLong(PENALTY_KEY, penalty)
            putLong(ANCHOR_KEY, clock.elapsedRealtime())
            putInt(BOOT_KEY, clock.bootCount())
        } else {
            remove(PENALTY_KEY)
        }
    }

    /**
     * A refused PIN in Settings because it is another PIN (the PIN, wipe or duress PIN): it
     * tells what that PIN is, so it costs a miss like a wrong guess, charged before the answer
     * is returned.
     */
    private fun chargeMiss(): Boolean =
        chargeAttempt(Misses(store.getInt(FAILURES_KEY, 0), store.getInt(STRIKES_KEY, 0)).next())

    private fun refundAttempt(previous: Misses) {
        store.edit {
            putInt(FAILURES_KEY, previous.failures)
            putInt(STRIKES_KEY, previous.strikes)
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
                decoyKek = Hkdf.sha256(ikm = ikm, salt = salt, info = DECOY_INFO, length = DEK_LENGTH),
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

    private fun pbkdf2(pin: CharArray, salt: ByteArray): ByteArray =
        Crypto.pbkdf2HmacSha256(pin, salt, PBKDF2_ITERATIONS, DEK_LENGTH)

    /** Whether [decoyKek] opens the duress slot; the DEK it holds is wiped at once. */
    private fun opensDecoySlot(decoyKek: ByteArray, salt: ByteArray): Boolean {
        val dek = openDecoySlotOrNull(decoyKek, salt) ?: return false
        SensitiveDataCleaner.clear(dek)
        return true
    }

    /** The decoy DEK in the duress slot, or null when [decoyKek] does not open it. */
    private fun openDecoySlotOrNull(decoyKek: ByteArray, salt: ByteArray): ByteArray? {
        val slot = store.getBytes(DECOY_SLOT_KEY) ?: return null
        return try {
            openDecoy(slot, decoyKek, decoySlotAad(salt))
        } finally {
            SensitiveDataCleaner.clear(slot)
        }
    }

    /** The decoy DEK sealed under [primaryDek] in `decoy.key`, or null when there is none. */
    private fun openDecoyKeyOrNull(primaryDek: ByteArray): DecoyKey? {
        val sealed = store.getBytes(DECOY_KEY_KEY) ?: return null
        val key = decoyKeyKek(primaryDek)
        return try {
            // Both tried, as the attempt tries all slots: one AES-GCM open more is no tell here.
            val active = openDecoy(sealed, key, DECOY_KEY_AAD)
            val off = openDecoy(sealed, key, DECOY_KEY_OFF_AAD)
            when {
                active != null -> DecoyKey(active, active = true)
                off != null -> DecoyKey(off, active = false)
                else -> null
            }
        } finally {
            SensitiveDataCleaner.clear(sealed)
            SensitiveDataCleaner.clear(key)
        }
    }

    private fun sealDecoyKey(decoyDek: ByteArray, primaryDek: ByteArray, active: Boolean = true): ByteArray {
        val key = decoyKeyKek(primaryDek)
        return try {
            sealDecoy(decoyDek, key, if (active) DECOY_KEY_AAD else DECOY_KEY_OFF_AAD)
        } finally {
            SensitiveDataCleaner.clear(key)
        }
    }

    /** `decoy.key` for a dead duress slot: the decoy DEK sealed as turned off, or random bytes. */
    private fun turnedOffDecoyKey(primaryDek: ByteArray): ByteArray {
        val decoyDek = openDecoyKeyOrNull(primaryDek)?.dek ?: return randomSlot()
        return try {
            sealDecoyKey(decoyDek, primaryDek, active = false)
        } finally {
            SensitiveDataCleaner.clear(decoyDek)
        }
    }

    /** The decoy DEK from `decoy.key`, the caller's to wipe; [active]: the duress slot opens it. */
    private class DecoyKey(val dek: ByteArray, val active: Boolean)

    /** Both KEKs of one KDF run, and its PBKDF2 half (reused if the vault is erased). */
    private class PinKeys(
        val keySlot: PinKeySlot,
        val stretched: ByteArray,
        val kek: ByteArray,
        val wipeKek: ByteArray,
        val decoyKek: ByteArray,
    ) {
        fun wipe() {
            SensitiveDataCleaner.clear(stretched)
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(wipeKek)
            SensitiveDataCleaner.clear(decoyKek)
        }
    }

    /** What a counted PIN attempt is for: which matches it hands out. */
    private enum class Purpose {
        /** The lock screen: the PIN, the duress PIN, or the wipe PIN's erase. */
        UNLOCK,

        /** A re-check inside the real vault: the PIN only. */
        VERIFY,

        /** A re-check inside the decoy vault: the duress PIN only. */
        VERIFY_DECOY,
    }

    /** What one counted attempt checks for, and the strikes before it was charged. */
    private class Check(val purpose: Purpose, val strikes: Int)

    /**
     * The miss counters: [failures] since any vault last opened (the lockout as the user sees
     * it), [strikes] since the PIN itself was last right. Only the PIN, a new PIN slot and the
     * erase clear [strikes]; see [penaltyFor].
     */
    private data class Misses(val failures: Int, val strikes: Int) {
        fun next() = Misses(failures + 1, strikes + 1)
    }

    /** What a new PIN slot does to the wipe slot (see [configurePin]). */
    private enum class WipeSlotChange { KEEP, TURN_OFF, TURN_OFF_AND_TELL }

    /** What a new PIN slot does to the wipe slot and to the duress slot. */
    private data class SlotChanges(val wipe: WipeSlotChange, val decoy: WipeSlotChange)

    companion object {
        private const val PIN_PREFIX = "pin."
        private const val PIN_SLOT_KEY = "pin.slot"
        private const val WIPE_SLOT_KEY = "pin.wipe.slot"
        private const val WIPE_NOTICE_KEY = "pin.wipe.notice"
        private const val KEY_SLOT_KEY = "pin.key_slot"
        private const val WIPE_PENDING_KEY = "wipe.pending"
        private const val DECOY_SLOT_KEY = "pin.decoy.slot"
        private const val DECOY_PREFIX = "decoy."
        private const val DECOY_KEY_KEY = "decoy.key"
        private const val AUTH_PREFIX = "auth."
        private const val AUTH_TYPE_KEY = "auth.type"
        private const val BIOMETRIC_ENABLED_KEY = "auth.biometric_enabled"
        private const val LOCKOUT_PREFIX = "lockout."
        private const val FAILURES_KEY = "lockout.failures"
        private const val PENALTY_KEY = "lockout.penalty_ms"
        private const val ANCHOR_KEY = "lockout.anchor_elapsed"
        private const val BOOT_KEY = "lockout.boot"

        /** Outside [LOCKOUT_PREFIX]: the duress PIN clears that prefix, never this; see [Misses]. */
        private const val STRIKES_KEY = "pin.strikes"

        const val MAX_ATTEMPTS = 5
        const val FIRST_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 24 * 60 * 60_000L

        /**
         * Misses since the PIN was last right that are free of the lockout on top of
         * [MAX_ATTEMPTS], for typos around the duress PIN: past them each miss is locked out
         * even right after the duress PIN opened the decoy vault.
         */
        const val STRIKE_GRACE = 5
        private const val MAX_DOUBLINGS = 12

        private const val PBKDF2_ITERATIONS = 600_000

        private val KEK_INFO = "encly/pin/kek/v3".toByteArray(Charsets.UTF_8)
        private val WIPE_INFO = "encly/pin/wipe/v3".toByteArray(Charsets.UTF_8)
        private val DECOY_INFO = "encly/pin/decoy/v3".toByteArray(Charsets.UTF_8)

        /** Every other way back to an erased vault's DEK: recovery, backup-key and biometric slots. */
        private val ERASED_PREFIXES = listOf(
            SeedPhraseManager.RECOVERY_PREFIX,
            SeedPhraseManager.BACKUP_PREFIX,
            BiometricManager.SLOT_PREFIX,
        )

        /**
         * The lockout after [failures] consecutive misses: none before [MAX_ATTEMPTS], then
         * 30 s, doubling each miss (1 min, 2, 4, 8, 16, 32 min, ~1 h, ~2 h ...) up to 24 h.
         * [strikes] (misses since the PIN was last right) count too, less [STRIKE_GRACE].
         */
        fun penaltyFor(failures: Int, strikes: Int = failures): Long {
            val misses = maxOf(failures, strikes - STRIKE_GRACE)
            if (misses < MAX_ATTEMPTS) return 0L
            val doublings = (misses - MAX_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
        }
    }
}
