package com.pasich.encly.presentation.screen.onboarding.slides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgeAlert
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.onboarding.AnimatedButton
import com.pasich.encly.presentation.screen.onboarding.AnimatedText
import com.pasich.encly.presentation.screen.onboarding.SlideLayout
import kotlinx.coroutines.delay

@Composable
fun SeedPhraseExplanationSlide(onNext: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SlideLayout(modifier = modifier) {
        // Animated title
        AnimatedText(
            text = stringResource(R.string.seed_explain_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            delay = 0,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedText(
            text = stringResource(R.string.seed_explain_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            delay = 200,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Animated explanation cards
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedFeatureCard(
                icon = Lucide.Key,
                title = stringResource(R.string.seed_explain_security_title),
                description = stringResource(R.string.seed_explain_security_desc),
                delay = 400,
            )

            AnimatedFeatureCard(
                icon = Lucide.Key,
                title = stringResource(R.string.seed_explain_control_title),
                description = stringResource(R.string.seed_explain_control_desc),
                delay = 600,
            )

            AnimatedFeatureCard(
                icon = Lucide.Key,
                title = stringResource(R.string.seed_explain_recovery_title),
                description = stringResource(R.string.seed_explain_recovery_desc),
                delay = 800,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Warning
        AnimatedWarningCard()

        Spacer(modifier = Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                delay = 1400,
                isSecondary = true,
            )

            AnimatedButton(
                text = stringResource(R.string.seed_explain_create),
                onClick = onNext,
                delay = 1500,
                modifier = Modifier.fillMaxWidth(),
                icon = Lucide.Key,
            )
        }
    }
}

@Composable
private fun AnimatedFeatureCard(icon: ImageVector, title: String, description: String, delay: Long) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it / 2 },
            animationSpec = tween(500, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(500)),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon with a glow effect
                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "glow_alpha",
                )

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .alpha(glowAlpha)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )

                    // Icon
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedWarningCard() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(600),
        ) + fadeIn(animationSpec = tween(600)),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.BadgeAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.seed_explain_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
