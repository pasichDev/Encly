package com.pasich.encly.platform

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The Storage Access Framework dialogs as [DocumentPickers]: the system's own "save as", "open" and "choose
 * a folder" screens, so Encly needs no storage permission and learns of no more than the one document the
 * user picked. A device with no documents app reports it as [PickerUnavailableException].
 */
object AndroidDocumentPickers : DocumentPickers {

    @Composable
    override fun rememberCreateDocument(mimeType: String, onPick: (DocumentRef?) -> Unit): PickerLauncher<String> {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
            onPick(uri?.let { DocumentRef(it.toString()) })
        }
        return launcher.asPicker()
    }

    @Composable
    override fun rememberOpenDocument(onPick: (DocumentRef?) -> Unit): PickerLauncher<Array<String>> {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onPick(uri?.let { DocumentRef(it.toString()) })
        }
        return launcher.asPicker()
    }

    @Composable
    override fun rememberOpenDocuments(onPick: (List<DocumentRef>) -> Unit): PickerLauncher<Array<String>> {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            onPick(uris.map { DocumentRef(it.toString()) })
        }
        return launcher.asPicker()
    }

    @Composable
    override fun rememberOpenFolder(onPick: (DocumentRef?) -> Unit): PickerLauncher<Unit> {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            onPick(uri?.let { DocumentRef(it.toString()) })
        }
        return remember(launcher) { PickerLauncher<Unit> { launcher.launchOrUnavailable(null) } }
    }

    @Composable
    private fun <I> ActivityResultLauncher<I>.asPicker(): PickerLauncher<I> =
        remember(this) { PickerLauncher<I> { input -> launchOrUnavailable(input) } }

    private fun <I> ActivityResultLauncher<I>.launchOrUnavailable(input: I) {
        try {
            launch(input)
        } catch (_: ActivityNotFoundException) {
            throw PickerUnavailableException()
        }
    }
}
