package com.pasich.encly.presentation.components

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Anything copied out of the app is marked sensitive and leaves the clipboard after a minute,
 * unless something else was copied in the meantime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SensitiveClipTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clipboard: ClipboardManager get() = context.getSystemService(ClipboardManager::class.java)

    private fun passMillis(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    private fun isSensitive(clip: ClipData?) =
        clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true

    @Test
    fun aCopiedClipIsMarkedSensitiveAndCarriesItsText() {
        SensitiveClip.copy(clipboard, "label", "secret")

        val clip = clipboard.primaryClip
        assertEquals("secret", clip?.getItemAt(0)?.text.toString())
        assertTrue(isSensitive(clip))
    }

    @Test
    fun theClipboardIsClearedAfterAMinute() {
        SensitiveClip.copy(clipboard, "label", "secret")

        passMillis(SensitiveClip.CLEAR_AFTER_MS - 1)
        assertEquals("not yet", "secret", clipboard.primaryClip?.getItemAt(0)?.text.toString())

        passMillis(2)
        assertTrue(clipboard.primaryClip == null || clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty())
    }

    @Test
    fun somethingCopiedLaterIsLeftAlone() {
        SensitiveClip.copy(clipboard, "label", "secret")
        passMillis(10)
        clipboard.setPrimaryClip(ClipData.newPlainText("other", "from elsewhere"))

        passMillis(SensitiveClip.CLEAR_AFTER_MS)

        assertEquals("from elsewhere", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun clearEmptiesTheClipboardAtOnce() {
        clipboard.setPrimaryClip(ClipData.newPlainText("l", "x"))

        SensitiveClip.clear(clipboard)

        assertTrue(clipboard.primaryClip == null || clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty())
    }

    @Test
    fun markingAClipKeepsItsOtherExtras() {
        val clip = ClipData.newPlainText("l", "x")
        clip.description.extras = android.os.PersistableBundle().apply { putString("keep", "me") }

        SensitiveClip.markSensitive(clip)

        assertEquals("me", clip.description.extras?.getString("keep"))
        assertTrue(isSensitive(clip))
    }

    @Test
    fun theBoundaryMarksAndSchedulesEveryComposeCopy() {
        lateinit var copy: suspend (ClipEntry?) -> Unit
        var scope: kotlinx.coroutines.CoroutineScope? = null
        rule.setContent {
            SensitiveClipboardBoundary {
                val clip = LocalClipboard.current
                scope = rememberCoroutineScope()
                copy = { entry -> clip.setClipEntry(entry) }
                Probe()
            }
        }
        rule.waitForIdle()

        scope!!.launch { copy(ClipEntry(ClipData.newPlainText("l", "typed secret"))) }
        rule.waitForIdle()
        assertTrue(isSensitive(clipboard.primaryClip))

        passMillis(SensitiveClip.CLEAR_AFTER_MS)
        assertTrue(clipboard.primaryClip == null || clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty())
    }

    @Composable
    private fun Probe() = Unit
}
