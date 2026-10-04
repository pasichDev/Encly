package com.pasich.encly.data.handoff

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** The package-manager queries [MyNotesCallerVerifier] needs; replaced by a fake in tests. */
interface PackageSignatures {
    val sdkInt: Int

    /** API 28+: whether [packageName] is (or, through key rotation, was) signed by [sha256]. */
    fun hasSigningCertificateSha256(packageName: String, sha256: ByteArray): Boolean

    /** API 26-27: the encoded signing certificates of [packageName]; null if not installed. */
    fun legacySignatures(packageName: String): List<ByteArray>?
}

class AndroidPackageSignatures(private val packageManager: PackageManager) : PackageSignatures {
    override val sdkInt: Int = Build.VERSION.SDK_INT

    override fun hasSigningCertificateSha256(packageName: String, sha256: ByteArray): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            packageManager.hasSigningCertificate(packageName, sha256, PackageManager.CERT_INPUT_SHA256)

    // GET_SIGNATURES is the only signer API before 28. The lint warning is about trusting one
    // of several signers; the verifier accepts exactly one signer, so there is no other to miss.
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    override fun legacySignatures(packageName: String): List<ByteArray>? = try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            .signatures
            ?.map { it.toByteArray() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

/**
 * Decides whether the activity that started the hand-off is My Notes. Checked before the URI
 * is read: anything else is refused without touching its data.
 *
 * The calling package comes from `Activity.getCallingPackage()`, which the system fills in for
 * `startActivityForResult` and a caller cannot forge; the signing certificate then proves that
 * package is the real My Notes and not an app installed under its name.
 */
class MyNotesCallerVerifier(
    private val signatures: PackageSignatures,
    private val trustedCertSha256: Set<String> = TRUSTED_MY_NOTES_CERT_SHA256,
) {
    fun isTrusted(callingPackage: String?): Boolean =
        callingPackage == MY_NOTES_PACKAGE && hasPinnedCertificate(trustedCertSha256.map(::hexToBytes))

    private fun hasPinnedCertificate(pinned: List<ByteArray>): Boolean =
        if (signatures.sdkInt >= Build.VERSION_CODES.P) {
            pinned.any { signatures.hasSigningCertificateSha256(MY_NOTES_PACKAGE, it) }
        } else {
            // Exactly one signer: with several, one trusted certificate would not prove the rest.
            val signer = signatures.legacySignatures(MY_NOTES_PACKAGE)?.singleOrNull()
            val digest = signer?.let { MessageDigest.getInstance("SHA-256").digest(it) }
            digest != null && pinned.any { MessageDigest.isEqual(it, digest) }
        }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(HEX).toByte()
    }

    companion object {
        const val MY_NOTES_PACKAGE = "com.pasich.mynotes"
        private const val HEX = 16

        /**
         * SHA-256 (lower-case hex) of every certificate My Notes is signed with. A caller signed
         * with none of them is refused.
         *
         * - `03f2b8c7…483f`: the release key of the GitHub / F-Droid builds (checked on the
         *   GitHub release APK 2.6.55, CN=Andrii Pasichnik12).
         *
         * TODO(pasichDev/Encly#46): before release, add the Google Play App Signing certificate
         *  SHA-256 of My Notes (Play Console -> My Notes -> Test and release -> App integrity ->
         *  App signing). Without it the Play build of My Notes is refused as untrusted_caller.
         */
        val TRUSTED_MY_NOTES_CERT_SHA256: Set<String> = setOf(
            "03f2b8c7c96778b7efb80bb08af99b273a6c0c24e5864d0a211c3a8e3d02483f",
        )
    }
}
