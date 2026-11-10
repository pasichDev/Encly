package com.pasich.encly.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.platform.DocumentPickers
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.platform.PickerLauncher
import com.pasich.encly.presentation.navigation.NavRoutes
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/** What a dropped or opened file is, for the flow it belongs to. */
enum class DroppedKind {
    /** An Encly backup (`.enclybak`): the restore flow. */
    BACKUP,

    /** Notes to import: Markdown, plain text, a ZIP or an export of another notes app. */
    NOTES,

    /** A folder of notes to import. */
    FOLDER,
    ;

    companion object {
        private val NOTE_EXTENSIONS = setOf("md", "markdown", "txt", "zip", "json", "jex", "html", "htm")

        /** The kind of [path], or null for a file Encly has no use for. */
        fun of(path: Path): DroppedKind? {
            if (Files.isDirectory(path)) return FOLDER
            val extension = path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return when {
                extension == BackupFormat.FILE_EXTENSION -> BACKUP
                extension in NOTE_EXTENSIONS -> NOTES
                else -> null
            }
        }
    }
}

/**
 * Connects files that arrive without a dialog (dropped on the window, opened with a double click on a backup)
 * to the screens' own pickers. A screen that asks for a file registers its callback through the picker it
 * remembers ([DesktopDocumentPickers]); a dropped file is answered to the most recent screen of the right
 * kind exactly as if the user had chosen it in the dialog, so the existing restore and import flows run
 * unchanged. With no such screen open the matching screen is requested through [navigation] and the file is
 * handed over as soon as it registers (within [PENDING_MILLIS]).
 */
class DropRouter(
    private val navigation: NavigationRequests,
    private val post: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private enum class Target { ONE_FILE, MANY_FILES, FOLDER }

    private class Registration(val target: Target, val accept: (List<DocumentRef>) -> Unit)

    private class Pending(val target: Target, val refs: List<DocumentRef>, val at: Long)

    private val registrations = CopyOnWriteArrayList<Registration>()
    private var pending: Pending? = null

    /** A screen that picks one file registers; the result closes the registration. */
    fun registerOneFile(accept: (DocumentRef?) -> Unit): AutoCloseable =
        register(Target.ONE_FILE) { accept(it.firstOrNull()) }

    /** A screen that picks several files registers. */
    fun registerManyFiles(accept: (List<DocumentRef>) -> Unit): AutoCloseable = register(Target.MANY_FILES, accept)

    /** A screen that picks a folder registers. */
    fun registerFolder(accept: (DocumentRef?) -> Unit): AutoCloseable =
        register(Target.FOLDER) { accept(it.firstOrNull()) }

    /**
     * Routes [files]. Backups win over notes, notes over a folder; anything else is ignored. Returns whether
     * something was routed.
     */
    fun drop(files: List<Path>): Boolean {
        val kinds = files.associateWith(DroppedKind::of)
        val backup = kinds.entries.firstOrNull { it.value == DroppedKind.BACKUP }
        val notes = kinds.filterValues { it == DroppedKind.NOTES }.keys
        val folder = kinds.entries.firstOrNull { it.value == DroppedKind.FOLDER }
        when {
            backup != null -> deliver(Target.ONE_FILE, listOf(backup.key.toDocumentRef()), NavRoutes.BackupRoute)

            notes.isNotEmpty() ->
                deliver(Target.MANY_FILES, notes.map { it.toDocumentRef() }, NavRoutes.ImportNotesRoute)

            folder != null -> deliver(Target.FOLDER, listOf(folder.key.toDocumentRef()), NavRoutes.ImportNotesRoute)

            else -> return false
        }
        return true
    }

    private fun register(target: Target, accept: (List<DocumentRef>) -> Unit): AutoCloseable {
        val registration = Registration(target, accept)
        registrations += registration
        pending?.takeIf { it.target == target }?.let { waiting ->
            pending = null
            if (clock() - waiting.at <= PENDING_MILLIS) post { accept(waiting.refs) }
        }
        return AutoCloseable { registrations -= registration }
    }

    private fun deliver(target: Target, refs: List<DocumentRef>, route: NavRoutes) {
        val handler = registrations.lastOrNull { it.target == target }
        if (handler != null) {
            post { handler.accept(refs) }
        } else {
            pending = Pending(target, refs, clock())
            navigation.request(route.name)
        }
    }

    companion object {
        const val PENDING_MILLIS = 15_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * The document dialogs of the desktop: the OS's own dialogs ([FileDialogs]) behind the pickers the screens
 * remember, and the registration of those pickers with [DropRouter] so that a dropped file answers them too.
 */
class DesktopDocumentPickers(private val dialogs: FileDialogs, private val drops: DropRouter) : DocumentPickers {
    @Composable
    override fun rememberCreateDocument(mimeType: String, onPick: (DocumentRef?) -> Unit): PickerLauncher<String> {
        val current by rememberUpdatedState(onPick)
        return remember(dialogs) {
            PickerLauncher { suggestedName ->
                dialogs.save(suggestedName) { current(it?.toDocumentRef()) }
            }
        }
    }

    @Composable
    override fun rememberOpenDocument(onPick: (DocumentRef?) -> Unit): PickerLauncher<Array<String>> {
        val current by rememberUpdatedState(onPick)
        DisposableEffect(drops) {
            val registration = drops.registerOneFile { current(it) }
            onDispose(registration::close)
        }
        return remember(dialogs) {
            PickerLauncher { mimeTypes ->
                dialogs.open(multiple = false, extensions = FileFilters.extensionsFor(mimeTypes)) { paths ->
                    current(paths.firstOrNull()?.toDocumentRef())
                }
            }
        }
    }

    @Composable
    override fun rememberOpenDocuments(onPick: (List<DocumentRef>) -> Unit): PickerLauncher<Array<String>> {
        val current by rememberUpdatedState(onPick)
        DisposableEffect(drops) {
            val registration = drops.registerManyFiles { current(it) }
            onDispose(registration::close)
        }
        return remember(dialogs) {
            PickerLauncher { mimeTypes ->
                dialogs.open(multiple = true, extensions = FileFilters.extensionsFor(mimeTypes)) { paths ->
                    current(paths.map { it.toDocumentRef() })
                }
            }
        }
    }

    @Composable
    override fun rememberOpenFolder(onPick: (DocumentRef?) -> Unit): PickerLauncher<Unit> {
        val current by rememberUpdatedState(onPick)
        DisposableEffect(drops) {
            val registration = drops.registerFolder { current(it) }
            onDispose(registration::close)
        }
        return remember(dialogs) {
            PickerLauncher { dialogs.folder { current(it?.toDocumentRef()) } }
        }
    }
}
