package com.pasich.encly.presentation.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclySwitchRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/** A settings section: its overline, then its rows as one grouped list with hairlines between. */
@Composable
fun SettingsCategoryRenderer(category: SettingsCategory, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(category.titleRes))
        EnclyGroup {
            category.items.forEachIndexed { index, item ->
                if (index > 0) EnclyGroupDivider()
                SettingsItemRenderer(item = item)
            }
        }
    }
}

@Composable
private fun SettingsItemRenderer(item: SettingsItem) {
    val rowModifier = Modifier.padding(horizontal = EnclyTheme.spacing.s)
    when (item) {
        is SettingsItem.Switch -> EnclySwitchRow(
            title = stringResource(item.titleRes),
            supporting = item.subtitleRes?.let { stringResource(it) },
            icon = item.icon,
            checked = item.checked,
            onCheckedChange = item.onCheckedChange,
            enabled = item.isEnabled,
            modifier = rowModifier,
        )

        is SettingsItem.Navigation -> EnclyNavigationRow(
            title = stringResource(item.titleRes),
            supporting = item.subtitleRes?.let { stringResource(it) },
            icon = item.icon,
            onClick = item.action,
            modifier = rowModifier,
        )

        is SettingsItem.Selection -> EnclyNavigationRow(
            title = stringResource(item.titleRes),
            supporting = item.currentValue,
            icon = item.icon,
            onClick = item.action,
            modifier = rowModifier,
        )
    }
}
