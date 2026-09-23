package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ChevronsUp
import com.composables.icons.lucide.Lucide
import com.pasich.encly.presentation.components.BadgeCount
import com.pasich.encly.presentation.viewmodel.TaskFilter

/** [selectedFilterIds]: ids of the selected chips; filter ids are unique across filter types. */
@Composable
fun TaskFilterChips(
    availableFilters: List<TaskFilter>,
    selectedFilterIds: Set<String>,
    onFilterSelect: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(availableFilters) { filter ->
            val isSelected = filter.id in selectedFilterIds

            FilterChip(
                onClick = { onFilterSelect(filter) },
                label = { FilterChipLabel(filter, isSelected) },
                selected = isSelected,
                leadingIcon = filterLeadingIcon(filter),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selectedContainerColor(filter),
                    selectedLabelColor = selectedLabelColor(filter),
                ),
            )
        }
    }
}

@Composable
private fun FilterChipLabel(filter: TaskFilter, isSelected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(filter.label),
            style = MaterialTheme.typography.labelMedium,
        )
        if (filter.count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            BadgeCount(
                count = filter.count,
                backgroundColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@Composable
private fun selectedContainerColor(filter: TaskFilter): Color = when (filter.id) {
    "priority_high" -> MaterialTheme.colorScheme.errorContainer
    "priority_medium" -> MaterialTheme.colorScheme.tertiaryContainer
    "priority_low" -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun selectedLabelColor(filter: TaskFilter): Color = when (filter.id) {
    "priority_high" -> MaterialTheme.colorScheme.onErrorContainer
    "priority_medium" -> MaterialTheme.colorScheme.onTertiaryContainer
    "priority_low" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
fun filterLeadingIcon(filter: TaskFilter): (@Composable () -> Unit)? {
    if (filter.type != TaskFilter.Type.PRIORITY) return null

    val icon = when (filter.id) {
        "priority_high" -> Lucide.ChevronsUp
        "priority_medium" -> Lucide.ChevronUp
        "priority_low" -> Lucide.ChevronDown
        else -> Lucide.ChevronDown
    }

    val tint = when (filter.id) {
        "priority_high" -> MaterialTheme.colorScheme.onErrorContainer
        "priority_medium" -> MaterialTheme.colorScheme.onTertiaryContainer
        "priority_low" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    return {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}
