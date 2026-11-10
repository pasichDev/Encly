@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pasich.encly.platform.DocumentPickers
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.PickerLauncher
import com.pasich.encly.platform.PickerUnavailableException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/**
 * The document dialogs of iOS: `UIDocumentPickerViewController`, the Files app's browser (iCloud Drive, "On My
 * iPhone" and any provider the user has).
 * - "Open": the system copies the chosen files into the app (`asCopy`), which also makes it download an iCloud file
 *   that is not on the device yet; the copies are moved into [copies], a folder of the protected cache that is
 *   emptied when the vault locks, when the app starts and before the next pick.
 * - "Save as": there is no create-with-a-name dialog on iOS. An empty file of the suggested name is exported with
 *   `asCopy`, the user picks the folder, and the file at the chosen place is what the app then writes into.
 * - "Choose a folder": the folder is read in place under a security scope that [scopes] ends with the others.
 * The MIME types an Android caller offers are not used to filter: iOS has no type for an Encly backup, so the
 * dialog offers every file.
 */
internal class IosDocumentPickers(
    private val scopes: SecurityScopes,
    private val copies: PickedCopies,
    private val exportDirectory: Path,
) : DocumentPickers {
    @Composable
    override fun rememberCreateDocument(mimeType: String, onPick: (DocumentRef?) -> Unit): PickerLauncher<String> {
        val callback by rememberUpdatedState(onPick)
        return remember { PickerLauncher<String> { name -> exportPlaceholder(name) { callback(it) } } }
    }

    @Composable
    override fun rememberOpenDocument(onPick: (DocumentRef?) -> Unit): PickerLauncher<Array<String>> {
        val callback by rememberUpdatedState(onPick)
        return remember { PickerLauncher<Array<String>> { openFiles(multiple = false) { callback(it.firstOrNull()) } } }
    }

    @Composable
    override fun rememberOpenDocuments(onPick: (List<DocumentRef>) -> Unit): PickerLauncher<Array<String>> {
        val callback by rememberUpdatedState(onPick)
        return remember { PickerLauncher<Array<String>> { openFiles(multiple = true) { callback(it) } } }
    }

    @Composable
    override fun rememberOpenFolder(onPick: (DocumentRef?) -> Unit): PickerLauncher<Unit> {
        val callback by rememberUpdatedState(onPick)
        return remember { PickerLauncher<Unit> { openFolder { callback(it) } } }
    }

    private fun openFiles(multiple: Boolean, done: (List<DocumentRef>) -> Unit) {
        releaseEarlierPicks()
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = multiple
        present(picker) { urls ->
            val folder = copies.newFolder()
            done(urls.mapNotNull { copies.adopt(it, folder) }.map(::DocumentRef))
        }
    }

    private fun openFolder(done: (DocumentRef?) -> Unit) {
        releaseEarlierPicks()
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        picker.allowsMultipleSelection = false
        present(picker) { urls ->
            val folder = urls.firstOrNull()
            if (folder != null) scopes.retain(folder)
            done(folder?.path?.let(::DocumentRef))
        }
    }

    private fun exportPlaceholder(suggestedName: String, done: (DocumentRef?) -> Unit) {
        releaseEarlierPicks()
        val folder = Path(exportDirectory, NSUUID().UUIDString)
        SystemFileSystem.createDirectories(folder)
        val placeholder = Path(folder, safeFileName(suggestedName))
        NSFileManager.defaultManager.createFileAtPath(placeholder.toString(), null, null)
        val url = NSURL.fileURLWithPath(placeholder.toString())
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        present(picker) { urls ->
            val destination = urls.firstOrNull()
            if (destination != null) scopes.retain(destination)
            runCatching { SystemFileSystem.delete(placeholder, mustExist = false) }
            runCatching { SystemFileSystem.delete(folder, mustExist = false) }
            done(destination?.path?.let(::DocumentRef))
        }
    }

    private fun releaseEarlierPicks() {
        scopes.releaseAll()
        copies.purge()
    }

    private fun present(picker: UIDocumentPickerViewController, onPicked: (List<NSURL>) -> Unit) {
        val presenter = topViewController() ?: throw PickerUnavailableException()
        val delegate = PickerDelegate(onPicked)
        picker.delegate = delegate
        // The picker holds its delegate weakly: keep it alive until the dialog is over.
        delegates += delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private val delegates = mutableListOf<PickerDelegate>()

    private inner class PickerDelegate(private val onPicked: (List<NSURL>) -> Unit) :
        NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            delegates -= this
            onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            delegates -= this
            onPicked(emptyList())
        }
    }

    private fun safeFileName(name: String): String =
        name.map { if (it == '/' || it == ':' || it.isISOControl()) '_' else it }.joinToString("").ifBlank { "Encly" }
}

/** The view controller a sheet can be presented from: the top of the foreground scene's normal window. */
internal fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene =
        scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    val window = scene?.windows?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.windowLevel == UIWindowLevelNormal && !it.hidden }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
