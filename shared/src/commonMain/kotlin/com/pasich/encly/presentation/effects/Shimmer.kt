package com.pasich.encly.presentation.effects

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Extension for applying the shimmer effect to any modifier
 */
@Composable
fun Modifier.shimmerEffect() = this.then(
    Modifier.background(
        shimmerBrush(),
    ),
)
