package com.pasich.encly.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    thickness: Dp = 1.dp,
    height: Dp = Dp.Unspecified,
) {
    Box(
        modifier = modifier
            .width(thickness)
            .then(
                if (height != Dp.Unspecified) Modifier.height(height) else Modifier,
            )
            .background(color),
    )
}
