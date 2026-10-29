package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.backup_error_no_picker
import com.pasich.encly.core.resources.main_drawer_tasks
import com.pasich.encly.core.resources.markdown_export_done
import com.pasich.encly.core.resources.note_preview_unreadable
import com.pasich.encly.core.resources.priority_high
import com.pasich.encly.core.resources.priority_low
import com.pasich.encly.core.resources.priority_medium
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.export.MarkdownDocuments
import com.pasich.encly.data.export.MarkdownExport
import com.pasich.encly.data.export.MarkdownLabels
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.utils.todayStamp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a Markdown export writes. */
sealed interface MarkdownExportTarget {
    /** Every note that is not in the trash, and the tasks, as a `.zip` of `.md` files. */
    data object AllData : MarkdownExportTarget

    /** One note, as one `.md` file. */
    data class SingleNote(val noteId: Long) : MarkdownExportTarget
}

/**
 * The plaintext export of notes as Markdown. The file is **not encrypted**, so the flow is: a
 * warning ([BackupStep.ConfirmPlainExport]), then the same re-authentication every export uses
 * ([ReauthFlow]: the PIN, or biometrics when enabled), then the system save dialog, then the
 * content streams straight into the document the user created there.
 *
 * - The vault is read only after the save dialog returns, and only through the session that was
 *   authenticated: a vault that re-locked meanwhile fails instead of exporting.
 * - It exports the vault that is open, whichever it is (the decoy vault exports what it holds).
 * - Nothing plaintext is held while another app (the save dialog) is in front, and no copy is
 *   made anywhere else: no temp file, no cache, no share intent.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Suppress("TooManyFunctions") // One function per step of the flow, and the helpers of the write.
@Inject
class MarkdownExportViewModel(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
    private val store: VaultDataStore,
    private val documents: MarkdownDocuments,
    private val strings: AppStrings,
) : ViewModel() {

    private val state = BackupFlowState(viewModelScope, null, ::dropPending)
    val uiState: StateFlow<BackupUiState> = state.ui.asStateFlow()

    val reauthFlow = ReauthFlow(state, securityManager, onAuthenticated = ::proceed)

    private var target: MarkdownExportTarget? = null

    /** The vault session that was authenticated; the export reads this one and no other. */
    private var session: VaultDataStore? = null

    init {
        // A re-lock drops what the flow holds at once, even while the app is in the background.
        viewModelScope.launch {
            sessionLockManager.locked.collect { locked ->
                if (locked) {
                    dropPending()
                    state.ui.update { it.copy(step = BackupStep.Idle, busy = false) }
                }
            }
        }
    }

    /** Starts an export of [target]: first the warning that the file will not be encrypted. */
    fun start(target: MarkdownExportTarget) {
        dropPending()
        this.target = target
        state.go(BackupStep.ConfirmPlainExport)
    }

    /** "Continue" on the warning: re-authenticate before anything is read. */
    fun confirmWarning() {
        if (state.step != BackupStep.ConfirmPlainExport) return
        val biometric = securityManager.canReauthWithBiometric()
        state.go(BackupStep.Reauth(BackupAction.EXPORT_MARKDOWN, biometric))
    }

    private fun proceed(action: BackupAction) {
        val target = target
        // A PIN that verified after the flow was cancelled (or restarted) must not carry on: only the
        // re-authentication of the export now waiting for it counts.
        val waiting = (state.step as? BackupStep.Reauth)?.action == BackupAction.EXPORT_MARKDOWN
        if (action != BackupAction.EXPORT_MARKDOWN || target == null || !waiting) return
        try {
            // Tied to this opening of the vault: after a re-lock it fails instead of reading.
            session = store.bindToCurrentSession()
        } catch (_: RuntimeException) {
            state.fail(BackupError.VAULT_UNAVAILABLE)
            return
        }
        state.go(
            when (target) {
                MarkdownExportTarget.AllData -> BackupStep.PickMarkdownTarget(
                    datedName("encly-notes", "zip"),
                    zip = true,
                )

                // A date only, never the title: the name shows in the system file dialog and its Recents.
                is MarkdownExportTarget.SingleNote -> BackupStep.PickMarkdownTarget(
                    datedName("encly-note", "md"),
                    zip = false,
                )
            },
        )
    }

    /**
     * The save dialog is another app. It keeps the vault open for a short while (like the import
     * dialog does), because the notes are read when it returns.
     */
    fun onSystemPickerLaunched() {
        sessionLockManager.allowSystemPicker()
        state.go(BackupStep.Idle)
    }

    /** No app on this device handles the save dialog. */
    fun onSystemPickerUnavailable() {
        sessionLockManager.endSystemPicker()
        state.finish(BackupMessage.Text(Res.string.backup_error_no_picker))
    }

    /** The save dialog returned: [uri] is the new, empty document, or null when it was cancelled. */
    fun onTarget(uri: DocumentRef?) {
        sessionLockManager.endSystemPicker()
        val bound = session
        val what = target
        dropPending()
        if (uri == null) return
        if (bound == null || what == null) {
            state.launchBusy { discard(uri) }
            return
        }
        state.launchBusy {
            try {
                export(bound, what, uri)
                state.finish(BackupMessage.Text(Res.string.markdown_export_done))
            } catch (e: BackupException) {
                state.fail(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // The vault closed under the export (a re-lock): nothing readable was written.
                AppLogger.w(TAG, "Markdown export failed")
                discard(uri)
                state.fail(BackupError.VAULT_UNAVAILABLE)
            }
        }
    }

    private suspend fun export(bound: VaultDataStore, what: MarkdownExportTarget, uri: DocumentRef) {
        val snapshot = when (what) {
            MarkdownExportTarget.AllData -> bound.snapshot()

            // Only that note and its tag are read, not the whole vault.
            is MarkdownExportTarget.SingleNote -> bound.snapshotOfNote(what.noteId)
        }
        val labels = labels()
        // A re-lock that landed after the read must not be followed by a write.
        bound.assertCurrentSession()
        // Not cancelled by a re-lock clearing this screen: a half-written document would look like
        // a complete export.
        withContext(NonCancellable + Dispatchers.IO) {
            documents.write(uri) { out ->
                when (what) {
                    MarkdownExportTarget.AllData -> MarkdownExport.writeAll(snapshot, labels, out)

                    is MarkdownExportTarget.SingleNote ->
                        if (!MarkdownExport.writeNote(snapshot, what.noteId, labels, out)) {
                            throw BackupException(BackupError.IO)
                        }
                }
            }
        }
    }

    /** Removes the empty document the save dialog created, off the main thread. */
    private suspend fun discard(uri: DocumentRef) =
        withContext(NonCancellable + Dispatchers.IO) { documents.discard(uri) }

    fun cancel() {
        sessionLockManager.endSystemPicker()
        dropPending()
        state.go(BackupStep.Idle)
    }

    fun clearMessage() {
        state.ui.update { it.copy(message = null) }
    }

    override fun onCleared() {
        dropPending()
        super.onCleared()
    }

    private fun dropPending() {
        target = null
        session = null
    }

    private suspend fun labels() = MarkdownLabels(
        untitled = strings.get(Res.string.untitled),
        unreadable = strings.get(Res.string.note_preview_unreadable),
        tasks = strings.get(Res.string.main_drawer_tasks),
        priorities = mapOf(
            HIGH to strings.get(Res.string.priority_high),
            MEDIUM to strings.get(Res.string.priority_medium),
            LOW to strings.get(Res.string.priority_low),
        ),
    )

    /** "encly-notes-2026-10-25.zip": a date only, never a title. */
    private fun datedName(prefix: String, extension: String): String = "$prefix-${todayStamp()}.$extension"

    private companion object {
        const val TAG = "MarkdownExport"
        const val HIGH = 2
        const val MEDIUM = 1
        const val LOW = 0
    }
}
