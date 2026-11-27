package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.core.common.asString
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auth_setup_restore_failed
import com.pasich.encly.core.resources.auth_setup_restore_failed_title
import com.pasich.encly.core.resources.auth_setup_restore_retry
import com.pasich.encly.core.resources.auth_setup_restore_skip
import com.pasich.encly.core.resources.error_database_open
import com.pasich.encly.core.resources.language_dialog_title
import com.pasich.encly.core.resources.onboarding_restore_label
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.platform.LocalAppLocales
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.platform.LocalDocumentPickers
import com.pasich.encly.platform.PickerUnavailableException
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyProgressHeader
import com.pasich.encly.presentation.designsystem.LabelHeader
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.designsystem.WelcomeHeader
import com.pasich.encly.presentation.designsystem.authCardApplies
import com.pasich.encly.presentation.designsystem.authSurfaceColor
import com.pasich.encly.presentation.dialogs.LanguageDialog
import com.pasich.encly.presentation.effects.StepFade
import com.pasich.encly.presentation.effects.screenFade
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingPath
import com.pasich.encly.presentation.viewmodel.OnboardingStep
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel.OnboardingUiState
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.currentLanguageTag
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

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
    viewModel: OnboardingViewModel = metroViewModel(),
    setupViewModel: AuthSetupViewModel = metroViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finishing by setupViewModel.busy.collectAsStateWithLifecycle()
    val restoreFailed by setupViewModel.restoreFailed.collectAsStateWithLifecycle()
    val finishFailed by setupViewModel.finishFailed.collectAsStateWithLifecycle()
    val host = LocalBiometricHost.current
    val currentOnComplete by rememberUpdatedState(onComplete)
    val pickBackupFile = rememberBackupFilePicker(viewModel::onRestoreFilePicked, viewModel::onRestorePickerUnavailable)

    // The phrase is never put in saved state; it lives only while the screen does.
    val restorePhrase = remember { RecoveryPhraseState() }
    // Likewise the passphrase, where that is the secret: typed here, taken out of the field when it is sent.
    val passphrase = rememberPassphraseInput(viewModel.secretMaxUnits)
    var languageDialog by rememberSaveable { mutableStateOf(false) }

    val onFinished: (AuthSetupViewModel.FinishResult) -> Unit = { result ->
        // Backgrounded: set up but re-locked; EnclyApp routes to the lock screen.
        if (result.ok) currentOnComplete()
    }
    val onRestore = {
        if (state.restoreFileReady && restorePhrase.canSubmit) viewModel.restoreBackup(restorePhrase.toCharArray())
    }
    val actions = onboardingActions(
        viewModel = viewModel,
        restore = RestoreActions(
            onPickFile = pickBackupFile,
            onPhraseEdited = viewModel::onRestorePhraseEdited,
            onSubmit = onRestore,
        ),
        onRestore = onRestore,
        onLanguage = { languageDialog = true },
        onOpenNotebook = { setupViewModel.finishSetup(onFinished) },
        onRetryBiometric = { host?.let(viewModel::retryBiometric) },
        onSubmitPassphrase = { passphrase.sendTo(viewModel) },
        onSuggestPassphrase = { passphrase.suggestFrom(viewModel) },
    )

    LaunchedEffect(state.biometricPending) {
        if (state.biometricPending) {
            if (host != null) viewModel.enrollBiometric(host) else viewModel.skipBiometric()
        }
    }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }
    // Off the restore path (and once it reached Ready) the typed words are dropped.
    LaunchedEffect(state.step) {
        if (state.step != OnboardingStep.RESTORE && state.step != OnboardingStep.PIN) restorePhrase.clear()
        if (state.step != OnboardingStep.PIN) passphrase.clear()
    }

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
        fields = OnboardingFields(restorePhrase, passphrase),
        finishing = finishing,
        error = if (finishFailed) stringResource(Res.string.error_database_open) else state.error?.asString(),
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScaffold(
    state: OnboardingUiState,
    actions: OnboardingActions,
    fields: OnboardingFields,
    finishing: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    // On a desktop window the steps stand in a card as high as the step: the button follows the form closely.
    val card = authCardApplies()
    Surface(
        color = authSurfaceColor(card),
        modifier = if (card) modifier.fillMaxWidth() else modifier.fillMaxSize(),
    ) {
        Column(
            modifier = (if (card) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            OnboardingHeader(state = state, onBack = actions.onBack, onLanguage = actions.onLanguage)
            StepContent(
                state = state,
                actions = actions,
                restorePhrase = fields.restorePhrase,
                passphrase = fields.passphrase,
                card = card,
                modifier = if (card) Modifier.weight(1f, fill = false) else Modifier.weight(1f),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
                )
            }
            OnboardingFooter(
                spec = footerSpec(state, actions, fields.restorePhrase, finishing, fields.passphrase),
                inCard = card,
            )
        }
    }
}

@Composable
private fun OnboardingLanguageDialog(onDismiss: () -> Unit) {
    val locales = LocalAppLocales.current
    LanguageDialog(
        current = locales.current(),
        onDismiss = onDismiss,
        onConfirm = { language ->
            onDismiss()
            if (language != locales.current()) locales.apply(language)
        },
    )
}

@Composable
private fun OnboardingHeader(state: OnboardingUiState, onBack: () -> Unit, onLanguage: () -> Unit) {
    val progress = state.progress
    when {
        state.step == OnboardingStep.WELCOME -> WelcomeHeader(
            appName = LocalAppInfo.current.appName,
            language = stringResource(shownLanguage().nativeName),
            languageDescription = stringResource(Res.string.language_dialog_title),
            onLanguage = onLanguage,
        )

        progress == null -> LabelHeader(
            label = secretString(state.secretKind, Res.string.onboarding_restore_label),
            onBack = onBack,
        )

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
    val chosen = LocalAppLocales.current.current()
    if (chosen != AppLanguage.SYSTEM) return chosen
    val device = AppLanguage.fromTag(currentLanguageTag())
    return if (device == AppLanguage.SYSTEM) AppLanguage.ENGLISH else device
}

@Composable
private fun StepContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    restorePhrase: RecoveryPhraseState,
    passphrase: PassphraseInput,
    card: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.step,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val direction = if (forward) SlideDirection.Start else SlideDirection.End
            slideIntoContainer(direction, EnclyMotion.long()) togetherWith
                slideOutOfContainer(direction, EnclyMotion.long())
        },
        label = "onboarding_step",
    ) { step ->
        // The fade is drawn per draw call, not as fadeIn/fadeOut would: those put the step in an offscreen layer.
        val body = Modifier
            .screenFade(this, enter = { StepFade.In }, exit = { StepFade.Out })
            .then(if (card) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (card) EnclyTheme.spacing.xs else EnclyTheme.spacing.l)
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(body)

            OnboardingStep.PIN -> if (state.secretKind == SecretKind.PASSWORD) {
                PassphraseSetupStep(state, passphrase, actions.pin, body)
            } else {
                PinStep(state, actions.pin, body)
            }

            OnboardingStep.RECOVERY_INFO -> RecoveryInfoStep(state.secretKind, body)

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
        title = stringResource(Res.string.auth_setup_restore_failed_title),
        text = stringResource(Res.string.auth_setup_restore_failed),
        onDismissRequest = {},
        confirm = DialogAction(stringResource(Res.string.auth_setup_restore_retry), onRetry),
        dismiss = DialogAction(stringResource(Res.string.auth_setup_restore_skip), onSkip),
    )
}

/**
 * The Storage Access Framework "open document" picker for backup files. [onUnavailable] runs
 * instead of a crash on a device with no documents app to handle it.
 */
@Composable
private fun rememberBackupFilePicker(onPick: (DocumentRef?) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val launcher = LocalDocumentPickers.current.rememberOpenDocument(onPick)
    return {
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: PickerUnavailableException) {
            onUnavailable()
        }
    }
}
