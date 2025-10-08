package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.utils.rememberSeedPhraseActions
import com.pasich.encly.presentation.dialogs.InfoSnackbar
import com.pasich.encly.presentation.dialogs.SnackType
import com.pasich.encly.presentation.screen.onboarding.slides.CompletionSlide
import com.pasich.encly.presentation.screen.onboarding.slides.SecurityChoiceSlide
import com.pasich.encly.presentation.screen.onboarding.slides.SeedPhraseDisplaySlide
import com.pasich.encly.presentation.screen.onboarding.slides.WelcomeSlide
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.SecurityType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var snackType = SnackType.SUCCESS

    // Якщо онбординг вже завершено
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onComplete()
        }
    }

    val seedPhraseActions = rememberSeedPhraseActions(context = context, onFileSaveSuccess = {
        coroutineScope.launch {
            snackType = SnackType.SUCCESS
            snackbarHostState.showSnackbar("Ключ успішно збережено у файл!")
        }
    }, onFileSaveError = { error ->
        coroutineScope.launch {
            snackType = SnackType.ERROR
            snackbarHostState.showSnackbar("Помилка збереження файлу: ${error.message}")
        }
    }, onGoogleDriveSuccess = {
        coroutineScope.launch {
            snackType = SnackType.SUCCESS
            snackbarHostState.showSnackbar("Ключ успішно збережено на Google Drive!")
        }
    }, onGoogleDriveError = { error ->
        coroutineScope.launch {
            snackType = SnackType.ERROR
            snackbarHostState.showSnackbar("Помилка Google Drive: ${error.message}")
        }
    }, onCopySuccess = {
        return@rememberSeedPhraseActions
    })

    val infiniteTransition = rememberInfiniteTransition()
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        )
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    ), start = Offset(offset, offset), end = Offset(offset + 500f, offset + 800f)
                )
            ), snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState, snackbar = { snackbarData ->
                    InfoSnackbar(snackbarData, snackType)
                })
        }) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Прогрес-бар з анімацією
            val totalPages = when {
                uiState.isComplete -> 1
                uiState.securityType == SecurityType.USER_MANAGED -> 4 // welcome + security + seed phrase + completion
                uiState.securityType != null -> 3 // welcome + security + completion (для AUTO)
                else -> 2 // welcome + security choice (поки не вибрано)
            }
            AnimatedProgressBar(
                currentPage = currentPage,
                totalPages = totalPages,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // Контент слайдів
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { if (targetState > initialState) it else -it },
                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300, 200)) togetherWith slideOutHorizontally(
                        targetOffsetX = { if (targetState > initialState) -it else it },
                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(300))
                },
                label = "onboarding_pages"
            ) { page ->
                when {
                    // Якщо система вже завершена, нічого не показуємо
                    uiState.isComplete -> {}

                    // Перша сторінка - Welcome слайд
                    page == 0 -> {
                        WelcomeSlide(
                            onNext = { viewModel.nextPage() })
                    }

                    // Друга сторінка - вибір системи безпеки
                    page == 1 -> {
                        SecurityChoiceSlide(
                            onCreateSeedPhrase = { viewModel.navigateToSeedPhraseCreation() },
                            onSkipSecurity = {
                                viewModel.skipSecuritySetup()
                                // Переходимо до завершальної сторінки для AUTO режиму
                                viewModel.nextPage()
                            },
                        )
                    }

                    // Третя сторінка - показ сід-фрази (тільки для user-managed)
                    page == 2 && uiState.securityType == SecurityType.USER_MANAGED -> {
                        SeedPhraseDisplaySlide(
                            uiState = uiState,
                            onToggleVisibility = { viewModel.toggleKeyVisibility() },
                            onCopyKey = {
                                seedPhraseActions.copyToClipboard(uiState.phase)
                            },
                            onSaveToFile = {
                                seedPhraseActions.saveToFile(uiState.phase)
                            },
                            onSaveToGoogleDrive = {
                                seedPhraseActions.saveToGoogleDrive(uiState.phase)
                            },
                            onStartVerification = { viewModel.startSeedPhraseVerification() },
                            onUpdateAnswer = { wordIndex, answer ->
                                viewModel.updateUserAnswer(
                                    wordIndex, answer
                                )
                            },
                            onCompleteVerification = { viewModel.completeVerification() },
                            onCancelVerification = { viewModel.cancelVerification() })
                    }

                    // CompletionSlide для AUTO режиму (сторінка 2) або для USER_MANAGED (сторінка 3)
                    page == 2 && uiState.securityType == SecurityType.AUTO_MANAGED -> {
                        CompletionSlide(
                            onComplete = {
                                viewModel.completeOnboarding()
                                onComplete()
                            }, securityType = uiState.securityType
                        )
                    }

                    // CompletionSlide для USER_MANAGED після seed phrase
                    page == 3 && uiState.securityType == SecurityType.USER_MANAGED -> {
                        CompletionSlide(
                            onComplete = {
                                viewModel.completeOnboarding()
                                onComplete()
                            }, securityType = uiState.securityType
                        )
                    }
                }
            }

            // Показ помилок
            AnimatedVisibility(
                visible = uiState.error != null, enter = slideInVertically(
                    initialOffsetY = { it }, animationSpec = tween(300)
                ) + fadeIn(), exit = slideOutVertically(
                    targetOffsetY = { it }, animationSpec = tween(300)
                ) + fadeOut(), modifier = Modifier.padding(16.dp)
            ) {
                ErrorCard(
                    error = uiState.error ?: "", onDismiss = { viewModel.clearError() })
            }
        }

        // Декоративні елементи
        DecorativeElements()
    }

}


@Composable
private fun AnimatedProgressBar(
    currentPage: Int, totalPages: Int, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalPages) { index ->
            val isActive = index <= currentPage
            val animatedWidth by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.3f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "progress_width"
            )

            Box(
                modifier = Modifier
                    .weight(animatedWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: String, onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.fingerprint_dialog_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрити",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DecorativeElements() {
    // Декоративні елементи у фоні
    val infiniteTransition = rememberInfiniteTransition(label = "decorative")

    // Плаваючі кружечки
    repeat(3) { index ->
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 30f, animationSpec = infiniteRepeatable(
                animation = tween((3000 + index * 500), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "float_$index"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.1f, targetValue = 0.3f, animationSpec = infiniteRepeatable(
                animation = tween((2000 + index * 300), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha_$index"
        )

        Box(
            modifier = Modifier
                .offset(
                    x = (50 + index * 120).dp, y = (100 + index * 150 + offsetY).dp
                )
                .size((40 + index * 20).dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        )
    }
}

