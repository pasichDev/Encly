package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinSlots.DEK_LENGTH
import com.pasich.encly.core.security.PinSlots.GCM_TAG_BYTES
import com.pasich.encly.core.security.PinSlots.IV_LENGTH
import com.pasich.encly.core.security.PinSlots.PIN_SALT_SIZE

/**
 * The slot formats by version, for [AuthenticationManager]: version 3 (the PIN, see [PinSlots] and [DuressSlots], which
 * every v3 branch here calls and does not repeat) and version 4 (the password).
 *
 * The main slot v4, 85 bytes: `0x04 ‖ kdf id ‖ factor id ‖ memory KiB (u32 BE) ‖ passes ‖ lanes ‖ salt(16) ‖
 * iv(12) ‖ AES-GCM(dek)(48)`, with `"encly/secret/slot/v4" ‖ bytes[0..25)` as AAD. The wipe slot and the duress slot
 * v4 are `0x04 ‖ iv(12) ‖ ct(48)` (61 bytes, like v3), their AAD `"encly/secret/wipe-slot/v4"` or
 * `"encly/secret/decoy-slot/v4"` followed by the main slot's bytes `[0..25)`: the version, the ids, the costs and the
 * salt, all authenticated. The decoy key (`decoy.key`) is not here: it comes from the DEK and stays version 3.
 *
 * Reading only cuts a slot of exactly the right size at fixed offsets: nothing the slot says sets an allocation, and
 * what its header asks for is the bound policy's to accept ([UnlockSecretPolicy.accepts]) before it is stretched.
 */
internal object SecretSlots {
    const val V4: Byte = SlotHeader.PASSWORD_VERSION
    private const val HEADER_V4_LENGTH = 1 + 1 + 1 + 4 + 1 + 1 + PIN_SALT_SIZE
    private const val MAIN_V4_LENGTH = HEADER_V4_LENGTH + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES
    private const val MEMORY_OFFSET = 3
    private const val MEMORY_BYTES = 4
    private const val PASSES_OFFSET = MEMORY_OFFSET + MEMORY_BYTES
    private const val LANES_OFFSET = PASSES_OFFSET + 1
    private const val SALT_OFFSET = LANES_OFFSET + 1
    private const val WIPE_MARKER_LENGTH = 32
    private const val BYTE_MASK = 0xFF
    private const val BYTE_MAX = 0xFF
    private const val MEMORY_MAX = 0xFFFF_FFFFL

    private val MAIN_AAD = "encly/secret/slot/v4".encodeToByteArray()
    private val WIPE_AAD = "encly/secret/wipe-slot/v4".encodeToByteArray()
    private val DECOY_AAD = "encly/secret/decoy-slot/v4".encodeToByteArray()

    /**
     * The main slot cut apart, or null when it is neither a version 3 slot of 77 bytes nor a version 4 slot of 85
     * bytes. The parts are the slot's own copies; [Parsed.wipe] wipes them.
     */
    fun parse(slot: ByteArray): Parsed? {
        if (slot.isEmpty()) return null
        return when (slot[0]) {
            // A version 3 slot has no costs of its own: its header is the PIN's (PBKDF2, the OS-store factor).
            PinSlots.SLOT_VERSION -> PinSlots.parseSlot(slot)?.let { v3 ->
                val header = SlotHeader(
                    version = PinSlots.SLOT_VERSION,
                    kdfId = SlotHeader.KDF_PBKDF2,
                    factorId = SlotHeader.FACTOR_OS_STORE_HMAC,
                    salt = v3.salt,
                )
                Parsed(header, v3.iv, v3.ciphertext)
            }

            V4 -> parseV4(slot)

            else -> null
        }
    }

    private fun parseV4(slot: ByteArray): Parsed? {
        if (slot.size != MAIN_V4_LENGTH) return null
        var memory = 0L
        for (index in MEMORY_OFFSET until MEMORY_OFFSET + MEMORY_BYTES) {
            memory = (memory shl Byte.SIZE_BITS) or (slot[index].toInt() and BYTE_MASK).toLong()
        }
        val header = SlotHeader(
            version = V4,
            kdfId = slot[1].toInt() and BYTE_MASK,
            factorId = slot[2].toInt() and BYTE_MASK,
            memoryKiB = memory,
            passes = slot[PASSES_OFFSET].toInt() and BYTE_MASK,
            lanes = slot[LANES_OFFSET].toInt() and BYTE_MASK,
            salt = slot.copyOfRange(SALT_OFFSET, SALT_OFFSET + PIN_SALT_SIZE),
        )
        val ciphertextStart = HEADER_V4_LENGTH + IV_LENGTH
        return Parsed(
            header,
            slot.copyOfRange(HEADER_V4_LENGTH, ciphertextStart),
            slot.copyOfRange(ciphertextStart, slot.size),
        )
    }

    /** `header ‖ iv ‖ AES-GCM(dek)` in the format of [header]'s version. */
    fun seal(dek: ByteArray, kek: ByteArray, header: SlotHeader): ByteArray = when (header.version) {
        PinSlots.SLOT_VERSION -> PinSlots.sealSlot(dek, kek, header.salt)

        V4 -> {
            val head = headerBytes(header)
            val iv = Crypto.randomBytes(IV_LENGTH)
            try {
                val encrypted = Crypto.aesGcmSeal(kek, iv, MAIN_AAD + head, dek)
                try {
                    head + iv + encrypted
                } finally {
                    SensitiveDataCleaner.clear(encrypted)
                }
            } finally {
                SensitiveDataCleaner.clear(iv)
            }
        }

        else -> throw IllegalArgumentException("Unknown slot version ${header.version}")
    }

    /** The DEK in [slot], or null when [kek] does not open it (a wrong secret). The caller wipes the result. */
    fun open(slot: Parsed, kek: ByteArray): ByteArray? = when (slot.header.version) {
        PinSlots.SLOT_VERSION ->
            PinSlots.openSlotOrNull(PinSlots.ParsedSlot(slot.salt, slot.iv, slot.ciphertext), kek)

        else -> Crypto.aesGcmOpen(kek, slot.iv, MAIN_AAD + headerBytes(slot.header), slot.ciphertext)
    }

    /** The wipe slot for the main slot with [header]. */
    fun sealWipe(wipeKek: ByteArray, header: SlotHeader): ByteArray = when (header.version) {
        PinSlots.SLOT_VERSION -> PinSlots.sealWipeSlot(wipeKek, header.salt)

        else -> {
            val marker = Crypto.randomBytes(WIPE_MARKER_LENGTH)
            try {
                DuressSlots.sealDecoy(marker, wipeKek, WIPE_AAD + headerBytes(header), V4)
            } finally {
                SensitiveDataCleaner.clear(marker)
            }
        }
    }

    /** Whether [wipeKek] authenticates [wipeSlot] for the main slot with [header]; a filler never does. */
    fun opensWipe(wipeSlot: ByteArray, wipeKek: ByteArray, header: SlotHeader): Boolean = when (header.version) {
        PinSlots.SLOT_VERSION -> PinSlots.opensWipeSlot(wipeSlot, wipeKek, header.salt)

        else -> {
            val marker = DuressSlots.openDecoy(wipeSlot, wipeKek, WIPE_AAD + headerBytes(header), V4)
            if (marker != null) SensitiveDataCleaner.clear(marker)
            marker != null
        }
    }

    /** The duress slot for the main slot with [header]. */
    fun sealDuress(decoyDek: ByteArray, decoyKek: ByteArray, header: SlotHeader): ByteArray = when (header.version) {
        PinSlots.SLOT_VERSION -> DuressSlots.sealDecoy(decoyDek, decoyKek, DuressSlots.decoySlotAad(header.salt))
        else -> DuressSlots.sealDecoy(decoyDek, decoyKek, DECOY_AAD + headerBytes(header), V4)
    }

    /** The decoy DEK in [duressSlot] for the main slot with [header], or null when [decoyKek] does not open it. */
    fun openDuress(duressSlot: ByteArray, decoyKek: ByteArray, header: SlotHeader): ByteArray? = when (header.version) {
        PinSlots.SLOT_VERSION ->
            DuressSlots.openDecoy(duressSlot, decoyKek, DuressSlots.decoySlotAad(header.salt))

        else -> DuressSlots.openDecoy(duressSlot, decoyKek, DECOY_AAD + headerBytes(header), V4)
    }

    /** Random bytes shaped like the wipe slot of [version]: no secret opens it. */
    fun wipeFiller(version: Byte): ByteArray =
        if (version == PinSlots.SLOT_VERSION) PinSlots.decoyWipeSlot() else DuressSlots.randomSlot(version)

    /** The 25 bytes a version 4 slot starts with, and its three AADs end with. */
    private fun headerBytes(header: SlotHeader): ByteArray {
        require(
            header.version == V4 &&
                header.kdfId in 0..BYTE_MAX &&
                header.factorId in 0..BYTE_MAX &&
                header.memoryKiB in 0..MEMORY_MAX &&
                header.passes in 0..BYTE_MAX &&
                header.lanes in 0..BYTE_MAX &&
                header.salt.size == PIN_SALT_SIZE,
        ) { "Not a version 4 slot header" }
        val bytes = ByteArray(HEADER_V4_LENGTH)
        bytes[0] = V4
        bytes[1] = header.kdfId.toByte()
        bytes[2] = header.factorId.toByte()
        for (index in 0 until MEMORY_BYTES) {
            val shift = Byte.SIZE_BITS * (MEMORY_BYTES - 1 - index)
            bytes[MEMORY_OFFSET + index] = (header.memoryKiB shr shift).toByte()
        }
        bytes[PASSES_OFFSET] = header.passes.toByte()
        bytes[LANES_OFFSET] = header.lanes.toByte()
        header.salt.copyInto(bytes, SALT_OFFSET)
        return bytes
    }

    /** A main slot cut apart: its [header], the [iv] and the sealed DEK. */
    class Parsed(val header: SlotHeader, val iv: ByteArray, val ciphertext: ByteArray) {
        val salt: ByteArray get() = header.salt

        fun wipe() {
            SensitiveDataCleaner.clear(header.salt)
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }
}
