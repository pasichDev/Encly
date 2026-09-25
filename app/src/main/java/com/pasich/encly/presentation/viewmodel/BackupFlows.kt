package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.data.backup.ImportMode
import com.pasich.encly.data.backup.ImportSummary
import com.pasich.encly.presentation.screen.backup.backupErrorMessage
import com.pasich.encly.presentation.screen.backup.backupExportErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a re-authenticated flow goes on to do. [CREATE_PHRASE], [REPLACE_PHRASE] and [ERASE]
 * start from Settings → Security; they share the re-authentication and phrase steps with
 * backups.
 */
enum class BackupAction { EXPORT, IMPORT, CREATE_PHRASE, REPLACE_PHRASE, ERASE }

/** Where a backup or vault flow currently is. Each step is one dialog or system picker. */
sealed interface BackupStep {
    data object Idle : BackupStep

    /**
     * Re-authentication (PIN, or biometric when enabled) before any export or import. [failures]
     * counts wrong PINs, so each one shakes the dots, even with the same error.
     */
    data class Reauth(
        val action: BackupAction,
        val biometric: Boolean,
        @param:StringRes val error: Int? = null,
        val failures: Int = 0,
    ) : BackupStep

    /** Export needs a recovery phrase and this vault has none: explain and offer to create it. */
    data object NeedsPhrase : BackupStep

    /** [replacing]: the words will replace the vault's current recovery phrase. */
    data class ShowNewPhrase(val words: List<String>, val replacing: Boolean = false) : BackupStep

    /**
     * Before new words replace the recovery phrase: the old words stop unlocking this vault,
     * and backups already made with them still open only with them.
     */
    data object ConfirmReplacePhrase : BackupStep

    /**
     * 0-based positions of the words the user must type back, and those words, so each field
     * can say at once whether it is right (the words were just shown anyway).
     */
    data class CheckNewPhrase(
        val positions: List<Int>,
        val expected: List<String>,
        @param:StringRes val error: Int? = null,
    ) : BackupStep

    /** A recovery-seed vault without a backup key must type its words once. */
    data class ConfirmExistingPhrase(@param:StringRes val error: Int? = null) : BackupStep

    data object PickExportTarget : BackupStep
    data object PickImportFile : BackupStep

    data class EnterImportPhrase(@param:StringRes val error: Int? = null) : BackupStep

    data class ChooseImportMode(val notes: Int, val tasks: Int, val tags: Int) : BackupStep
    data object ConfirmReplace : BackupStep

    /** Last chance before every note, task, tag and key on this device is deleted. */
    data object ConfirmErase : BackupStep
}

/** A one-shot result line. Carries counts at most, never content. */
sealed interface BackupMessage {
    data class Text(@param:StringRes val id: Int) : BackupMessage
    data class Imported(val summary: ImportSummary) : BackupMessage
}

data class BackupUiState(
    val lastExportAt: Long? = null,
    val busy: Boolean = false,
    val step: BackupStep = BackupStep.Idle,
    val message: BackupMessage? = null,
    /** The vault was erased: the app must start over with onboarding. */
    val erased: Boolean = false,
)

/** State shared by the backup flows, owned by [BackupViewModel]. */
class BackupFlowState(private val scope: CoroutineScope, lastExportAt: Long?, private val dropPending: () -> Unit) {
    val ui = MutableStateFlow(BackupUiState(lastExportAt = lastExportAt))

    val step: BackupStep get() = ui.value.step

    fun go(step: BackupStep) = ui.update { it.copy(step = step) }

    fun message(@StringRes id: Int) = ui.update { it.copy(message = BackupMessage.Text(id)) }

    /** Ends the flow: drops every pending buffer and shows [message]. */
    fun finish(message: BackupMessage) {
        dropPending()
        ui.update { it.copy(step = BackupStep.Idle, message = message) }
    }

    fun fail(error: BackupError) = finish(BackupMessage.Text(backupErrorMessage(error)))

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            ui.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                ui.update { it.copy(busy = false) }
            }
        }
    }
}

/**
 * Runs [block] with the typed words normalized (lower case, single spaces) in a CharArray that
 * is wiped afterwards. The Compose text field's own String cannot be wiped (see SECURITY.md).
 */
internal fun <T> withNormalizedPhrase(backupManager: BackupManager, input: String, block: (CharArray) -> T): T =
    withNormalizedPhrase(backupManager, input.toCharArray(), block)

/** As above, for words already in a CharArray; [input] is wiped too. */
internal fun <T> withNormalizedPhrase(backupManager: BackupManager, input: CharArray, block: (CharArray) -> T): T {
    val phrase = try {
        backupManager.normalizeRecoveryPhrase(input)
    } finally {
        SensitiveDataCleaner.clear(input)
    }
    return try {
        block(phrase)
    } finally {
        SensitiveDataCleaner.clear(phrase)
    }
}

/**
 * Re-authentication before any export or import: the PIN, or biometric when enabled.
 * [onAuthenticated] continues the flow that was started.
 */
class ReauthFlow(
    private val state: BackupFlowState,
    private val securityManager: SecurityManager,
    private val onAuthenticated: suspend (BackupAction) -> Unit,
) {
    fun pinLockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    /**
     * Checks [pin], which is wiped. During a PIN lockout nothing is verified (every attempt
     * would be refused, even the right PIN), so no "wrong PIN" is shown either: the dialog shows
     * the remaining lockout instead.
     */
    fun submitPin(pin: CharArray) {
        val step = state.step as? BackupStep.Reauth
        if (step == null || pinLockoutRemainingMillis() > 0L) {
            SensitiveDataCleaner.clear(pin)
            if (step != null) state.go(step.copy(error = null))
            return
        }
        state.launchBusy {
            val ok = withContext(Dispatchers.Default) {
                try {
                    securityManager.verifyPin(pin)
                } finally {
                    SensitiveDataCleaner.clear(pin)
                }
            }
            if (ok) {
                onAuthenticated(step.action)
            } else {
                state.go(step.copy(error = R.string.lock_wrong_pin, failures = step.failures + 1))
            }
        }
    }

    fun withBiometric(activity: FragmentActivity) {
        val step = state.step as? BackupStep.Reauth ?: return
        securityManager.confirmBiometric(activity) { ok ->
            if (ok) {
                state.launch { onAuthenticated(step.action) }
            } else {
                state.go(step.copy(error = R.string.biometric_change_not_confirmed))
            }
        }
    }
}

/**
 * Export: seal the whole vault to the recovery phrase, then let the user pick where the file
 * goes. The file is sealed before the picker opens, so a failure never leaves an empty file.
 */
class ExportFlow(
    private val state: BackupFlowState,
    private val backupManager: BackupManager,
    private val documents: BackupDocuments,
) {
    private var sealed: ByteArray? = null

    suspend fun seal() {
        try {
            sealed = withContext(Dispatchers.Default) { backupManager.createBackup() }
            state.go(BackupStep.PickExportTarget)
        } catch (e: BackupException) {
            state.finish(BackupMessage.Text(backupExportErrorMessage(e.error)))
        }
    }

    fun onTarget(uri: Uri?) {
        val file = sealed ?: return
        sealed = null
        if (uri == null) return
        state.launchBusy {
            try {
                // Not cancelled by a re-lock clearing this screen: a half-written document
                // would look like a backup and never restore. The file is ciphertext.
                withContext(NonCancellable + Dispatchers.IO) { documents.write(uri, file) }
                backupManager.recordExport()
                state.ui.update { it.copy(lastExportAt = backupManager.lastExportAt()) }
                state.finish(BackupMessage.Text(R.string.backup_export_done))
            } catch (e: BackupException) {
                state.fail(e.error)
            }
        }
    }

    fun drop() {
        sealed = null
    }
}

/**
 * Gives a vault the recovery phrase exports need: a new one (shown, then three words typed
 * back) or, for an older recovery-seed vault, a one-time confirmation of its words.
 */
@Suppress("TooManyFunctions") // Create, replace, show, check and confirm share one phrase buffer.
class RecoveryPhraseFlow(
    private val state: BackupFlowState,
    private val backupManager: BackupManager,
    private val phraseSetup: BackupPhraseSetup,
    private val onReady: suspend () -> Unit,
) {
    private var newPhrase: CharArray? = null

    /** The new words replace the vault's recovery phrase instead of being its first one. */
    private var replacing = false

    fun create() = start(replace = false)

    /** "Replace" on [BackupStep.ConfirmReplacePhrase]: new words, shown, then checked. */
    fun replace() {
        if (state.step == BackupStep.ConfirmReplacePhrase) start(replace = true)
    }

    private fun start(replace: Boolean) {
        drop()
        val phrase = phraseSetup.generate()
        newPhrase = phrase
        replacing = replace
        state.go(BackupStep.ShowNewPhrase(String(phrase).split(' '), replacing))
    }

    fun writtenDown() {
        val words = (state.step as? BackupStep.ShowNewPhrase)?.words ?: return
        val positions = words.indices.shuffled().take(CHECKED_WORDS).sorted()
        state.go(BackupStep.CheckNewPhrase(positions, positions.map(words::get)))
    }

    /** "Show the words again" (and back) from the check. */
    fun showAgain() {
        val phrase = newPhrase ?: return
        if (state.step is BackupStep.CheckNewPhrase) {
            state.go(BackupStep.ShowNewPhrase(String(phrase).split(' '), replacing))
        }
    }

    fun submitCheck(answers: List<String>) {
        val step = state.step as? BackupStep.CheckNewPhrase
        val phrase = newPhrase
        when {
            step == null || phrase == null -> Unit

            !matches(phrase, step.positions, answers) ->
                state.go(step.copy(error = R.string.backup_phrase_check_wrong))

            else -> add(phrase)
        }
    }

    private fun add(phrase: CharArray) {
        val replace = replacing
        state.launchBusy {
            val saved = withContext(Dispatchers.Default) {
                if (replace) phraseSetup.replace(phrase) else phraseSetup.add(phrase)
            }
            drop()
            when {
                !saved -> state.finish(BackupMessage.Text(R.string.backup_error_phrase_setup))

                replace -> state.finish(BackupMessage.Text(R.string.security_recovery_replaced))

                else -> {
                    state.message(R.string.backup_phrase_added)
                    onReady()
                }
            }
        }
    }

    /** The typed words; [input] is wiped. */
    fun submitExisting(input: CharArray) {
        if (state.step !is BackupStep.ConfirmExistingPhrase) {
            SensitiveDataCleaner.clear(input)
            return
        }
        state.launchBusy {
            val ok = withContext(Dispatchers.Default) {
                withNormalizedPhrase(backupManager, input) { phraseSetup.confirm(it) }
            }
            if (ok) onReady() else state.go(BackupStep.ConfirmExistingPhrase(R.string.backup_error_phrase_mismatch))
        }
    }

    /** The user edited the words: an error under them goes away. */
    fun clearError() {
        when (val step = state.step) {
            is BackupStep.ConfirmExistingPhrase -> if (step.error != null) state.go(step.copy(error = null))
            is BackupStep.CheckNewPhrase -> if (step.error != null) state.go(step.copy(error = null))
            else -> Unit
        }
    }

    fun drop() {
        newPhrase?.let(SensitiveDataCleaner::clear)
        newPhrase = null
        replacing = false
    }

    private fun matches(phrase: CharArray, positions: List<Int>, answers: List<String>): Boolean {
        val words = String(phrase).split(' ')
        return answers.size == positions.size &&
            positions.zip(answers).all { (index, answer) -> words[index] == answer.trim().lowercase() }
    }

    private companion object {
        const val CHECKED_WORDS = 3
    }
}

/**
 * Import: pick a file, check its header, decrypt and validate it with the typed words, then
 * merge or (after an explicit confirmation) replace, in one transaction.
 */
class ImportFlow(
    private val state: BackupFlowState,
    private val backupManager: BackupManager,
    private val documents: BackupDocuments,
    private val onPickerReturned: () -> Unit = {},
) {
    private var file: ByteArray? = null
    private var decrypted: BackupPayload? = null

    /** The merge/replace choice, to return to when "Replace all data?" is cancelled. */
    private var choice: BackupStep.ChooseImportMode? = null

    fun onFile(uri: Uri?) {
        // The picker is back: its grace on the vault ends here, whatever was picked.
        onPickerReturned()
        if (uri == null) return
        state.launchBusy {
            try {
                file = withContext(Dispatchers.IO) { documents.read(uri).also(BackupCipher::inspect) }
                state.go(BackupStep.EnterImportPhrase())
            } catch (e: BackupException) {
                state.fail(e.error)
            }
        }
    }

    /** The typed words; [input] is wiped. */
    fun submitPhrase(input: CharArray) {
        val encrypted = file
        if (encrypted == null) {
            SensitiveDataCleaner.clear(input)
            return
        }
        state.launchBusy {
            try {
                val payload = withContext(Dispatchers.Default) {
                    withNormalizedPhrase(backupManager, input) { backupManager.decrypt(encrypted, it) }
                }
                decrypted = payload
                file = null
                val step = BackupStep.ChooseImportMode(payload.notes.size, payload.tasks.size, payload.tags.size)
                choice = step
                state.go(step)
            } catch (e: BackupException) {
                if (e.error == BackupError.WRONG_SECRET || e.error == BackupError.INVALID_PHRASE) {
                    state.go(BackupStep.EnterImportPhrase(backupErrorMessage(e.error)))
                } else {
                    state.fail(e.error)
                }
            }
        }
    }

    fun merge() {
        if (state.step is BackupStep.ChooseImportMode) run(ImportMode.MERGE)
    }

    fun askReplace() {
        if (state.step is BackupStep.ChooseImportMode) state.go(BackupStep.ConfirmReplace)
    }

    fun confirmReplace() {
        if (state.step is BackupStep.ConfirmReplace) run(ImportMode.REPLACE)
    }

    /** The user edited the words: a wrong-phrase error under them goes away. */
    fun clearError() {
        val step = state.step as? BackupStep.EnterImportPhrase ?: return
        if (step.error != null) state.go(step.copy(error = null))
    }

    /** "Cancel" on "Replace all data?": back to the choice, the decrypted backup kept. */
    fun backToChoice() {
        val step = choice ?: return
        if (state.step is BackupStep.ConfirmReplace && decrypted != null) state.go(step)
    }

    fun drop() {
        file = null
        decrypted = null
        choice = null
    }

    private fun run(mode: ImportMode) {
        val payload = decrypted ?: return
        decrypted = null
        state.launchBusy {
            try {
                val summary = withContext(Dispatchers.IO) { backupManager.import(payload, mode) }
                state.finish(BackupMessage.Imported(summary))
            } catch (e: BackupException) {
                state.fail(e.error)
            }
        }
    }
}
