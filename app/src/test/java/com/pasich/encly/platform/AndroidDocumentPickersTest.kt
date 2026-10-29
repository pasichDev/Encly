package com.pasich.encly.platform

import android.app.Application
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The document dialogs hand back [DocumentRef]s, never URIs, and a device with no documents app is reported as
 * [PickerUnavailableException], which the screens turn into a message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidDocumentPickersTest {
    @get:Rule
    val rule = createComposeRule()

    /** The answer the next dialog gives; null makes it throw ActivityNotFoundException instead. */
    private var answer: Any? = null
    private var unavailable = false

    private val registry = object : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            if (unavailable) throw ActivityNotFoundException()
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, answer as O)
        }
    }

    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    private fun uri(text: String): Uri = Uri.parse(text)

    @Test
    fun aCreatedDocumentIsReturnedAsARef() {
        var picked: DocumentRef? = null
        var launcher: PickerLauncher<String>? = null
        setContent { launcher = AndroidDocumentPickers.rememberCreateDocument("text/plain") { picked = it } }

        answer = uri("content://docs/new.txt")
        rule.runOnIdle { checkNotNull(launcher).launch("new.txt") }
        rule.waitForIdle()

        assertEquals(DocumentRef("content://docs/new.txt"), picked)
    }

    @Test
    fun anOpenedDocumentIsReturnedAndACancelIsNull() {
        var picked: DocumentRef? = DocumentRef("before")
        var launcher: PickerLauncher<Array<String>>? = null
        setContent { launcher = AndroidDocumentPickers.rememberOpenDocument { picked = it } }

        answer = uri("content://docs/a.md")
        rule.runOnIdle { checkNotNull(launcher).launch(arrayOf("*/*")) }
        rule.waitForIdle()
        assertEquals(DocumentRef("content://docs/a.md"), picked)

        answer = null
        rule.runOnIdle { checkNotNull(launcher).launch(arrayOf("*/*")) }
        rule.waitForIdle()
        assertEquals(null, picked)
    }

    @Test
    fun severalOpenedDocumentsAreReturnedInOrder() {
        var picked: List<DocumentRef> = emptyList()
        var launcher: PickerLauncher<Array<String>>? = null
        setContent { launcher = AndroidDocumentPickers.rememberOpenDocuments { picked = it } }

        answer = listOf(uri("content://docs/a.md"), uri("content://docs/b.md"))
        rule.runOnIdle { checkNotNull(launcher).launch(arrayOf("*/*")) }
        rule.waitForIdle()

        assertEquals(listOf(DocumentRef("content://docs/a.md"), DocumentRef("content://docs/b.md")), picked)
    }

    @Test
    fun aChosenFolderIsReturnedAsARef() {
        var picked: DocumentRef? = null
        var launcher: PickerLauncher<Unit>? = null
        setContent { launcher = AndroidDocumentPickers.rememberOpenFolder { picked = it } }

        answer = uri("content://docs/tree/root")
        rule.runOnIdle { checkNotNull(launcher).launch(Unit) }
        rule.waitForIdle()

        assertEquals(DocumentRef("content://docs/tree/root"), picked)
    }

    @Test
    fun noDocumentsAppIsPickerUnavailableForEveryDialog() {
        var create: PickerLauncher<String>? = null
        var open: PickerLauncher<Array<String>>? = null
        var several: PickerLauncher<Array<String>>? = null
        var folder: PickerLauncher<Unit>? = null
        setContent {
            create = AndroidDocumentPickers.rememberCreateDocument("text/plain") {}
            open = AndroidDocumentPickers.rememberOpenDocument {}
            several = AndroidDocumentPickers.rememberOpenDocuments {}
            folder = AndroidDocumentPickers.rememberOpenFolder {}
        }
        unavailable = true

        rule.runOnIdle {
            assertThrows(PickerUnavailableException::class.java) { checkNotNull(create).launch("a.txt") }
            assertThrows(PickerUnavailableException::class.java) { checkNotNull(open).launch(arrayOf("*/*")) }
            assertThrows(PickerUnavailableException::class.java) { checkNotNull(several).launch(arrayOf("*/*")) }
            assertThrows(PickerUnavailableException::class.java) { checkNotNull(folder).launch(Unit) }
        }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { content() }
        }
        rule.waitForIdle()
    }
}
