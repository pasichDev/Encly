package com.pasich.encly.editor.ui.components

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pasich.encly.editor.EditorProvider
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In a wide window the editor's sheet is a centred dialog; a window gets the editor's privacy flags, and the
 * windows of dialogs, sheets and menus follow the host's capture protection while they are open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EditorSheetDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(title: String?) {
        rule.setContent {
            MaterialTheme {
                EditorProvider(sheetsAsDialogs = true) {
                    EditorSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(), title = title) {
                        Text("body")
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theDialogShowsItsTitleAndContent() {
        show("Heading")

        rule.onNodeWithText("Heading").assertExists()
        rule.onNodeWithText("body").assertExists()
    }

    @Test
    fun theDialogWithoutATitleShowsItsContent() {
        show(null)

        rule.onNodeWithText("body").assertExists()
    }

    /** A host whose windows may be captured or not, as a test says. */
    private class FakeCapture(initial: Boolean) : WindowCapture {
        private val observers = mutableListOf<(Boolean) -> Unit>()

        override var capturable: Boolean = initial
            set(value) {
                field = value
                observers.toList().forEach { it(value) }
            }

        val observed: Int get() = observers.size

        override fun observe(observer: (capturable: Boolean) -> Unit): () -> Unit {
            observers += observer
            observer(capturable)
            return { observers -= observer }
        }
    }

    private fun Window.isSecure() = attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun anOpenDialogIsProtectedFromCaptureAsSoonAsTheHostsWindowsAre() {
        val capture = FakeCapture(initial = true)
        var window: Window? = null
        var shown by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalWindowCapture provides capture) {
                if (shown) {
                    Dialog(onDismissRequest = {}) {
                        ProtectDialogWindow()
                        window = (LocalView.current.parent as DialogWindowProvider).window
                    }
                }
            }
        }
        rule.waitForIdle()
        val dialog = checkNotNull(window)
        assertFalse(dialog.isSecure(), "opened while the host allows capture")

        // The allowance ends (a lock, a secret on screen) while the dialog is open.
        rule.runOnIdle { capture.capturable = false }
        assertTrue(dialog.isSecure())

        rule.runOnIdle { capture.capturable = true }
        assertFalse(dialog.isSecure())

        // A closed dialog no longer listens.
        shown = false
        rule.waitForIdle()
        assertEquals(0, capture.observed)
    }

    @Test
    fun aDialogOpenedWhileTheHostsWindowsAreProtectedIsProtected() {
        var window: Window? = null
        rule.setContent {
            // As the host does: its main window is protected whenever its windows may not be captured.
            val host = LocalContext.current as Activity
            remember(host) { host.window.also { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
            CompositionLocalProvider(LocalWindowCapture provides FakeCapture(initial = false)) {
                Dialog(onDismissRequest = {}) {
                    ProtectDialogWindow()
                    window = (LocalView.current.parent as DialogWindowProvider).window
                }
            }
        }
        rule.waitForIdle()

        assertTrue(checkNotNull(window).isSecure())
    }

    @Test
    fun aBottomSheetWindowFollowsTheHostToo() {
        val capture = FakeCapture(initial = true)
        var window: Window? = null
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalWindowCapture provides capture) {
                    EditorSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(), title = null) {
                        window = (LocalView.current.parent as DialogWindowProvider).window
                        Text("body")
                    }
                }
            }
        }
        rule.waitForIdle()
        assertFalse(checkNotNull(window).isSecure())

        rule.runOnIdle { capture.capturable = false }

        assertTrue(checkNotNull(window).isSecure())
    }

    @Test
    fun aPopupIsProtectedWhileTheHostsWindowsAreAndInheritsOtherwise() {
        val capture = FakeCapture(initial = true)
        var properties: PopupProperties? = null
        var plain: PopupProperties? = null
        rule.setContent {
            plain = protectedPopupProperties(focusable = false)
            CompositionLocalProvider(LocalWindowCapture provides capture) {
                properties = protectedPopupProperties()
            }
        }
        rule.waitForIdle()
        assertEquals(SecureFlagPolicy.Inherit, checkNotNull(properties).securePolicy)
        assertTrue(checkNotNull(properties).focusable)

        rule.runOnIdle { capture.capturable = false }
        rule.waitForIdle()
        assertEquals(SecureFlagPolicy.SecureOn, properties?.securePolicy, "an open menu is protected too")

        rule.runOnIdle { capture.capturable = true }
        rule.waitForIdle()
        assertEquals(SecureFlagPolicy.Inherit, properties?.securePolicy)

        // A host with no say in it: a popup as it always was.
        assertEquals(SecureFlagPolicy.Inherit, checkNotNull(plain).securePolicy)
        assertFalse(checkNotNull(plain).focusable)
    }

    @Test
    fun aWindowIsMarkedToHideFromAutofill() {
        val decor = View(RuntimeEnvironment.getApplication())
        val window = Mockito.mock(Window::class.java)
        Mockito.`when`(window.decorView).thenReturn(decor)

        window.applyWindowPrivacy()

        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, decor.importantForAutofill)
    }
}
