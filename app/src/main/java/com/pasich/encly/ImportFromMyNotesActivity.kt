package com.pasich.encly

import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.handoff.AndroidPackageSignatures
import com.pasich.encly.data.handoff.HandoffError
import com.pasich.encly.data.handoff.MyNotesCallerVerifier
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.presentation.components.SecureTextInputBoundary
import com.pasich.encly.presentation.components.SensitiveClipboardBoundary
import com.pasich.encly.presentation.screen.handoff.ImportFromMyNotesScreen
import com.pasich.encly.presentation.viewmodel.HandoffStep
import com.pasich.encly.presentation.viewmodel.ImportFromMyNotesViewModel
import com.pasich.encly.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Receives the one-way hand-off from My Notes (pasichDev/Encly#46): My Notes starts it with
 * `startActivityForResult`, [ACTION_IMPORT_FROM_MY_NOTES] and a `content://` URI to a ZIP it
 * grants read access to. Encly never sends anything back except the result counts.
 *
 * Order, each step only after the one before succeeded:
 * 1. the caller must be My Notes, signed with a pinned certificate ([MyNotesCallerVerifier]);
 *    anything else is refused before the URI is even looked at;
 * 2. the vault is unlocked through the normal lock screen;
 * 3. the ZIP is copied, read and deleted, and only counts are shown;
 * 4. the import runs when the user confirms.
 *
 * Result: `RESULT_OK` with the int extras [EXTRA_NOTES], [EXTRA_TASKS], [EXTRA_TAGS],
 * [EXTRA_SKIPPED]; or `RESULT_CANCELED` with the string extra [EXTRA_REASON]
 * ([HandoffError.reason]).
 */
@AndroidEntryPoint
class ImportFromMyNotesActivity : AppCompatActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var keyboardPrivacy: KeyboardPrivacy

    private val viewModel: ImportFromMyNotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // As in MainActivity: protected before the first frame.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        protectWindow()

        val verifier = MyNotesCallerVerifier(AndroidPackageSignatures(packageManager))
        if (!verifier.isTrusted(callingPackage)) {
            finishWith(HandoffError.UNTRUSTED_CALLER)
            return
        }
        val uri = intent?.data
        if (intent?.action != ACTION_IMPORT_FROM_MY_NOTES || uri?.scheme != ContentResolver.SCHEME_CONTENT) {
            finishWith(HandoffError.INVALID_PAYLOAD)
            return
        }
        // Leaving before the end (Back, the close button) reports a cancellation.
        setResult(RESULT_CANCELED, reasonIntent(HandoffError.CANCELLED))
        enableEdgeToEdge()

        var themeReady by mutableStateOf(false)
        lifecycleScope.launch {
            // The stored palette and fonts, read off the main thread as MainActivity does.
            withContext(Dispatchers.IO) {
                try {
                    settingsRepository.loadThemeSettings(existingInstall = !securityManager.isOnboardingShow())
                } catch (_: IOException) {
                    // An unreadable settings file: the theme starts from its defaults.
                }
            }
            themeReady = true
        }
        lifecycleScope.launch { viewModel.step.collect(::publishResult) }
        viewModel.start(uri)

        setContent {
            if (!themeReady) return@setContent
            val strictKeyboard by keyboardPrivacy.strict.collectAsState()
            AppTheme {
                SecureTextInputBoundary(strict = strictKeyboard) {
                    SensitiveClipboardBoundary {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .windowInsetsPadding(WindowInsets.statusBars),
                        ) {
                            ImportFromMyNotesScreen(onClose = ::finish)
                        }
                    }
                }
            }
        }
    }

    /** Keeps the activity result in step with the hand-off, so any way out reports it. */
    private fun publishResult(step: HandoffStep) {
        when (step) {
            is HandoffStep.Done -> setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_NOTES, step.summary.notesAdded)
                    .putExtra(EXTRA_TASKS, step.summary.tasksAdded)
                    .putExtra(EXTRA_TAGS, step.summary.tagsAdded)
                    .putExtra(EXTRA_SKIPPED, step.skipped),
            )

            is HandoffStep.Failed -> setResult(RESULT_CANCELED, reasonIntent(step.error))

            else -> Unit
        }
    }

    private fun finishWith(error: HandoffError) {
        setResult(RESULT_CANCELED, reasonIntent(error))
        finish()
    }

    private fun reasonIntent(error: HandoffError) = Intent().putExtra(EXTRA_REASON, error.reason)

    companion object {
        const val ACTION_IMPORT_FROM_MY_NOTES = "com.pasich.encly.action.IMPORT_FROM_MY_NOTES"
        const val EXTRA_NOTES = "notes"
        const val EXTRA_TASKS = "tasks"
        const val EXTRA_TAGS = "tags"
        const val EXTRA_SKIPPED = "skipped"
        const val EXTRA_REASON = "reason"
    }
}
