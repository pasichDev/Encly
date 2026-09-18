package com.pasich.encly.presentation.screen.onboarding.slides

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.presentation.screen.onboarding.AnimatedButton
import com.pasich.encly.presentation.screen.onboarding.SlideLayout
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import kotlin.math.ceil

data class SeedPhraseActions(
    val onToggleVisibility: () -> Unit,
    val onStartVerification: () -> Unit = {},
    val onUpdateAnswer: (Int, String) -> Unit = { _, _ -> },
    val onCompleteVerification: () -> Unit = {},
    val onCancelVerification: () -> Unit = {}
)

@Composable
fun SeedPhraseDisplaySlide(
    uiState: OnboardingViewModel.OnboardingUiState,
    actions: SeedPhraseActions
) {
    SlideLayout {
        when {
            // Show a loading indicator while the key is being created
            uiState.isLoading -> {
                LoadingContent()
            }

            // If the key has been created, show it or the verification step
            uiState.phase.isNotEmpty() && !uiState.isLoading -> {
                if (uiState.isVerificationMode) {
                    SeedPhraseVerificationContent(
                        uiState = uiState,
                        onUpdateAnswer = actions.onUpdateAnswer,
                        onCompleteVerification = actions.onCompleteVerification,
                        onCancel = actions.onCancelVerification
                    )
                } else {
                    SeedPhraseDisplayContent(
                        uiState = uiState,
                        onToggleVisibility = actions.onToggleVisibility,
                        onNext = actions.onStartVerification
                    )
                }
            }

            else -> return@SlideLayout
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "generation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ), label = "rotation"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation), contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp), strokeWidth = 6.dp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Генеруємо ваш ключ безпеки...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Це може зайняти кілька секунд",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SeedPhraseDisplayContent(
    uiState: OnboardingViewModel.OnboardingUiState,
    onToggleVisibility: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉 Ключ безпеки створено!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))


        SeedRecoveryWarning()

        Spacer(modifier = Modifier.height(24.dp))

        SeedPhraseCard(
            uiState = uiState,
            onToggleVisibility = onToggleVisibility
        )

        Spacer(modifier = Modifier.height(35.dp))


        AnimatedButton(
            onClick = onNext, modifier = Modifier.fillMaxWidth(), text = "Продовжити до перевірки"
        )

    }
}

@Composable
private fun SeedRecoveryWarning() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ВАЖЛИВО!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Запишіть recovery seed офлайн. Без нього recovery-slot не відновить доступ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SeedPhraseCard(
    uiState: OnboardingViewModel.OnboardingUiState,
    onToggleVisibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (uiState.isKeyVisible) {
                SeedPhraseGrid(seedPhrase = uiState.phase)
            } else {
                HiddenSeedPhraseGrid()
            }
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedButton(
                onClick = onToggleVisibility,
                modifier = Modifier.fillMaxWidth(),
                text = if (uiState.isKeyVisible) "Приховати Seed" else "Показати Seed",
                isSecondary = true,
                smallStyle = true
            )
        }
    }
}

@Composable
private fun SeedPhraseGrid(seedPhrase: String) {
    val words = seedPhrase.split(" ").filter { it.isNotBlank() }
    val rowCount = ceil(words.size / 3.0).toInt()
    val gridHeight = (rowCount * 56 + (rowCount - 1) * 8).dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(words) { index, word ->
            SeedWordCard(
                number = index + 1, word = word
            )
        }
    }
}

@Composable
private fun HiddenSeedPhraseGrid() {
    // For 12 words in 3 columns = 4 rows
    val gridHeight = (4 * 56 + 3 * 8).dp // 4 rows of 56dp + 3 gaps of 8dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(12) { index ->
            SeedWordCard(
                number = index + 1, word = "••••••", isHidden = true
            )
        }
    }
}

@Composable
private fun SeedWordCard(
    number: Int, word: String, isHidden: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ), colors = CardDefaults.cardColors(
            containerColor = if (isHidden) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = word,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (isHidden) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SeedPhraseVerificationContent(
    uiState: OnboardingViewModel.OnboardingUiState,
    onUpdateAnswer: (Int, String) -> Unit,
    onCompleteVerification: () -> Unit,
    onCancel: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // If all words are entered correctly, hide the keyboard
    LaunchedEffect(uiState.isVerificationComplete) {
        if (uiState.isVerificationComplete) {
            keyboardController?.hide()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔍 Підтвердіть ваш ключ",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Введіть слова з вашого ключа безпеки для підтвердження:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Word input fields
        val lastIndex = uiState.verificationWords.lastIndex
        uiState.verificationWords.forEachIndexed { index, (wordIndex, correctWord) ->
            VerificationWordInput(
                wordNumber = wordIndex + 1,
                userAnswer = uiState.userAnswers[wordIndex] ?: "",
                correctWord = correctWord,
                onAnswerChange = { answer -> onUpdateAnswer(wordIndex, answer) },
                imeAction = when (index) {
                    lastIndex -> androidx.compose.ui.text.input.ImeAction.Done
                    else -> androidx.compose.ui.text.input.ImeAction.Next
                },
                onImeAction = {
                    if (index == lastIndex) {
                        onCompleteVerification()
                    }
                })

            if (index < lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Confirmation button
        Button(
            onClick = onCompleteVerification,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isVerificationComplete
        ) {
            Text("Підтвердити створення ключа")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back button
        OutlinedButton(
            onClick = onCancel, modifier = Modifier.fillMaxWidth()
        ) {
            Text("Повернутися назад")
        }
    }
}

@Composable
private fun VerificationWordInput(
    wordNumber: Int,
    userAnswer: String,
    correctWord: String,
    onAnswerChange: (String) -> Unit,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    // Check whether the answer is correct
    val isCorrect =
        userAnswer.trim().equals(correctWord.trim(), ignoreCase = true) && userAnswer.isNotBlank()

    // Border color animation
    val borderColor by animateColorAsState(
        targetValue = if (isCorrect) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "border_color"
    )

    Column {
        Text(
            text = "Слово #$wordNumber:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = userAnswer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Введіть слово...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = if (isCorrect) borderColor else MaterialTheme.colorScheme.outline
            ),
            trailingIcon = if (isCorrect) {
                {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Правильно",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else null,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onAny = { onImeAction() })
        )
    }
}
