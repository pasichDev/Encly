package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.data.datasource.local.ThemeType
import com.pasich.encly.presentation.components.tiles.RadioItem
import com.pasich.encly.presentation.components.tiles.getThemeTypeList
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel

@Composable
fun ThemeColorDialog(model: SettingsViewModel, themeType: ThemeType) {
    var previewTheme by remember { mutableStateOf(themeType) }
    val themeList = getThemeTypeList()

    AlertDialog(
        onDismissRequest = { model.setDialogVisibility(false) },
        title = { Text(text = stringResource(R.string.theme_dialog_title)) },
        text = {
            Column {
                LazyColumn {
                    items(themeList) { item ->
                        ThemeOptionItem(
                            themeItem = item,
                            isSelected = item.value == previewTheme,
                            onSelect = {
                                previewTheme = item.value
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                model.onEvent(SettingsEvent.UpdateThemeType(previewTheme))
                model.setDialogVisibility(false)
            }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = { model.setDialogVisibility(false) }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


@Composable
private fun ThemeOptionItem(
    themeItem: RadioItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect
        )
        Text(
            text = themeItem.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

