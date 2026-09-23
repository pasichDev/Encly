package com.pasich.encly.presentation.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.custombox.SettingBox

@Composable
fun SettingsCategoryRenderer(category: SettingsCategory, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // Category title
        Text(
            text = stringResource(id = category.titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Category items
        category.items.forEachIndexed { index, item ->
            val roundPosition = when {
                category.items.size == 1 -> RoundPosition.Full
                index == 0 -> RoundPosition.First
                index == category.items.lastIndex -> RoundPosition.Last
                else -> RoundPosition.Medium
            }

            SettingsItemRenderer(
                item = item,
                roundPosition = roundPosition,
            )
        }
    }
}

@Composable
private fun SettingsItemRenderer(item: SettingsItem, roundPosition: RoundPosition) {
    when (item) {
        is SettingsItem.Switch -> {
            SettingBox(
                title = stringResource(id = item.titleRes),
                subTitle = item.subtitleRes?.let { stringResource(id = it) } ?: "",
                roundPosition = roundPosition,
                endWidget = {
                    Switch(
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange,
                        enabled = item.isEnabled,
                    )
                },
            )
        }

        is SettingsItem.Navigation -> {
            SettingBox(
                title = stringResource(id = item.titleRes),
                subTitle = item.subtitleRes?.let { stringResource(id = it) } ?: "",
                roundPosition = roundPosition,
                action = item.action,
                endWidget = item.endIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                        )
                    }
                },
            )
        }

        is SettingsItem.Selection -> {
            SettingBox(
                title = stringResource(id = item.titleRes),
                subTitle = item.currentValue,
                roundPosition = roundPosition,
                action = item.action,
                endWidget = item.endIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    }
}
