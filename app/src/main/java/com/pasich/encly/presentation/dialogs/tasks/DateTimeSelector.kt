package com.pasich.encly.presentation.dialogs.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DateTimeSelector(
    selectedDateTime: Long?,
    onDateTimeSelected: (Long?) -> Unit,
    isEnabled: Boolean = true
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    fun openPicker() {
        if (!isEnabled) return

        val calendar = Calendar.getInstance()
        if (selectedDateTime != null) {
            calendar.timeInMillis = selectedDateTime
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val selectedCalendar = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onDateTimeSelected(selectedCalendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
            )
            .clickable(enabled = isEnabled) { openPicker() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (selectedDateTime != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Дата та час",
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    ),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = dateFormat.format(Date(selectedDateTime)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    )
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Очистити дату",
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    ),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDateTimeSelected(null) }
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Встановити дату та час",
                    tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    ),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Нагадування",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.4f
                    )
                )
            }
        }
    }
}
