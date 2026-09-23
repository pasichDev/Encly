package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun PriorityIndicator(
    priority: Int,
    modifier: Modifier = Modifier,
    size: PriorityIndicatorSize = PriorityIndicatorSize.Small,
) {
    val priorityData = PriorityValues.getById(priority)

    val style = getIndicatorStyle(size)

    Box(
        modifier = modifier
            .clip(style.shape)
            .background(priorityData.backgroundColor)
            .padding(style.padding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = priorityData.icon,
                contentDescription = null,
                tint = priorityData.contentColor,
                modifier = Modifier.size(style.iconSize),
            )
            Text(
                text = stringResource(priorityData.label),
                style = style.textStyle,
                color = priorityData.contentColor,
            )
        }
    }
}
