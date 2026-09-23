package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PriorityIndicatorSize {
    Small,
    Large,
}

data class IndicatorStyle(
    val iconSize: Dp,
    val textStyle: TextStyle,
    val padding: PaddingValues,
    val shape: RoundedCornerShape,
)

@Composable
fun getIndicatorStyle(size: PriorityIndicatorSize): IndicatorStyle = when (size) {
    PriorityIndicatorSize.Small -> IndicatorStyle(
        iconSize = 12.dp,
        textStyle = MaterialTheme.typography.labelSmall,
        padding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
        shape = RoundedCornerShape(6.dp),
    )

    PriorityIndicatorSize.Large -> IndicatorStyle(
        iconSize = 16.dp,
        textStyle = MaterialTheme.typography.labelMedium,
        padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
    )
}
