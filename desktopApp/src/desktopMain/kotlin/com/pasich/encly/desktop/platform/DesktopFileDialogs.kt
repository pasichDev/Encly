package com.pasich.encly.desktop.platform

import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.desktop.storage.DesktopOs
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * The OS's file dialogs. Every call answers on the AWT event thread, once: with the chosen path(s), or with
 * nothing when the user cancelled.
 */
interface FileDialogs {
    /** "Save as" with [suggestedName]. */
    fun save(suggestedName: String, onResult: (Path?) -> Unit)

    /** "Open" one or [multiple] files; [extensions] (lower case, no dot) narrows the offer, empty offers all. */
    fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit)

    /** "Choose a folder". */
    fun folder(onResult: (Path?) -> Unit)
}

/** Which file extensions to offer for the MIME types the screens ask for. Empty means no filter. */
object FileFilters {
    private val BY_MIME = mapOf(
        "text/markdown" to setOf("md", "markdown"),
        "text/x-markdown" to setOf("md", "markdown"),
        "text/plain" to setOf("txt", "md"),
        "application/zip" to setOf("zip"),
        "application/x-zip-compressed" to setOf("zip"),
        "application/json" to setOf("json"),
        "text/html" to setOf("html", "htm"),
    )

    /** Types that mean "any file": a generic binary (the backup) or a wildcard. */
    private val ANYTHING = setOf("application/octet-stream", "*/*")

    fun extensionsFor(mimeTypes: Array<String>): Set<String> {
        if (mimeTypes.isEmpty() || mimeTypes.any { it in ANYTHING }) return emptySet()
        val known = mimeTypes.map(BY_MIME::get)
        // One unknown type could be anything: offer everything rather than hide a file.
        return if (known.any { it == null }) emptySet() else known.filterNotNull().flatten().toSet()
    }
}

/**
 * The dialogs AWT provides: the real native ones on macOS and Windows, a toolkit dialog on Linux (used there
 * only when the freedesktop portal does not answer). A folder is chosen with the macOS file dialog in folder
 * mode, and with a Swing chooser elsewhere, since AWT's `FileDialog` cannot choose a folder on Windows or Linux.
 */
class AwtFileDialogs(private val os: DesktopOs, private val parent: () -> Frame?) : FileDialogs {
    override fun save(suggestedName: String, onResult: (Path?) -> Unit) = onEventThread {
        val dialog = FileDialog(parent(), "", FileDialog.SAVE).apply { file = suggestedName }
        dialog.isVisible = true
        onResult(dialog.pathsOrEmpty().firstOrNull())
    }

    override fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit) = onEventThread {
        val dialog = FileDialog(parent(), "", FileDialog.LOAD).apply {
            isMultipleMode = multiple
            if (extensions.isNotEmpty()) {
                // Windows reads a pattern from the file field; the other toolkits take a name filter.
                if (os == DesktopOs.WINDOWS) file = extensions.joinToString(";") { "*.$it" }
                setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
            }
        }
        dialog.isVisible = true
        onResult(dialog.pathsOrEmpty())
    }

    override fun folder(onResult: (Path?) -> Unit) = onEventThread {
        onResult(if (os == DesktopOs.MACOS) macFolder() else swingFolder())
    }

    private fun macFolder(): Path? {
        val property = "apple.awt.fileDialogForDirectories"
        System.setProperty(property, "true")
        try {
            val dialog = FileDialog(parent(), "", FileDialog.LOAD)
            dialog.isVisible = true
            return dialog.pathsOrEmpty().firstOrNull()
        } finally {
            System.setProperty(property, "false")
        }
    }

    private fun swingFolder(): Path? {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(parent()) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }

    // Empty when the dialog was cancelled.
    private fun FileDialog.pathsOrEmpty(): List<Path> = files.map { it.toPath() }

    private fun onEventThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}

/**
 * Tells [lifecycle] that one of the app's own dialogs is open while [delegate] shows it, so taking the focus
 * away from the window for a file chooser is not mistaken for the user leaving the app (which would lock the
 * vault in the middle of an export).
 */
class GatedFileDialogs(private val delegate: FileDialogs, private val lifecycle: DesktopAppLifecycle) : FileDialogs {
    override fun save(suggestedName: String, onResult: (Path?) -> Unit) {
        lifecycle.beginOwnDialog()
        delegate.save(suggestedName) { path ->
            lifecycle.endOwnDialog()
            onResult(path)
        }
    }

    override fun open(multiple: Boolean, extensions: Set<String>, onResult: (List<Path>) -> Unit) {
        lifecycle.beginOwnDialog()
        delegate.open(multiple, extensions) { paths ->
            lifecycle.endOwnDialog()
            onResult(paths)
        }
    }

    override fun folder(onResult: (Path?) -> Unit) {
        lifecycle.beginOwnDialog()
        delegate.folder { path ->
            lifecycle.endOwnDialog()
            onResult(path)
        }
    }
}
