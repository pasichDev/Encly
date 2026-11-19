package com.pasich.encly.editor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.PopupProperties

/**
 * Whether the host lets its windows be captured (screenshots, recordings, casting) right now. A dialog, a
 * sheet and a menu each have a window of their own, which takes the main window's protection as it opens and
 * then keeps it, whatever the host does to the main window afterwards. Through this they follow the host for
 * as long as they are open. Android only: the other targets have no such window flag.
 */
public interface WindowCapture {
    /** Whether a window may be captured at this moment. */
    public val capturable: Boolean

    /** Tells [observer] now, and on every change, whether a window may be captured; the returned function stops it. */
    public fun observe(observer: (capturable: Boolean) -> Unit): () -> Unit
}

/**
 * The host's [WindowCapture], or null for a host whose windows never change (always protected, or never):
 * a dialog, a sheet and a menu then keep what they took from the main window as they opened. A host that
 * can lift and restore the protection of its main window provides it above every dialog and menu, its own
 * and the editor's.
 */
public val LocalWindowCapture: ProvidableCompositionLocal<WindowCapture?> = staticCompositionLocalOf { null }

/**
 * The properties of a menu or another popup, [focusable] or not, whose window is protected from capture
 * whenever [LocalWindowCapture] says the host's windows are, also when that changes while it is open
 * (Android only; plain properties elsewhere, and where the host provides no [WindowCapture]).
 */
@Composable
public expect fun protectedPopupProperties(focusable: Boolean = true): PopupProperties
