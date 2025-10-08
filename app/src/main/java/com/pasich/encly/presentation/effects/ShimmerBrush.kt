package com.pasich.encly.presentation.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Создает кисть с эффектом мерцания для скелетона загрузки
 */
@Composable
fun ShimmerBrush(
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000
): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 1.0f),
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = (durationMillis + widthOfShadowBrush).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_animation",
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - widthOfShadowBrush, y = 0.0f),
        end = Offset(x = translateAnimation.value, y = angleOfAxisY),
    )
}
