package com.pasich.encly.presentation.components.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.components.tiles.getThemeTypeLabel
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.utils.DeviceCapabilities
import javax.inject.Inject

/** Destinations the settings list links to. */
data class SettingsNavigation(val onSecurity: () -> Unit, val onBackup: () -> Unit)

class SettingsBuilder @Inject constructor(private val deviceCapabilities: DeviceCapabilities) {

    @Composable
    fun buildSettingsCategories(
        viewModel: SettingsViewModel,
        themeSettings: ThemeSettings,
        showTasks: Boolean,
        simpleEdit: Boolean,
        navigation: SettingsNavigation,
    ): List<SettingsCategory> {
        val isDynamic = themeSettings.dynamic
        val themeType = themeSettings.type

        return buildList {
            // Appearance Category
            add(
                SettingsCategory(
                    titleRes = R.string.settings_appearance,
                    items = buildAppearanceSettings(
                        viewModel = viewModel,
                        isDynamic = isDynamic,
                        themeType = themeType,
                    ),
                ),
            )

            // General Category
            add(
                SettingsCategory(
                    titleRes = R.string.settings_general,
                    items = buildGeneralSettings(
                        viewModel = viewModel,
                        showTasks = showTasks,
                        simpleEdit = simpleEdit,
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
                            endIcon = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        ),
                    ),
                ),
            )
        }
    }

    @Composable
    private fun buildAppearanceSettings(
        viewModel: SettingsViewModel,
        isDynamic: Boolean,
        themeType: ThemeType,
    ): List<SettingsItem> = buildList {
        // Theme selection
        add(
            SettingsItem.Selection(
                titleRes = R.string.theme,
                currentValue = getThemeTypeLabel(themeType),
                action = { viewModel.setDialogVisibility(true) },
                endIcon = Icons.AutoMirrored.Default.KeyboardArrowRight,
            ),
        )

        // Dynamic colors (only for supported devices)
        if (viewModel.supportsDynamicColors()) {
            add(
                SettingsItem.Switch(
                    titleRes = R.string.dynamic_color,
                    checked = isDynamic,
                    onCheckedChange = { newValue ->
                        viewModel.onEvent(SettingsEvent.UpdateIsDynamicTheme(newValue))
                    },
                ),
            )
        }
    }

    @Composable
    private fun buildGeneralSettings(
        viewModel: SettingsViewModel,
        showTasks: Boolean,
        simpleEdit: Boolean,
    ): List<SettingsItem> = listOf(
        SettingsItem.Selection(
            titleRes = R.string.settings_language,
            currentValue = stringResource(viewModel.currentLanguage().nativeName),
            action = { viewModel.setLanguageDialogVisibility(true) },
            endIcon = Icons.AutoMirrored.Default.KeyboardArrowRight,
        ),
        SettingsItem.Switch(
            titleRes = R.string.settings_show_tasks,
            subtitleRes = R.string.settings_show_tasks_desc,
            checked = showTasks,
            onCheckedChange = { newValue ->
                viewModel.onEvent(SettingsEvent.UpdateShowTasks(newValue))
            },
        ),
        SettingsItem.Switch(
            titleRes = R.string.settings_simple_edit,
            subtitleRes = R.string.settings_simple_edit_desc,
            checked = simpleEdit,
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
            endIcon = Icons.AutoMirrored.Default.KeyboardArrowRight,
        ),
    )
}
