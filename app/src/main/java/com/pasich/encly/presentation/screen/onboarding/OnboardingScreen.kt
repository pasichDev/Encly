package com.pasich.encly.presentation.screen.onboarding

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyProgressHeader
import com.pasich.encly.presentation.designsystem.LabelHeader
import com.pasich.encly.presentation.designsystem.WelcomeHeader
import com.pasich.encly.presentation.dialogs.LanguageDialog
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingPath
import com.pasich.encly.presentation.viewmodel.OnboardingStep
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.ui.theme.EnclyTheme

private const val SLIDE_MS = 500
private const val FADE_IN_MS = 300
private const val FADE_IN_DELAY_MS = 200
private const val FADE_OUT_MS = 300

/**
 * First run, in one scaffold: a header (wordmark, progress or label), the step, and a footer
 * whose primary button sits at the same place on every step. PIN and fingerprint come first,
 * then the recovery phrase; the vault is created on the way to Ready and committed by "Open my
 * notebook" ([AuthSetupViewModel.finishSetup]), which also imports a restored backup.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    setupViewModel: AuthSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finishing by setupViewModel.busy.collectAsStateWithLifecycle()
    val restoreFailed by setupViewModel.restoreFailed.collectAsStateWithLifecycle()
    val finishFailed by setupViewModel.finishFailed.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val currentOnComplete by rememberUpdatedState(onComplete)
    val pickBackupFile = rememberBackupFilePicker(viewModel::onRestoreFilePicked, viewModel::onRestorePickerUnavailable)

    // The phrase is never put in saved state; it lives only while the screen does.
    var restorePhrase by remember { mutableStateOf("") }
    var languageDialog by rememberSaveable { mutableStateOf(false) }

    val onFinished: (AuthSetupViewModel.FinishResult) -> Unit = { result ->
        // Backgrounded: set up but re-locked; MainActivity routes to the lock screen.
        if (result.ok) currentOnComplete()
    }
    val actions = onboardingActions(
        viewModel = viewModel,
        restore = RestoreActions(onPickFile = pickBackupFile, onPhraseChange = { restorePhrase = it }),
        onRestore = { viewModel.restoreBackup(restorePhrase) },
        onLanguage = { languageDialog = true },
        onOpenNotebook = { setupViewModel.finishSetup(onFinished) },
    )

    LaunchedEffect(state.biometricPending) {
        if (state.biometricPending) {
            if (activity != null) viewModel.enrollBiometric(activity) else viewModel.skipBiometric()
        }
    }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    if (languageDialog) OnboardingLanguageDialog(onDismiss = { languageDialog = false })
    // Only over the restore path's Ready step, the one place a staged backup can have failed.
    if (restoreFailed && state.step == OnboardingStep.READY && state.path == OnboardingPath.RESTORE) {
        RestoreFailedDialog(
            onRetry = { setupViewModel.retryRestore(onFinished) },
            onSkip = { setupViewModel.skipRestore(onFinished) },
        )
    }

    OnboardingScaffold(
        state = state,
        actions = actions,
        restorePhrase = restorePhrase,
        finishing = finishing,
        error = if (finishFailed) stringResource(R.string.error_database_open) else state.error?.asString(),
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScaffold(
    state: OnboardingUiState,
    actions: OnboardingActions,
    restorePhrase: String,
    finishing: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            OnboardingHeader(state = state, onBack = actions.onBack, onLanguage = actions.onLanguage)
            StepContent(
                state = state,
                actions = actions,
                restorePhrase = restorePhrase,
                modifier = Modifier.weight(1f),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
                )
            }
            OnboardingFooter(footerSpec(state, actions, restorePhrase, finishing))
        }
    }
}

@Composable
private fun OnboardingLanguageDialog(onDismiss: () -> Unit) {
    LanguageDialog(
        current = AppLocales.current(),
        onDismiss = onDismiss,
        onConfirm = { language ->
            onDismiss()
            if (language != AppLocales.current()) AppLocales.apply(language)
        },
    )
}

@Composable
private fun OnboardingHeader(state: OnboardingUiState, onBack: () -> Unit, onLanguage: () -> Unit) {
    val progress = state.progress
    when {
        state.step == OnboardingStep.WELCOME -> WelcomeHeader(
            appName = stringResource(R.string.app_name),
            language = stringResource(shownLanguage().nativeName),
            languageDescription = stringResource(R.string.language_dialog_title),
            onLanguage = onLanguage,
        )

        progress == null -> LabelHeader(label = stringResource(R.string.onboarding_restore_label), onBack = onBack)

        else -> EnclyProgressHeader(
            step = progress.step,
            totalSteps = progress.total,
            onBack = if (state.canGoBack) onBack else null,
        )
    }
}

/** The language the UI is shown in: the in-app choice, else the device's if translated, else English. */
@Composable
private fun shownLanguage(): AppLanguage {
    val chosen = AppLocales.current()
    if (chosen != AppLanguage.SYSTEM) return chosen
    val device = AppLanguage.fromTag(LocalConfiguration.current.locales[0]?.toLanguageTag())
    return if (device == AppLanguage.SYSTEM) AppLanguage.ENGLISH else device
}

@Composable
private fun StepContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    restorePhrase: String,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.step,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            slideInHorizontally(
                initialOffsetX = { if (forward) it else -it },
                animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(FADE_IN_MS, FADE_IN_DELAY_MS)) togetherWith slideOutHorizontally(
                targetOffsetX = { if (forward) -it else it },
                animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(FADE_OUT_MS))
        },
        label = "onboarding_step",
    ) { step ->
        val body = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = EnclyTheme.spacing.l)
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(body)
            OnboardingStep.PIN -> PinStep(state, actions.pin, body)
            OnboardingStep.RECOVERY_INFO -> RecoveryInfoStep(body)
            OnboardingStep.PHRASE -> PhraseStep(state, onToggleWords = actions.onToggleWords, modifier = body)
            OnboardingStep.VERIFY -> VerifyStep(state, onAnswer = actions.onAnswer, modifier = body)
            OnboardingStep.READY -> ReadyStep(state, body)
            OnboardingStep.RESTORE -> RestoreStep(state, restorePhrase, actions.restore, body)
        }
    }
}

/** The staged backup did not import; nothing is committed until the user chooses. */
@Composable
private fun RestoreFailedDialog(onRetry: () -> Unit, onSkip: () -> Unit) {
    EnclyDialog(
        title = stringResource(R.string.auth_setup_restore_failed_title),
        text = stringResource(R.string.auth_setup_restore_failed),
        onDismissRequest = {},
        confirm = DialogAction(stringResource(R.string.auth_setup_restore_retry), onRetry),
        dismiss = DialogAction(stringResource(R.string.auth_setup_restore_skip), onSkip),
    )
}

/**
 * The Storage Access Framework "open document" picker for backup files. [onUnavailable] runs
 * instead of a crash on a device with no documents app to handle it.
 */
@Composable
private fun rememberBackupFilePicker(onPick: (Uri?) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onPick)
    return {
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}
