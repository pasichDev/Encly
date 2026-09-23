package com.pasich.encly.presentation.screen.onboarding.slides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tag
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.onboarding.AnimatedButton
import com.pasich.encly.presentation.screen.onboarding.AnimatedText
import com.pasich.encly.presentation.screen.onboarding.SlideLayout
import kotlinx.coroutines.delay

@Composable
fun WelcomeSlide(onNext: () -> Unit, modifier: Modifier = Modifier) {
    SlideLayout(modifier = modifier) {
        var iconVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(300)
            iconVisible = true
        }

        AnimatedVisibility(
            visible = iconVisible,
            enter = scaleIn(
                animationSpec = tween(600),
                initialScale = 0.3f,
            ) + fadeIn(),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splashscreen),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Animated title
        AnimatedText(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
            delay = 600,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedText(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            delay = 800,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Features - compact list
        val features = listOf(
            Triple(
                Icons.Default.Edit,
                stringResource(R.string.onboarding_feature_editor_title),
                stringResource(R.string.onboarding_feature_editor_desc),
            ),
            Triple(
                Lucide.Tag,
                stringResource(R.string.onboarding_feature_tags_title),
                stringResource(R.string.onboarding_feature_tags_desc),
            ),
            Triple(
                Lucide.LayoutDashboard,
                stringResource(R.string.onboarding_feature_interface_title),
                stringResource(R.string.onboarding_feature_interface_desc),
            ),
            Triple(
                Lucide.Check,
                stringResource(R.string.onboarding_feature_tasks_title),
                stringResource(R.string.onboarding_feature_tasks_desc),
            ),
            Triple(
                Lucide.Lock,
                stringResource(R.string.onboarding_feature_privacy_title),
                stringResource(R.string.onboarding_feature_privacy_desc),
            ),
        )

        features.forEachIndexed { index, (icon, title, description) ->
            AnimatedCompactFeatureCard(
                icon = icon,
                title = title,
                description = description,
                delay = 1000 + index * 100,
            )
            if (index < features.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedButton(
            text = stringResource(R.string.action_continue),
            onClick = onNext,
            delay = 1800,
            modifier = Modifier.fillMaxWidth(),
            icon = Lucide.ArrowRight,
        )
    }
}

@Composable
private fun AnimatedCompactFeatureCard(icon: ImageVector, title: String, description: String, delay: Int) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -it / 2 },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(300)),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
