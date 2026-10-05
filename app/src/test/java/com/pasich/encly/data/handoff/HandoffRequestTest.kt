package com.pasich.encly.data.handoff

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The checks before a hand-off's URI is read (pasichDev/Encly#46): a request another app passed
 * on, or a URI that is not My Notes' own, is refused even when the calling package is My Notes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandoffRequestTest {
    private val myNotes = MyNotesCallerVerifier.MY_NOTES_PACKAGE
    private val myNotesUri = Uri.parse(
        "content://${MyNotesCallerVerifier.MY_NOTES_AUTHORITY}/encly_handoff/handoff.zip",
    )

    /** Which package owns each provider authority on the device. */
    private val providers = mutableMapOf(MyNotesCallerVerifier.MY_NOTES_AUTHORITY to myNotes)

    private fun check(
        flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        callerTrusted: Boolean = true,
        launchedFrom: String? = null,
        action: String? = HandoffRequest.ACTION_IMPORT_FROM_MY_NOTES,
        uri: Uri? = myNotesUri,
    ): HandoffError? = HandoffRequest.check(HandoffLaunch(flags, { callerTrusted }, launchedFrom, action, uri)) {
        providers[it]
    }

    @Test
    fun myNotesWithItsOwnProviderGoesOn() {
        assertNull(check())
        assertNull(check(launchedFrom = myNotes))
    }

    @Test
    fun aRequestPassedOnWithForwardResultIsRefused() {
        // A picker My Notes started forwards the result request into Encly with its own intent.
        assertEquals(
            HandoffError.UNTRUSTED_CALLER,
            check(flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    @Test
    fun aCallerThatIsNotAVerifiedMyNotesIsRefused() {
        assertEquals(HandoffError.UNTRUSTED_CALLER, check(callerTrusted = false))
    }

    @Test
    fun anotherLaunchingAppIsRefusedWhereTheSystemNamesIt() {
        assertEquals(HandoffError.UNTRUSTED_CALLER, check(launchedFrom = "com.example.picker"))
    }

    @Test
    fun aWrongActionOrAUriThatIsNotContentIsInvalid() {
        assertEquals(HandoffError.INVALID_PAYLOAD, check(action = Intent.ACTION_VIEW))
        assertEquals(HandoffError.INVALID_PAYLOAD, check(uri = null))
        assertEquals(HandoffError.INVALID_PAYLOAD, check(uri = Uri.parse("file:///sdcard/handoff.zip")))
    }

    @Test
    fun aUriFromAnotherProviderIsRefused() {
        providers["com.example.files"] = "com.example.picker"

        assertEquals(
            HandoffError.UNTRUSTED_CALLER,
            check(uri = Uri.parse("content://com.example.files/handoff.zip")),
        )
    }

    @Test
    fun myNotesAuthorityServedByAnotherPackageIsRefused() {
        // An app that registered My Notes' authority first (My Notes not installed or replaced).
        providers[MyNotesCallerVerifier.MY_NOTES_AUTHORITY] = "com.example.squatter"

        assertEquals(HandoffError.UNTRUSTED_CALLER, check())
        providers.clear()
        assertEquals(HandoffError.UNTRUSTED_CALLER, check())
    }
}
