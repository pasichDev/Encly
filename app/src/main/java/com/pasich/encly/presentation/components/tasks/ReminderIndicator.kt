package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ReminderIndicator(
    reminderDate: Long,
    modifier: Modifier = Modifier,
    size: PriorityIndicatorSize = PriorityIndicatorSize.Small
) {
    val style = getIndicatorStyle(size)

    val dateFormat =
        remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()) }

    Box(
        modifier = modifier
            .clip(style.shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(style.padding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                contentDescription = "Нагадування",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(style.iconSize)
            )
            Text(
                text = dateFormat.format(java.util.Date(reminderDate)),
                style = style.textStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
