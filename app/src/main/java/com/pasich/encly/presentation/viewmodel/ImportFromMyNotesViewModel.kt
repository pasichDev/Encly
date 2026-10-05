package com.pasich.encly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.ImportSummary
import com.pasich.encly.data.backup.TagMatch
import com.pasich.encly.data.handoff.HandoffError
import com.pasich.encly.data.handoff.HandoffException
import com.pasich.encly.data.handoff.HandoffImport
import com.pasich.encly.data.handoff.HandoffPreview
import com.pasich.encly.data.handoff.HandoffStaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Where a My Notes hand-off is. */
sealed interface HandoffStep {
    /** Waiting for the vault: resolving its state, or for the user to unlock it. */
    data object Waiting : HandoffStep

    /** Copying, reading and mapping My Notes' data. */
    data object Reading : HandoffStep

    /** Nothing is written until the user confirms. */
    data class Preview(val preview: HandoffPreview) : HandoffStep

    data object Importing : HandoffStep

    /** [skipped]: already in the vault, plus records the hand-off could not use (blank tasks). */
    data class Done(val summary: ImportSummary, val skipped: Int) : HandoffStep

    /** [vaultUnavailable]: no vault to import into (not set up, or it cannot be opened here). */
    data class Failed(val error: HandoffError, val vaultUnavailable: Boolean = false) : HandoffStep
}

/**
 * The receiving side of the My Notes hand-off (see ImportFromMyNotesActivity, which checks
 * the caller first). The vault must be unlocked through the normal lock screen before the
 * URI is read at all; the data is then shown as counts, and imported only on [confirm].
 */
@HiltViewModel
class ImportFromMyNotesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
    private val backupManager: BackupManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val staging = HandoffStaging(File(context.cacheDir, HandoffStaging.DIR_NAME))

    private val _step = MutableStateFlow<HandoffStep>(HandoffStep.Waiting)
    val step: StateFlow<HandoffStep> = _step.asStateFlow()

    /** True while the vault is closed: the lock screen is shown instead of the hand-off. */
    val locked: StateFlow<Boolean> = sessionLockManager.locked

    private var started = false
    private var handoff: HandoffImport? = null

    /**
     * Starts the hand-off of [uri] once; a recreated activity calls it again and is ignored. One
     * that already finished before the process was killed shows its counts again instead of
     * offering the same import a second time.
     */
    fun start(uri: Uri) {
        if (started) return
        started = true
        restoredDone()?.let {
            _step.value = it
            return
        }
        viewModelScope.launch {
            if (!vaultOpen() && !requireUnlock()) {
                _step.value = HandoffStep.Failed(HandoffError.FAILED, vaultUnavailable = true)
                return@launch
            }
            sessionLockManager.locked.first { !it && securityManager.isDatabaseUnlocked() }
            _step.value = HandoffStep.Reading
            _step.value = read(uri)
        }
    }

    /** Imports the previewed data in one transaction; nothing is written on failure. */
    fun confirm() {
        val pending = handoff ?: return
        if (_step.value !is HandoffStep.Preview) return
        _step.value = HandoffStep.Importing
        viewModelScope.launch {
            _step.value = try {
                val summary = withContext(ioDispatcher) {
                    backupManager.import(pending.payload, ImportMode.MERGE, TagMatch.UID_OR_NAME)
                }
                handoff = null
                HandoffStep.Done(summary, skipped = summary.skipped + pending.dropped).also(::rememberDone)
            } catch (_: BackupException) {
                HandoffStep.Failed(HandoffError.FAILED)
            }
        }
    }

    /** The lock screen found that nothing can open the vault on this device any more. */
    fun onVaultLost() {
        handoff = null
        _step.value = HandoffStep.Failed(HandoffError.FAILED, vaultUnavailable = true)
    }

    override fun onCleared() {
        // The staged copy is already gone: each load deletes its own.
        handoff = null
    }

    /** Only the counts, which is all Done shows: nothing of the notes themselves. */
    private fun rememberDone(done: HandoffStep.Done) {
        val summary = done.summary
        savedStateHandle[KEY_DONE] = intArrayOf(
            summary.notesAdded,
            summary.tagsAdded,
            summary.tasksAdded,
            summary.skipped,
            summary.subtasksAdded,
            done.skipped,
        )
    }

    private fun restoredDone(): HandoffStep.Done? {
        val counts = savedStateHandle.get<IntArray>(KEY_DONE)?.takeIf { it.size == DONE_COUNTS } ?: return null
        // In the order rememberDone wrote them.
        val next = counts.iterator()
        val summary = ImportSummary(
            notesAdded = next.nextInt(),
            tagsAdded = next.nextInt(),
            tasksAdded = next.nextInt(),
            skipped = next.nextInt(),
            subtasksAdded = next.nextInt(),
        )
        return HandoffStep.Done(summary, skipped = next.nextInt())
    }

    private fun vaultOpen(): Boolean = securityManager.isDatabaseUnlocked() && !sessionLockManager.locked.value

    /**
     * A committed vault that is closed shows the lock screen (as MainActivity does at startup).
     * False when there is no vault to unlock: not set up yet, damaged, or from Encly 1.x.
     */
    private suspend fun requireUnlock(): Boolean {
        val status = withContext(ioDispatcher) { securityManager.resolveInitialStatus() }
        if (status != InitialStatus.AUTH) return false
        sessionLockManager.requireUnlock()
        return true
    }

    private suspend fun read(uri: Uri): HandoffStep = try {
        val loaded = withContext(ioDispatcher) { staging.load { context.contentResolver.openInputStream(uri) } }
        handoff = loaded
        HandoffStep.Preview(loaded.preview)
    } catch (e: HandoffException) {
        HandoffStep.Failed(e.error)
    } catch (_: BackupException) {
        // The mapped data failed the backup validation: a malformed hand-off.
        HandoffStep.Failed(HandoffError.INVALID_PAYLOAD)
    } catch (_: IOException) {
        HandoffStep.Failed(HandoffError.FAILED)
    } catch (_: SecurityException) {
        // No read grant on the URI.
        HandoffStep.Failed(HandoffError.FAILED)
    }
}

/** SavedStateHandle key of a finished hand-off's counts (see ImportFromMyNotesViewModel.start). */
private const val KEY_DONE = "handoff_done_counts"
private const val DONE_COUNTS = 6
