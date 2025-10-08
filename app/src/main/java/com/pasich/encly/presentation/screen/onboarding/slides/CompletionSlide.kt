package com.pasich.encly.presentation.screen.onboarding.slides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Lamp
import com.composables.icons.lucide.Lucide
import com.pasich.encly.presentation.screen.onboarding.AnimatedButton
import com.pasich.encly.presentation.screen.onboarding.AnimatedText
import com.pasich.encly.presentation.screen.onboarding.SlideLayout
import com.pasich.encly.presentation.viewmodel.SecurityType
import kotlinx.coroutines.delay


@Composable
fun CompletionSlide(
    onComplete: () -> Unit, securityType: SecurityType? = null
) {
    SlideLayout {
        // Анімація успіху
        var showSuccess by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(300)
            showSuccess = true
        }

        AnimatedVisibility(
            visible = showSuccess, enter = scaleIn(
                animationSpec = tween(800), initialScale = 0.3f
            ) + fadeIn()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            )
                        )
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedText(
            text = when (securityType) {
                SecurityType.USER_MANAGED -> "Ваш ключ безпеки готовий!"
                else -> "Безпека налаштована!"
            }, style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold, fontSize = 28.sp
            ), delay = 600
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedText(
            text = when (securityType) {
                SecurityType.USER_MANAGED -> "Тепер ви можете безпечно створювати, редагувати та зберігати свої нотатки. " + "Не забудьте зберегти вашу сід-фразу в надійному місці!"
                else -> "Ваші дані захищені. Можете відразу почати писати, творити та реалізовувати свої ідеї!"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            delay = 800
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Мотиваційна картка
        AnimatedMotivationCard()

        Spacer(modifier = Modifier.weight(1f))

        AnimatedButton(
            text = "Почати творити!",
            onClick = onComplete,
            delay = 1400,
            icon = Lucide.ArrowRight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun AnimatedMotivationCard() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1400)
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = slideInVertically(
            initialOffsetY = { it / 2 }, animationSpec = tween(600, easing = FastOutSlowInEasing)
        ) + fadeIn()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Lucide.Lamp, // або твоя SVG-ікона
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Час реалізовувати ідеї!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                val inspirationPoints = listOf(
                    "Записуйте блискучі думки",
                    "Створюйте списки для фокусу",
                    "Плануйте дні та мрії",
                    "Зберігайте важливе під надійним захистом",
                    "Втілюйте свої ідеї в реальність"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    inspirationPoints.forEach { point ->
                        Row(
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "✨", modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Готові творити?", style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
