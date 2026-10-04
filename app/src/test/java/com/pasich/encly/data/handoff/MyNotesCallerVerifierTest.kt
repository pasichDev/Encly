package com.pasich.encly.data.handoff

import com.pasich.encly.data.handoff.MyNotesCallerVerifier.Companion.MY_NOTES_PACKAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class MyNotesCallerVerifierTest {

    private val myNotesCert = "my notes release certificate".toByteArray()
    private val otherCert = "someone else's certificate".toByteArray()
    private val pinned = setOf(sha256Hex(myNotesCert))

    /** A package manager that knows one installed package and its signers. */
    private class FakeSignatures(
        override val sdkInt: Int,
        private val installed: String? = MY_NOTES_PACKAGE,
        private val signers: List<ByteArray> = emptyList(),
    ) : PackageSignatures {
        val queried = mutableListOf<String>()

        override fun hasSigningCertificateSha256(packageName: String, sha256: ByteArray): Boolean {
            queried += packageName
            return packageName == installed && signers.any { MessageDigest.isEqual(digest(it), sha256) }
        }

        override fun legacySignatures(packageName: String): List<ByteArray>? {
            queried += packageName
            return if (packageName == installed) signers else null
        }
    }

    private fun verifier(signatures: PackageSignatures) = MyNotesCallerVerifier(signatures, pinned)

    @Test
    fun myNotesWithThePinnedCertificateIsTrusted() {
        assertTrue(verifier(FakeSignatures(API_28, signers = listOf(myNotesCert))).isTrusted(MY_NOTES_PACKAGE))
        assertTrue(verifier(FakeSignatures(API_34, signers = listOf(myNotesCert))).isTrusted(MY_NOTES_PACKAGE))
    }

    @Test
    fun anotherOrMissingCallerIsRefusedWithoutAPackageLookup() {
        val signatures = FakeSignatures(API_34, installed = "com.evil.notes", signers = listOf(myNotesCert))
        assertFalse(verifier(signatures).isTrusted("com.evil.notes"))
        assertFalse(verifier(signatures).isTrusted(null))
        assertFalse(verifier(signatures).isTrusted("com.pasich.mynotes.debug"))
        assertTrue(signatures.queried.isEmpty())
    }

    @Test
    fun myNotesSignedWithAnotherCertificateIsRefused() {
        assertFalse(verifier(FakeSignatures(API_34, signers = listOf(otherCert))).isTrusted(MY_NOTES_PACKAGE))
        assertFalse(verifier(FakeSignatures(API_26, signers = listOf(otherCert))).isTrusted(MY_NOTES_PACKAGE))
    }

    @Test
    fun beforeApi28OneSignerWithThePinnedCertificateIsTrusted() {
        assertTrue(verifier(FakeSignatures(API_26, signers = listOf(myNotesCert))).isTrusted(MY_NOTES_PACKAGE))
        assertTrue(verifier(FakeSignatures(API_27, signers = listOf(myNotesCert))).isTrusted(MY_NOTES_PACKAGE))
    }

    @Test
    fun beforeApi28SeveralSignersOrNoneAreRefused() {
        val twoSigners = FakeSignatures(API_27, signers = listOf(myNotesCert, otherCert))
        assertFalse(verifier(twoSigners).isTrusted(MY_NOTES_PACKAGE))
        assertFalse(verifier(FakeSignatures(API_27, signers = emptyList())).isTrusted(MY_NOTES_PACKAGE))
        assertFalse(verifier(FakeSignatures(API_27, installed = null)).isTrusted(MY_NOTES_PACKAGE))
    }

    @Test
    fun theShippedPinIsTheGitHubReleaseCertificate() {
        assertEquals(
            setOf("03f2b8c7c96778b7efb80bb08af99b273a6c0c24e5864d0a211c3a8e3d02483f"),
            MyNotesCallerVerifier.TRUSTED_MY_NOTES_CERT_SHA256,
        )
        MyNotesCallerVerifier.TRUSTED_MY_NOTES_CERT_SHA256.forEach { assertTrue(it.matches(Regex("[0-9a-f]{64}"))) }
    }

    private companion object {
        const val API_26 = 26
        const val API_27 = 27
        const val API_28 = 28
        const val API_34 = 34

        fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        fun sha256Hex(bytes: ByteArray): String = digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
