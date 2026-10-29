package com.pasich.encly.core.security

import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.security.bip39.Bip39
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what recovery phrases already given to users rely on: the official BIP-39 vectors, the
 * accept/reject rules of the two validators, and the bytes Encly derives from the words.
 */
class Bip39GoldenTest {
    private val seeds = SeedPhraseManager(VaultStore(tempVaultFile()))
    private val abandon = List(11) { "abandon" }.joinToString(" ") + " about"

    private fun vectors(): List<Pair<ByteArray, String>> {
        val text = checkNotNull(javaClass.classLoader!!.getResourceAsStream("bip39-english-vectors.txt")).use {
            it.readBytes().decodeToString()
        }
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map {
            val (entropy, mnemonic) = it.split(' ', limit = 2)
            entropy.chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray() to mnemonic
        }
    }

    private fun encode(entropy: ByteArray): String = String(Bip39().encode(entropy))

    private fun strict(phrase: String) = RecoveryWords.isValidPhrase(phrase.toCharArray())

    private fun lenient(phrase: String) = seeds.isValidMnemonic(phrase.toCharArray())

    @Test
    fun officialVectorsEncode() {
        val all = vectors()
        assertEquals(24, all.size)
        all.forEach { (entropy, mnemonic) -> assertEquals(mnemonic, encode(entropy)) }
    }

    @Test
    fun officialVectorsDecode() {
        vectors().forEach { (entropy, mnemonic) ->
            assertArrayEquals(mnemonic, entropy, Bip39().decode(mnemonic.toCharArray()))
        }
    }

    @Test
    fun entropyOfEverySizeRoundTrips() {
        listOf(16, 20, 24, 28, 32).forEach { size ->
            val entropy = ByteArray(size) { (it * 7 + size).toByte() }
            val phrase = Bip39().encode(entropy)
            assertEquals(size * 3 / 4, String(phrase).split(' ').size)
            assertArrayEquals(entropy, Bip39().decode(phrase))
        }
    }

    @Test
    fun oddEntropySizesAreRefused() {
        listOf(0, 15, 17, 33).forEach { size ->
            assertThrows(IllegalArgumentException::class.java) { Bip39().encode(ByteArray(size)) }
        }
    }

    @Test
    fun decodeWipesNothingItWasGiven() {
        val phrase = abandon.toCharArray()
        Bip39().decode(phrase)
        assertEquals(abandon, String(phrase))
    }

    @Test
    fun officialVectorsValidate() {
        vectors().forEach { (_, mnemonic) ->
            assertTrue(mnemonic, strict(mnemonic))
            assertTrue(mnemonic, lenient(mnemonic))
        }
    }

    @Test
    fun generatedPhrasesAreTwelveValidWords() {
        repeat(20) {
            val phrase = String(seeds.generateMnemonic())
            assertEquals(12, phrase.split(' ').size)
            assertTrue(strict(phrase))
        }
    }

    @Test
    fun badChecksumIsRejected() {
        val swapped = abandon.replaceAfterLast(' ', "abandon")
        assertFalse(strict(swapped))
        assertFalse(lenient(swapped))
    }

    @Test
    fun unknownWordIsRejected() {
        val unknown = abandon.replaceAfterLast(' ', "aboot")
        assertFalse(strict(unknown))
        assertFalse(lenient(unknown))
        assertFalse(strict(abandon.replaceAfterLast(' ', "abo")))
    }

    @Test
    fun wrongWordCountIsRejected() {
        val words = abandon.split(' ')
        listOf(0, 1, 11, 13, 14, 25).forEach { count ->
            val phrase = List(count) { words[it % 11] }.joinToString(" ")
            assertFalse("$count", strict(phrase))
            assertFalse("$count", lenient(phrase))
        }
    }

    @Test
    fun strictValidatorWantsLowerCaseAndSingleSpaces() {
        assertTrue(strict(abandon))
        assertFalse(strict(abandon.uppercase()))
        assertFalse(strict(abandon.replaceFirstChar { it.uppercase() }))
        assertFalse(strict(abandon.replaceFirst(" ", "  ")))
        assertFalse(strict(" $abandon"))
        assertFalse(strict("$abandon "))
        assertFalse(strict(abandon.replaceFirst(" ", "\n")))
    }

    @Test
    fun lenientValidatorNormalizesCaseAndWhitespace() {
        assertTrue(lenient(abandon.uppercase()))
        assertTrue(lenient("  ${abandon.replace(" ", " \t\n ")}  "))
        assertTrue(lenient(abandon.replaceFirstChar { it.uppercase() }))
    }

    @Test
    fun nonAsciiLookalikesAreRejected() {
        assertFalse(lenient(abandon.replaceFirst('a', 'а')))
        assertFalse(lenient(abandon.replaceFirst("abandon", "abandon­")))
    }

    @Test
    fun seedHashIsPinned() {
        assertEquals(SEED_HASH, hex(Mnemonic.seedHash(abandon.toCharArray())))
        assertEquals(SEED_HASH, hex(Mnemonic.seedHash("  ${abandon.uppercase()}\n".toCharArray())))
    }

    @Test
    fun backupRootIsPinned() {
        assertEquals(BACKUP_ROOT, hex(BackupKeys.rootFromMnemonic(abandon.toCharArray())))
    }

    @Test
    fun recoverySlotOpensWithTheSameWords() {
        assertTrue(seeds.initializeVault(abandon.toCharArray()))
        val dek = checkNotNull(seeds.copyBootstrapKey())

        assertArrayEquals(dek, seeds.unlockWithSeed(abandon.uppercase().toCharArray()))
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val SEED_HASH = "c557eec878dfd852ba3f88087c4f350f09c55537ab5e549c3cd14320ec3cef38"
        const val BACKUP_ROOT = "1a33cffc42964db0c0dd84e985bc0d0acfd3264809e289e8a85700cc38cfeb63"
    }
}
