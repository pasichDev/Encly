package com.pasich.encly.presentation.screen.onboarding.slides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.pasich.encly.presentation.screen.onboarding.AnimatedText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityChoiceSlide(
    onCreateSeedPhrase: () -> Unit,
    onSkipSecurity: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Заголовок
        AnimatedText(
            text = "Налаштування безпеки", style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold, fontSize = 32.sp
            ), delay = 600
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedText(
            text = "Оберіть рівень безпеки для ваших даних",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            delay = 800
        )

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedText(
            text = "Сід-фраза (seed phrase) — це набір слів, який дозволяє відновити доступ до ваших даних. " + "Якщо ви оберете автоматичну безпеку, система буде використовувати вбудований ключ, " + "що спрощує використання, але знижує рівень захисту та не дозволяє створення локальних зашифрованих копій.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Start,
            delay = 1000
        )

        Spacer(modifier = Modifier.height(30.dp))

        SecurityOptionCard(
            title = "Створити ключ безпеки",
            descriptionLines = listOf(
                "Максимальний рівень безпеки",
                "Ви контролюєте свій ключ",
                "Потрібно зберегти сід-фразу",
                "Рекомендовано для важливих даних"
            ),
            icon = Lucide.Key,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onCreateSeedPhrase,
            delay = 1300
        )

        Spacer(modifier = Modifier.height(24.dp))

        SecurityOptionCard(
            title = "Автоматична безпека",
            descriptionLines = listOf(
                "Простий старт без налаштувань",
                "Система автоматично керує безпекою",
                "Не потрібно запам'ятовувати ключі",
                "Заборонено створювати зашифровані локальні копії"
            ),
            icon = Lucide.Settings,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onSkipSecurity,
            delay = 1600
        )

        Spacer(modifier = Modifier.height(12.dp))

    }
}

@Composable
fun SecurityOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    descriptionLines: List<String>,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    delay: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInHorizontally(
            initialOffsetX = { -it / 2 }, animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-20).dp)
                        .alpha(0.03f),
                    tint = iconTint
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = iconTint
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start
                    ) {
                        descriptionLines.forEach {
                            Text(
                                text = "• $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

    }
}

