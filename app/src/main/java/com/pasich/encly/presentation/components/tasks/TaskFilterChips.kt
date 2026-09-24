package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.ui.theme.EnclyTheme

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
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(availableFilters, key = { it.id }) { filter ->
            EnclyChip(
                label = stringResource(filter.label),
                selected = filter.id in selectedFilterIds,
                count = filter.count.takeIf { it > 0 },
                onClick = { onFilterSelect(filter) },
            )
        }
    }
}
