package com.pasich.encly.presentation.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Type
import com.pasich.encly.R
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel

/** Destinations the settings list links to. */
data class SettingsNavigation(val onAppearance: () -> Unit, val onSecurity: () -> Unit, val onBackup: () -> Unit)

class SettingsBuilder {

    @Composable
    fun buildSettingsCategories(
        viewModel: SettingsViewModel,
        showTasks: Boolean,
        simpleEdit: Boolean,
        navigation: SettingsNavigation,
    ): List<SettingsCategory> = buildList {
        // General Category
        add(
            SettingsCategory(
                titleRes = R.string.settings_general,
                items = buildGeneralSettings(
                    viewModel = viewModel,
                    showTasks = showTasks,
                    simpleEdit = simpleEdit,
                    onAppearance = navigation.onAppearance,
                ),
            ),
        )

        // Privacy Category
        add(
            SettingsCategory(
                titleRes = R.string.settings_privacy,
                items = buildPrivacySettings(
                    onNavigateToAuth = navigation.onSecurity,
                ),
            ),
        )

        // Backup Category
        add(
            SettingsCategory(
                titleRes = R.string.settings_backup,
                items = listOf(
                    SettingsItem.Navigation(
                        titleRes = R.string.backup_title,
                        subtitleRes = R.string.backup_settings_subtitle,
                        action = navigation.onBackup,
                        icon = Lucide.Download,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun buildGeneralSettings(
        viewModel: SettingsViewModel,
        showTasks: Boolean,
        simpleEdit: Boolean,
        onAppearance: () -> Unit,
    ): List<SettingsItem> = listOf(
        SettingsItem.Navigation(
            titleRes = R.string.settings_appearance,
            subtitleRes = R.string.appearance_settings_subtitle,
            action = onAppearance,
            icon = Lucide.Palette,
        ),
        SettingsItem.Selection(
            titleRes = R.string.settings_language,
            currentValue = stringResource(viewModel.currentLanguage().nativeName),
            action = { viewModel.setLanguageDialogVisibility(true) },
            icon = Lucide.Globe,
        ),
        SettingsItem.Switch(
            titleRes = R.string.settings_show_tasks,
            subtitleRes = R.string.settings_show_tasks_desc,
            checked = showTasks,
            icon = Lucide.ListChecks,
            onCheckedChange = { newValue ->
                viewModel.onEvent(SettingsEvent.UpdateShowTasks(newValue))
            },
        ),
        SettingsItem.Switch(
            titleRes = R.string.settings_simple_edit,
            subtitleRes = R.string.settings_simple_edit_desc,
            checked = simpleEdit,
            icon = Lucide.Type,
            onCheckedChange = { newValue ->
                viewModel.onEvent(SettingsEvent.UpdateSimpleEdit(newValue))
            },
        ),
    )

    private fun buildPrivacySettings(onNavigateToAuth: () -> Unit): List<SettingsItem> = listOf(
        SettingsItem.Navigation(
            titleRes = R.string.security_title,
            subtitleRes = R.string.security_subtitle,
            action = onNavigateToAuth,
            icon = Lucide.Shield,
        ),
    )
}
