package com.pasich.encly.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The permissions the app itself asks for are pinned (issue #14): Encly never touches the network, so a new
 * `<uses-permission>` in the source manifests is a decision, not a side effect. A library may add its own on
 * merge; the merged manifest of the F-Droid release is pinned, in full, by the `release` job of
 * `.github/workflows/android.yml` (it needs a build, which a unit test cannot run).
 */
class ManifestPermissionsTest {
    private val repoRoot = listOf(File(".."), File(".")).first { File(it, "version.properties").isFile }

    private val manifests = File(repoRoot, "app/src").listFiles().orEmpty()
        .filter { it.isDirectory && it.name != "androidTest" }
        .map { File(it, "AndroidManifest.xml") }
        .filter { it.isFile }

    private fun permissionsOf(manifest: File): Set<String> = USES_PERMISSION.findAll(manifest.readText())
        .map { it.groupValues[1] }
        .toSet()

    @Test
    fun theMainManifestAsksForExactlyTheKnownPermissions() {
        val main = File(repoRoot, "app/src/main/AndroidManifest.xml")

        assertEquals(
            setOf("android.permission.USE_BIOMETRIC", "android.permission.HIDE_OVERLAY_WINDOWS"),
            permissionsOf(main),
        )
    }

    @Test
    fun noSourceManifestAsksForTheNetworkOrStorage() {
        assertFalse("no manifests found", manifests.isEmpty())
        manifests.forEach { manifest ->
            val permissions = permissionsOf(manifest)
            BANNED.forEach { banned ->
                assertFalse("${manifest.path} asks for $banned", banned in permissions)
            }
        }
    }

    private companion object {
        val USES_PERMISSION = Regex("""<uses-permission(?:-sdk-\d+)?\s[^>]*android:name="([^"]+)"""")

        val BANNED = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
        )
    }
}
