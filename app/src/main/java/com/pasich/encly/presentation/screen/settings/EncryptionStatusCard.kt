package com.pasich.encly.presentation.screen.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme

private const val PULSE_MS = 1_800

/** The halo grows from the badge's edge to this share of the tile, fading out as it goes. */
private const val HALO_REACH = 1f
private const val HALO_START = 0.62f
private const val HALO_ALPHA = 0.35f

/**
 * The security page's headline status, "Encryption active": a `primaryContainer` card with the
 * shield on a `primary` badge and a slow halo pulsing out of it, so the state reads at a glance.
 */
@Composable
internal fun EncryptionStatusCard(title: String, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m),
            modifier = Modifier.padding(EnclyTheme.spacing.m),
        ) {
            PulsingShield()
            Column(
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = TEXT_ALPHA),
                )
            }
        }
    }
}

private const val TEXT_ALPHA = 0.85f

@Composable
private fun PulsingShield() {
    val colors = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Restart),
        label = "halo",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EnclyTheme.spacing.tile)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2 * HALO_REACH
            val radius = maxRadius * (HALO_START + (1 - HALO_START) * pulse)
            drawCircle(color = colors.primary, radius = radius, alpha = HALO_ALPHA * (1 - pulse))
        }
        Surface(
            shape = CircleShape,
            color = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.size(EnclyTheme.spacing.tileSmall),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(EnclyIcons.Shield, contentDescription = null, modifier = Modifier.size(EnclyTheme.spacing.icon))
            }
        }
    }
}
