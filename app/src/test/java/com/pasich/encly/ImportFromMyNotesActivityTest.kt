package com.pasich.encly

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.data.handoff.MyNotesCallerVerifier
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

    private fun refusalOf(callingPackage: String?, intent: Intent = handoffIntent): Pair<Int, String?> {
        val controller = Robolectric.buildActivity(ImportFromMyNotesActivity::class.java, intent)
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

    @Test
    fun aRequestAnotherAppPassedOnIsRefusedEvenInMyNotesName() {
        // A picker that My Notes started for a result forwards that request into Encly: the system
        // then reports My Notes as the caller of the picker's own intent and URI.
        val forwarded = Intent(handoffIntent).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)

        assertEquals(
            Activity.RESULT_CANCELED to "untrusted_caller",
            refusalOf(MyNotesCallerVerifier.MY_NOTES_PACKAGE, forwarded),
        )
    }

    // Each check before the URI is read: HandoffRequestTest. My Notes' name with the wrong
    // certificate: MyNotesCallerVerifierTest (Robolectric does not implement
    // PackageManager.hasSigningCertificate).
}
