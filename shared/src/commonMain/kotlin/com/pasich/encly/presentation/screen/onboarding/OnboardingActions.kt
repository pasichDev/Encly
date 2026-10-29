package com.pasich.encly.presentation.screen.onboarding

import com.pasich.encly.presentation.viewmodel.OnboardingViewModel

/** Every user action of the onboarding screens, so the steps never see the ViewModel. */
internal interface OnboardingActions {
    val onBack: () -> Unit
    val onLanguage: () -> Unit
    val onGetStarted: () -> Unit
    val onStartRestore: () -> Unit
    val pin: PinActions
    val onCreatePhrase: () -> Unit
    val onSkipPhrase: () -> Unit
    val onToggleWords: () -> Unit
    val onWroteWords: () -> Unit
    val onAnswer: (Int, String) -> Unit
    val onConfirmWords: () -> Unit
    val onShowWordsAgain: () -> Unit
    val restore: RestoreActions
    val onRestore: () -> Unit
    val onOpenNotebook: () -> Unit
}

/** The actions of [viewModel], plus those the screen itself handles. */
internal fun onboardingActions(
    viewModel: OnboardingViewModel,
    restore: RestoreActions,
    onRestore: () -> Unit,
    onLanguage: () -> Unit,
    onOpenNotebook: () -> Unit,
): OnboardingActions = object : OnboardingActions {
    override val onBack = viewModel::back
    override val onLanguage = onLanguage
    override val onGetStarted = viewModel::getStarted
    override val onStartRestore = viewModel::startRestore
    override val pin = PinActions(
        onDigit = viewModel::onPinDigit,
        onBackspace = viewModel::onPinBackspace,
        onBiometricChange = viewModel::setBiometricRequested,
        onSubmit = viewModel::submitPin,
    )
    override val onCreatePhrase = viewModel::createPhrase
    override val onSkipPhrase = viewModel::skipPhrase
    override val onToggleWords = viewModel::toggleWordsHidden
    override val onWroteWords = viewModel::startSeedPhraseVerification
    override val onAnswer = viewModel::updateUserAnswer
    override val onConfirmWords = viewModel::completeVerification
    override val onShowWordsAgain = viewModel::cancelVerification
    override val restore = restore
    override val onRestore = onRestore
    override val onOpenNotebook = onOpenNotebook
}
