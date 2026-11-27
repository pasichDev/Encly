package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.PinUnlock
import com.pasich.encly.core.security.SlotHeader
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * The whole version 4 chain against values made by OTHER software: the Argon2id of OpenSSL 4.1, its HMAC and HKDF,
 * Python's `unicodedata` for NFC and `cryptography` for AES-256-GCM (the generator is
 * `golden_v4_chain.py`; nothing here was computed by this code or by Bouncy Castle). A password typed in NFC and in
 * NFD must open the same slot.
 *
 * Password -> Argon2id -> `sw`; `hw` = HMAC(device secret, "encly/secret/hw/v4" ‖ salt ‖ pw); the KEKs by HKDF over
 * `sw ‖ hw`; the three slots sealed by AES-GCM with a fixed IV. The main slot is the password's, the wipe slot the
 * wipe secret's, the duress slot the duress secret's: three secrets, one salt and one device secret.
 */
class GoldenV4ChainTest {
    private val fixture = PolicyFixture(DesktopPasswordPolicy())
    private val policy = DesktopPasswordPolicy()
    private val store = fixture.store
    private val auth = fixture.auth

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    private fun load() {
        fixture.secrets.put(PinKeySlot.A, hex(DEVICE))
        store.edit {
            putBytes("pin.slot", hex(MAIN))
            putBytes("pin.wipe.slot", hex(WIPE))
            putBytes("pin.decoy.slot", hex(DURESS))
            putInt("pin.key_slot", PinKeySlot.A.ordinal)
        }
    }

    private val header = SlotHeader(4, 2, 1, 65_536, 3, 1, hex(SALT))

    @Test
    fun theStretchIsTheArgon2idOfOpenSslOverTheNfcBytes() {
        val key = policy.stretch(policy.canonical(NFC.toCharArray())!!, header)

        assertEquals(SW, key.toHex())
    }

    @Test
    fun theFactorInputGivesTheHmacOpenSslMade() {
        fixture.secrets.put(PinKeySlot.A, hex(DEVICE))
        val factor = DesktopPinFactor(fixture.secrets)

        val input = policy.factorInput(policy.canonical(NFC.toCharArray())!!, hex(SALT))

        assertEquals(PASSWORD_UTF8, input.copyOfRange(18 + 16, input.size).toHex())
        assertEquals(HW, factor.mac(PinKeySlot.A, input).toHex())
    }

    @Test
    fun nfcAndNfdAreTheSameBytesAndTheTwoFormsDifferInTheirUnits() {
        val nfdForm = Normalizer.normalize(NFC, Normalizer.Form.NFD)
        assertEquals(NFD, nfdForm)
        assertNotEquals(NFC.length, NFD.length)

        assertEquals(PASSWORD_UTF8, PasswordCanonical.utf8(policy.canonical(NFC.toCharArray())!!).toHex())
        assertEquals(PASSWORD_UTF8, PasswordCanonical.utf8(policy.canonical(NFD.toCharArray())!!).toHex())
    }

    @Test
    fun theSlotsOpenWithTheKeysOfTheOtherImplementation() {
        load()

        val main = assertIs<PinUnlock.Success>(auth.unlockWithPin(NFC.toCharArray()))
        assertEquals(DEK, main.dek.toHex())
        assertContentEquals(hex(DEK), main.dek)
    }

    @Test
    fun theSamePasswordTypedInNfdOpensTheSameSlot() {
        load()

        val main = assertIs<PinUnlock.Success>(auth.unlockWithPin(NFD.toCharArray()))

        assertEquals(DEK, main.dek.toHex())
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun theDuressSecretOpensTheDecoyVaultOfTheOtherImplementation() {
        load()

        val decoy = assertIs<PinUnlock.Decoy>(auth.unlockWithPin(DURESS_SECRET.toCharArray()))

        assertEquals(DECOY_DEK, decoy.dek.toHex())
    }

    @Test
    fun theWipeSecretOpensTheWipeSlotOfTheOtherImplementationAndErases() {
        load()

        val erased = assertIs<PinUnlock.Erased>(auth.unlockWithPin(WIPE_SECRET.toCharArray()))

        assertNotEquals(DEK, erased.dek.toHex())
        // The erase reused the stretched half of this very attempt and made a new device key in slot B: the new slot
        // opens with the wipe secret, and the old password does not any more.
        assertEquals(PinKeySlot.B.ordinal, store.getInt("pin.key_slot", -1))
        val reopened = assertIs<PinUnlock.Success>(auth.unlockWithPin(WIPE_SECRET.toCharArray()))
        assertContentEquals(erased.dek, reopened.dek)
    }

    private companion object {
        const val NFC =
            "P\u0159\u00EDli\u0161 \u017Elu\u0165ou\u010Dk\u00FD \u043A\u0456\u0442 \u65E5\u672C\u8A9E \uD83D\uDE00"
        const val NFD =
            "Pr\u030Ci\u0301lis\u030C z\u030Clut\u030Couc\u030Cky\u0301 " +
                "\u043A\u0456\u0442 \u65E5\u672C\u8A9E \uD83D\uDE00"
        const val WIPE_SECRET = "wipe secret phrase one"
        const val DURESS_SECRET = "duress secret phrase two"

        const val PASSWORD_UTF8 =
            "50c599c3ad6c69c5a120c5be6c75c5a56f75c48d6bc3bd20d0bad196d18220e697a5e69cace8aa9e20f09f9880"
        const val SALT = "000306090c0f1215181b1e2124272a2d"
        const val DEVICE = "909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        const val DEK = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        const val DECOY_DEK = "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f"
        const val SW = "d1af33a417b153caebe0add60275ef4b1e86ca2b32b338fc2f95a097061cfd1a"
        const val HW = "f5bcbeae09d9daa7ce1f0a16b02684d2095da7bf6e80bc1bcb3d447ba76c8802"
        const val MAIN =
            "040201000100000301000306090c0f1215181b1e2124272a2d202122232425262728292a2b06b6c174cbba447a" +
                "2dbb0d72ed4349c9a6f502d86b0aa4507e188fc0d42570cb85927a668954d2cd3715a95119145852"
        const val WIPE =
            "04202122232425262728292a2b042e356047b03155f262e5bb4d1893f4326606550701db97324ba26922a6eaa4" +
                "d1dbf76f230ff7a834491ccc8dd06207"
        const val DURESS =
            "04202122232425262728292a2b4d706702ac564ea7a83e3ee50dc0346288c6ec2a546342bd7f4e5fbef19f90ca" +
                "44f494b04f2a8793163fae48858dd757"
    }
}
