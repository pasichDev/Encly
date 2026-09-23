package com.pasich.encly.presentation.screen.editnote

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.net.toUri
import com.pasich.encly.R

/**
 * Opens a note's link in an app the user picks. The link leaves Encly: nothing is fetched by
 * Encly itself, the chosen app does it.
 */
fun openNoteLink(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, url.toUri())
    try {
        context.startActivity(Intent.createChooser(view, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    }
}

/**
 * Copies a note's link, marked sensitive so the system does not show it in the clipboard
 * preview (API 33+). Older versions get a toast, since the system shows no confirmation.
 */
fun copyNoteLink(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(context.getString(R.string.block_link), url)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }
}
