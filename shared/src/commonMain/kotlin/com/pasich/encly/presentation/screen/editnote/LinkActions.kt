package com.pasich.encly.presentation.screen.editnote

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.link_blocked
import com.pasich.encly.core.resources.link_copied
import com.pasich.encly.core.resources.link_open_failed
import com.pasich.encly.editor.ui.blocks.isOpenableLink
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.platform.ToastDuration
import com.pasich.encly.platform.Toaster

/**
 * Opens a note's link in an app the user picks. The link leaves Encly: nothing is fetched by
 * Encly itself, the chosen app does it. Only web and mail links are opened (a note restored from
 * a crafted backup may hold any address), and a failure to open one never crashes the app.
 */
fun openNoteLink(links: ExternalLinks, toaster: Toaster, url: String) {
    if (!isOpenableLink(url)) {
        toaster.show(Res.string.link_blocked, ToastDuration.LONG)
        return
    }
    if (!links.openUrl(url, chooser = true)) toaster.show(Res.string.link_open_failed, ToastDuration.LONG)
}

/**
 * Copies [text] (a note's link) under [label] with the app's clipboard policy ([SensitiveClipboard]: marked
 * sensitive, cleared after a minute). Where the system shows no confirmation, a toast does.
 */
fun copyNoteLink(clipboard: SensitiveClipboard, toaster: Toaster, label: String, text: String) {
    if (!clipboard.copy(label, text)) return
    if (!clipboard.systemConfirmsCopy) toaster.show(Res.string.link_copied, ToastDuration.SHORT)
}
