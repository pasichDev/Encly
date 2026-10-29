package com.pasich.encly.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.pasich.encly.data.importer.DocumentTree
import kotlinx.io.RawSink
import kotlinx.io.RawSource

/**
 * A document or folder the user picked in the system's dialog, as the platform names it (a content URI on
 * Android, a path on the desktop). Only the platform looks inside; everything else passes it on.
 */
data class DocumentRef(val value: String)

/**
 * What the platform does with a [DocumentRef]: the one place that touches content providers or the file
 * system. Access that is refused (a revoked grant) arrives as a [kotlinx.io.IOException], like a failed
 * read; other failures a provider throws across its own boundary may be a `RuntimeException`. The callers
 * map them to their own errors without the (path-quoting) message.
 */
interface PlatformDocuments {
    /** [doc] for reading, or null when the provider has no content stream for it. */
    fun openRead(doc: DocumentRef): RawSource?

    /** [doc] for writing, replacing what it holds; null when the provider has no stream for it. */
    fun openWrite(doc: DocumentRef): RawSink?

    /** The document's display name, for showing which file was picked; null when unknown. */
    fun displayName(doc: DocumentRef): String?

    /** Best effort: deletes [doc] where the provider allows it. A failure is the caller's to ignore. */
    fun delete(doc: DocumentRef)

    /** The folder [root], to be walked listing at most [maxEntries] entries per folder. */
    fun openTree(root: DocumentRef, maxEntries: Int): DocumentTree
}

/** Thrown when no app on the device can show the requested system dialog. */
class PickerUnavailableException : RuntimeException("no app can show the dialog")

/** One system dialog, ready to be shown with an [I]nput. */
fun interface PickerLauncher<in I> {
    /** Shows the dialog; its answer goes to the callback the launcher was created with. */
    fun launch(input: I)
}

/**
 * The system's document dialogs. The `remember` functions are composable because a launcher belongs to
 * the screen that registered it (on Android it is bound to the Activity result registry). Showing a dialog
 * with [PickerLauncher.launch] throws [PickerUnavailableException] when no app can handle it.
 */
interface DocumentPickers {
    /** "Save as": [mimeType] is the type of the new document; the input is the suggested file name. */
    @Composable
    fun rememberCreateDocument(mimeType: String, onPick: (DocumentRef?) -> Unit): PickerLauncher<String>

    /** "Open": the input is the MIME types to offer. */
    @Composable
    fun rememberOpenDocument(onPick: (DocumentRef?) -> Unit): PickerLauncher<Array<String>>

    /** "Open" for several documents at once. */
    @Composable
    fun rememberOpenDocuments(onPick: (List<DocumentRef>) -> Unit): PickerLauncher<Array<String>>

    /** "Choose a folder". */
    @Composable
    fun rememberOpenFolder(onPick: (DocumentRef?) -> Unit): PickerLauncher<Unit>
}

/** The pickers of a host that has none: every launch reports that no app can show the dialog. */
object NoDocumentPickers : DocumentPickers {
    @Composable
    override fun rememberCreateDocument(mimeType: String, onPick: (DocumentRef?) -> Unit) = unavailable<String>()

    @Composable
    override fun rememberOpenDocument(onPick: (DocumentRef?) -> Unit) = unavailable<Array<String>>()

    @Composable
    override fun rememberOpenDocuments(onPick: (List<DocumentRef>) -> Unit) = unavailable<Array<String>>()

    @Composable
    override fun rememberOpenFolder(onPick: (DocumentRef?) -> Unit) = unavailable<Unit>()

    private fun <I> unavailable() = PickerLauncher<I> { throw PickerUnavailableException() }
}

/** The host's document dialogs, provided once by its Activity or window. */
val LocalDocumentPickers = staticCompositionLocalOf<DocumentPickers> { NoDocumentPickers }
