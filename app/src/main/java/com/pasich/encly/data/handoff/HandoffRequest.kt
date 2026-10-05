package com.pasich.encly.data.handoff

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * How the hand-off activity was started: what [HandoffRequest.check] looks at before the URI is
 * read. [callerTrusted] asks [MyNotesCallerVerifier] (only once the cheaper checks passed).
 * [launchedFromPackage] is `Activity.getLaunchedFromPackage()` (API 34+), null where the system
 * does not say (it only does when the launching app shares its identity).
 */
class HandoffLaunch(
    val flags: Int,
    val callerTrusted: () -> Boolean,
    val launchedFromPackage: String?,
    val action: String?,
    val uri: Uri?,
)

/** The checks of an incoming hand-off, in the order ImportFromMyNotesActivity runs them. */
object HandoffRequest {
    /**
     * Null when the hand-off may go on, else why it is refused. [providerPackage] answers which
     * package owns a content provider authority (PackageManager.resolveContentProvider).
     *
     * - `FLAG_ACTIVITY_FORWARD_RESULT`: another app passed on a request My Notes made of it (a
     *   file picker, say), so the calling package is My Notes but the intent is that app's;
     * - the caller must be My Notes with a pinned certificate ([MyNotesCallerVerifier]), and,
     *   where the system names the app that launched this activity, that app too;
     * - the action and a `content://` URI;
     * - the URI from My Notes' own FileProvider: its authority, owned by the My Notes package.
     */
    @Suppress("ReturnCount") // One gate per check, each refusing on its own.
    fun check(launch: HandoffLaunch, providerPackage: (authority: String) -> String?): HandoffError? {
        if (launch.flags and Intent.FLAG_ACTIVITY_FORWARD_RESULT != 0) return HandoffError.UNTRUSTED_CALLER
        if (!launch.callerTrusted()) return HandoffError.UNTRUSTED_CALLER
        val launchedFrom = launch.launchedFromPackage
        if (launchedFrom != null && launchedFrom != MyNotesCallerVerifier.MY_NOTES_PACKAGE) {
            return HandoffError.UNTRUSTED_CALLER
        }
        val uri = launch.uri
        if (launch.action != ACTION_IMPORT_FROM_MY_NOTES || uri?.scheme != ContentResolver.SCHEME_CONTENT) {
            return HandoffError.INVALID_PAYLOAD
        }
        val authority = uri.authority
        if (authority != MyNotesCallerVerifier.MY_NOTES_AUTHORITY ||
            providerPackage(authority) != MyNotesCallerVerifier.MY_NOTES_PACKAGE
        ) {
            return HandoffError.UNTRUSTED_CALLER
        }
        return null
    }

    const val ACTION_IMPORT_FROM_MY_NOTES = "com.pasich.encly.action.IMPORT_FROM_MY_NOTES"
}
