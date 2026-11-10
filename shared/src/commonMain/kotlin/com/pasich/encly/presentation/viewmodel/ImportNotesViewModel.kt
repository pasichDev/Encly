package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.TagMatch
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.importer.Analysis
import com.pasich.encly.data.importer.ImportAnalyzer
import com.pasich.encly.data.importer.ImportDocuments
import com.pasich.encly.data.importer.ImportError
import com.pasich.encly.data.importer.ImportException
import com.pasich.encly.data.importer.ImportFileSource
import com.pasich.encly.data.importer.ImportPlan
import com.pasich.encly.data.importer.ImportPreview
import com.pasich.encly.data.importer.ImportReader
import com.pasich.encly.data.importer.ImportReview
import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.data.importer.ReadResult
import com.pasich.encly.data.importer.ReviewNote
import com.pasich.encly.data.importer.isOutOfMemory
import com.pasich.encly.platform.DocumentRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Why an import ended without writing anything. */
enum class ImportFailure {
    ENCRYPTED_STANDARD_NOTES,
    ENCRYPTED_NOTESNOOK,
    UNSUPPORTED,
    TOO_LARGE,
    NOTHING_FOUND,
    UNREADABLE,
    NO_PICKER,
    VAULT,
    IMPORT,
}

/** The counts shown when an import is over. [notImported]: entries left out (unreadable, unsupported, ...). */
data class ImportResult(val imported: Int, val duplicates: Int, val notImported: Int, val tagsAdded: Int)

enum class ReviewFilter { ALL, NEW, TRASH }

/**
 * The review list and what the user ticked. Only in the ViewModel's memory: never saved in
 * instance state, so a killed process shows the start page instead of anyone's notes.
 */
data class ReviewState(
    val rows: List<ReviewNote>,
    val selected: Set<String>,
    val filter: ReviewFilter = ReviewFilter.ALL,
    /** The row whose read-only text is open, and that text. */
    val expanded: String? = null,
    val expandedText: String = "",
) {
    val newCount: Int get() = rows.count { !it.existing }

    val visible: List<ReviewNote>
        get() = when (filter) {
            ReviewFilter.ALL -> rows
            ReviewFilter.NEW -> rows.filter { !it.existing }
            ReviewFilter.TRASH -> rows.filter { it.trashed }
        }

    companion object {
        /** Every new note ticked; a note that is already in the vault never is. */
        fun of(rows: List<ReviewNote>) = ReviewState(rows, rows.filter { !it.existing }.mapTo(HashSet()) { it.uid })
    }
}

/** Where the Import notes page is. */
sealed interface ImportStep {
    /** The supported apps and the two pickers. */
    data object Start : ImportStep

    /** Reading the selection and recognising its format. */
    data object Reading : ImportStep

    /** Nothing is written until the user confirms. */
    data class Preview(val preview: ImportPreview, val review: ReviewState) : ImportStep

    /** Files of several apps were found: the user names the source. */
    data object ChooseSource : ImportStep

    data object Importing : ImportStep

    data class Done(val result: ImportResult) : ImportStep

    data class Failed(val failure: ImportFailure) : ImportStep
}

/**
 * Settings → Import notes. A selection is read into memory (never to disk), recognised, mapped
 * and shown as counts; only [confirm] writes, through the backup MERGE import in one transaction
 * on the vault that was open when the read started (a re-lock or vault switch fails instead of
 * writing elsewhere). Everything decrypted-in-memory (the read files, the mapped notes) is
 * dropped on cancel, when the import ends, and the moment the vault locks.
 */
@Suppress("TooManyFunctions", "ReturnCount") // The page's actions; each one is a guard (no plan, no step) and a change.
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class ImportNotesViewModel(
    private val files: ImportDocuments,
    private val store: VaultDataStore,
    private val sessionLockManager: SessionLockManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _step = MutableStateFlow<ImportStep>(ImportStep.Start)
    val step: StateFlow<ImportStep> = _step.asStateFlow()

    private var job: Job? = null
    private var bound: VaultDataStore? = null
    private var plan: ImportPlan? = null
    private var pending: ReadResult? = null

    init {
        viewModelScope.launch {
            sessionLockManager.locked.collect { locked ->
                if (locked) {
                    job?.cancel()
                    drop()
                    _step.value = ImportStep.Start
                }
            }
        }
    }

    /** The picker is another app: keep the vault open for it, as the backup import does. */
    fun onPickerLaunched() = sessionLockManager.allowSystemPicker()

    /** No app on this device handles the Storage Access Framework picker. */
    fun onPickerUnavailable() {
        sessionLockManager.endSystemPicker()
        _step.value = ImportStep.Failed(ImportFailure.NO_PICKER)
    }

    fun onFilesPicked(uris: List<DocumentRef>) {
        sessionLockManager.endSystemPicker()
        if (uris.isNotEmpty()) begin(files.files(uris))
    }

    fun onFolderPicked(tree: DocumentRef?) {
        sessionLockManager.endSystemPicker()
        if (tree != null) begin(files.tree(tree))
    }

    /** Starts reading [source]; also the entry point for tests with their own sources. */
    internal fun begin(source: ImportFileSource) {
        if (_step.value is ImportStep.Reading || _step.value is ImportStep.Importing) return
        drop()
        val store = try {
            store.bindToCurrentSession()
        } catch (_: RuntimeException) {
            _step.value = ImportStep.Failed(ImportFailure.VAULT)
            return
        }
        bound = store
        _step.value = ImportStep.Reading
        job = viewModelScope.launch { settle(read(store, source)) }
    }

    /**
     * What a read or analysis produced. It is applied on the main thread only, once the job is
     * known to be live, so a cancelled or replaced job can never leave its notes behind.
     */
    private class Outcome(val step: ImportStep, val plan: ImportPlan? = null, val pending: ReadResult? = null)

    private suspend fun settle(outcome: Outcome) {
        currentCoroutineContext().ensureActive()
        if (outcome.step is ImportStep.Failed) {
            drop()
        } else {
            plan = outcome.plan
            pending = outcome.pending
        }
        _step.value = outcome.step
    }

    // The Error branch rethrows all but the out-of-memory one.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(store: VaultDataStore, source: ImportFileSource): Outcome = try {
        withContext(ioDispatcher) {
            val context = currentCoroutineContext()
            val read = ImportReader().read(source) { context.ensureActive() }
            show(ImportAnalyzer.analyze(read), read, store)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ImportException) {
        failure(failureOf(e.error))
    } catch (_: IOException) {
        failure(ImportFailure.UNREADABLE)
    } catch (e: Error) {
        if (!e.isOutOfMemory()) throw e
        failure(ImportFailure.TOO_LARGE)
    } catch (_: RuntimeException) {
        // The vault closed while reading, or the snapshot failed. The cause can quote content.
        failure(ImportFailure.VAULT)
    }

    private fun failure(failure: ImportFailure) = Outcome(ImportStep.Failed(failure))

    /** Pure: nothing of the view model is touched here, as this runs off the main thread. */
    private suspend fun show(analysis: Analysis, read: ReadResult, store: VaultDataStore): Outcome = when (analysis) {
        is Analysis.Ready -> {
            val existing = store.snapshot().notes.mapTo(HashSet()) { it.uid }
            val ready = analysis.plan.withExisting(existing)
            Outcome(ImportStep.Preview(ready.preview, ReviewState.of(ready.rows)), plan = ready)
        }

        // Kept in memory so the user's choice can be applied without reading again.
        Analysis.Ambiguous -> Outcome(ImportStep.ChooseSource, pending = read)

        is Analysis.Encrypted -> failure(
            if (analysis.source == ImportSource.NOTESNOOK) {
                ImportFailure.ENCRYPTED_NOTESNOOK
            } else {
                ImportFailure.ENCRYPTED_STANDARD_NOTES
            },
        )

        is Analysis.Failed -> failure(failureOf(analysis.error))
    }

    /** The user named the source of an ambiguous selection; it still has to validate. */
    fun chooseSource(source: ImportSource) {
        val read = pending ?: return
        if (_step.value != ImportStep.ChooseSource) return
        val store = bound ?: return
        _step.value = ImportStep.Reading
        job = viewModelScope.launch {
            settle(
                try {
                    withContext(ioDispatcher) { show(ImportAnalyzer.analyze(read, forced = source), read, store) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ImportException) {
                    failure(failureOf(e.error))
                } catch (_: RuntimeException) {
                    failure(ImportFailure.VAULT)
                },
            )
        }
    }

    /** Imports the previewed notes: one transaction, all or nothing. */
    fun confirm() {
        val ready = plan ?: return
        val store = bound ?: return
        val review = (_step.value as? ImportStep.Preview)?.review ?: return
        val selected = review.selected
        if (selected.isEmpty()) return
        val preview = ready.preview
        val payload = ImportReview.selection(ready.payload, selected)
        _step.value = ImportStep.Importing
        job = viewModelScope.launch {
            _step.value = try {
                val summary = withContext(ioDispatcher) {
                    BackupImporter.import(payload, ImportMode.MERGE, store, TagMatch.UID_OR_NAME)
                }
                drop()
                ImportStep.Done(
                    ImportResult(
                        imported = summary.notesAdded,
                        duplicates = payload.notes.size - summary.notesAdded + review.rows.count { it.existing },
                        notImported = preview.unsupported + preview.filesSkipped + preview.nestedArchives +
                            (review.newCount - selected.size),
                        tagsAdded = summary.tagsAdded,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupException) {
                failed(ImportFailure.IMPORT)
            } catch (_: RuntimeException) {
                failed(ImportFailure.IMPORT)
            }
        }
    }

    fun toggle(uid: String) = review { state ->
        if (state.rows.firstOrNull { it.uid == uid }?.existing != false) {
            state
        } else {
            state.copy(selected = if (uid in state.selected) state.selected - uid else state.selected + uid)
        }
    }

    /** Ticks every new note, or clears the selection if they are all ticked already. */
    fun toggleAll() = review { state ->
        val new = state.rows.filter { !it.existing }.mapTo(HashSet()) { it.uid }
        state.copy(selected = if (state.selected.containsAll(new)) emptySet() else new)
    }

    fun setFilter(filter: ReviewFilter) = review { it.copy(filter = filter) }

    /** Opens (or closes) the read-only text of one note. */
    fun expand(uid: String) = review { state ->
        if (state.expanded == uid) {
            state.copy(expanded = null, expandedText = "")
        } else {
            val note = plan?.payload?.notes?.firstOrNull { it.uid == uid }
            state.copy(expanded = uid, expandedText = note?.let(ImportReview::body).orEmpty())
        }
    }

    private fun review(change: (ReviewState) -> ReviewState) {
        _step.update { step -> if (step is ImportStep.Preview) step.copy(review = change(step.review)) else step }
    }

    /** Back to the start page; a read in progress is stopped and nothing has been written. */
    fun cancel() {
        job?.cancel()
        drop()
        _step.value = ImportStep.Start
    }

    override fun onCleared() {
        job?.cancel()
        drop()
        super.onCleared()
    }

    private fun failed(failure: ImportFailure): ImportStep {
        drop()
        return ImportStep.Failed(failure)
    }

    private fun drop() {
        plan = null
        pending = null
        bound = null
    }

    private fun failureOf(error: ImportError): ImportFailure = when (error) {
        ImportError.TOO_LARGE -> ImportFailure.TOO_LARGE
        ImportError.UNSUPPORTED -> ImportFailure.UNSUPPORTED
        ImportError.NOTHING_FOUND -> ImportFailure.NOTHING_FOUND
        ImportError.UNREADABLE -> ImportFailure.UNREADABLE
    }
}
