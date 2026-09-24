package com.pasich.encly.presentation.screen.editnote

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.pasich.encly.R
import com.pasich.encly.core.AppLogger
import com.pasich.encly.presentation.components.SensitiveClip
import com.pasich.encly.presentation.editor.blocks.isOpenableLink

/**
 * Opens a note's link in an app the user picks. The link leaves Encly: nothing is fetched by
 * Encly itself, the chosen app does it. Only web and mail links are opened (a note restored from
 * a crafted backup may hold any address), and a failure to open one never crashes the app.
 */
@Suppress("TooGenericExceptionCaught") // Another app's failure to take the link must never crash Encly.
fun openNoteLink(context: Context, url: String) {
    if (!isOpenableLink(url)) {
        Toast.makeText(context, R.string.link_blocked, Toast.LENGTH_LONG).show()
        return
    }
    val view = Intent(Intent.ACTION_VIEW, url.toUri())
    try {
        context.startActivity(Intent.createChooser(view, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    } catch (e: RuntimeException) {
        // FileUriExposedException, SecurityException: the address cannot leave the app safely.
        AppLogger.w(TAG, "Link not opened: ${e.javaClass.simpleName}")
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    }
}

private const val TAG = "LinkActions"

/**
 * Copies a note's link under the app's clipboard policy ([SensitiveClip]: marked sensitive,
 * cleared after a minute). Older versions get a toast, since the system shows no confirmation.
 */
fun copyNoteLink(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    SensitiveClip.copy(clipboard, context.getString(R.string.block_link), url)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }
}
