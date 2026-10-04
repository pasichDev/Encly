package com.pasich.encly

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The exported hand-off activity refuses any caller that is not a verified My Notes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportFromMyNotesActivityTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val handoffIntent = Intent(ImportFromMyNotesActivity.ACTION_IMPORT_FROM_MY_NOTES)
        .setDataAndType(Uri.parse("content://com.pasich.mynotes.fileprovider/handoff/handoff.zip"), "application/zip")
        .setClass(ApplicationProvider.getApplicationContext(), ImportFromMyNotesActivity::class.java)

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
    }

    private fun refusalOf(callingPackage: String?): Pair<Int, String?> {
        val controller = Robolectric.buildActivity(ImportFromMyNotesActivity::class.java, handoffIntent)
        val shadow = shadowOf(controller.get())
        shadow.setCallingPackage(callingPackage)
        controller.create()
        try {
            assertTrue(controller.get().isFinishing)
            return shadow.resultCode to shadow.resultIntent?.getStringExtra(ImportFromMyNotesActivity.EXTRA_REASON)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun aCallerThatIsNotMyNotesIsRefused() {
        assertEquals(Activity.RESULT_CANCELED to "untrusted_caller", refusalOf("com.example.other"))
    }

    @Test
    fun startedWithoutForResultIsRefused() {
        assertEquals(Activity.RESULT_CANCELED to "untrusted_caller", refusalOf(null))
    }

    // My Notes' name with the wrong certificate: MyNotesCallerVerifierTest (Robolectric does not
    // implement PackageManager.hasSigningCertificate).
}
