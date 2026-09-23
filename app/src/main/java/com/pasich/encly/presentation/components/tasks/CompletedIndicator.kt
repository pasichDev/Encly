package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.utils.rememberDateTimeFormat
import java.util.Date

@Composable
fun CompletedIndicator(
    completedDate: Long,
    modifier: Modifier = Modifier,
    size: PriorityIndicatorSize = PriorityIndicatorSize.Small,
) {
    val style = getIndicatorStyle(size)
    val dateFormat = rememberDateTimeFormat()

    Box(
        modifier = modifier
            .clip(style.shape)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            .padding(style.padding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.task_completed),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(style.iconSize),
            )
            Text(
                text = stringResource(R.string.task_completed_at, dateFormat.format(Date(completedDate))),
                style = style.textStyle,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
