package com.pasich.encly.presentation.dialogs.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pasich.encly.data.model.PriorityValues

@Composable
fun PrioritySelectionDialog(
    onDismissRequest: () -> Unit,
    onPrioritySelected: (Int) -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Виберіть пріоритет")
        },
        text = {
            Column {
                PriorityValues.priorities.forEach { priorityData ->
                    // val (bgColor, contentColor, icon, label) = quad
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(priorityData.backgroundColor)
                            .clickable {
                                onPrioritySelected(priorityData.id)
                                onDismissRequest()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = priorityData.icon,
                            contentDescription = priorityData.label,
                            tint = priorityData.contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = priorityData.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = priorityData.contentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Закрити")
            }
        }
    )
}
