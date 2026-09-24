package com.pasich.encly.presentation.components.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel

/** Destinations the settings list links to. */
data class SettingsNavigation(
    val onAppearance: () -> Unit,
    val onSecurity: () -> Unit,
    val onBackup: () -> Unit,
    val onAbout: () -> Unit,
    val onFaq: () -> Unit,
)

/**
 * The settings root (design spec §4.6): General (Appearance, Language), Notes and tasks (the
 * switches), Privacy, Backup, and About with the FAQ last.
 */
class SettingsBuilder {

    @Composable
    fun buildSettingsCategories(
        viewModel: SettingsViewModel,
        showTasks: Boolean,
        simpleEdit: Boolean,
        navigation: SettingsNavigation,
    ): List<SettingsCategory> = listOf(
        general(viewModel, navigation),
        notesAndTasks(viewModel, showTasks, simpleEdit),
        single(R.string.settings_privacy, R.string.security_title, R.string.security_subtitle, EnclyIcons.Shield) {
            navigation.onSecurity()
        },
        single(
            R.string.settings_backup,
            R.string.backup_title,
            R.string.backup_settings_subtitle,
            EnclyIcons.Download,
        ) {
            navigation.onBackup()
        },
        SettingsCategory(
            titleRes = R.string.about,
            items = listOf(
                SettingsItem.Navigation(
                    titleRes = R.string.about,
                    subtitleRes = R.string.settings_about_desc,
                    action = navigation.onAbout,
                    icon = EnclyIcons.Info,
                ),
                SettingsItem.Navigation(
                    titleRes = R.string.main_drawer_faq,
                    subtitleRes = R.string.settings_faq_desc,
                    action = navigation.onFaq,
                    icon = EnclyIcons.Help,
                ),
            ),
        ),
    )

    @Composable
    private fun general(viewModel: SettingsViewModel, navigation: SettingsNavigation) = SettingsCategory(
        titleRes = R.string.settings_general,
        items = listOf(
            SettingsItem.Navigation(
                titleRes = R.string.settings_appearance,
                subtitleRes = R.string.appearance_settings_subtitle,
                action = navigation.onAppearance,
                icon = EnclyIcons.Palette,
            ),
            SettingsItem.Selection(
                titleRes = R.string.settings_language,
                currentValue = stringResource(viewModel.currentLanguage().nativeName),
                action = { viewModel.setLanguageDialogVisibility(true) },
                icon = EnclyIcons.Globe,
            ),
        ),
    )

    private fun notesAndTasks(viewModel: SettingsViewModel, showTasks: Boolean, simpleEdit: Boolean) = SettingsCategory(
        titleRes = R.string.settings_notes_and_tasks,
        items = listOf(
            SettingsItem.Switch(
                titleRes = R.string.settings_show_tasks,
                subtitleRes = R.string.settings_show_tasks_desc,
                checked = showTasks,
                icon = EnclyIcons.Checklist,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateShowTasks(it)) },
            ),
            SettingsItem.Switch(
                titleRes = R.string.settings_simple_edit,
                subtitleRes = R.string.settings_simple_edit_desc,
                checked = simpleEdit,
                icon = EnclyIcons.Type,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateSimpleEdit(it)) },
            ),
        ),
    )

    /** A section holding one navigation row. */
    private fun single(
        @StringRes section: Int,
        @StringRes title: Int,
        @StringRes subtitle: Int,
        icon: ImageVector,
        action: () -> Unit,
    ) = SettingsCategory(
        titleRes = section,
        items = listOf(SettingsItem.Navigation(titleRes = title, subtitleRes = subtitle, action = action, icon = icon)),
    )
}
