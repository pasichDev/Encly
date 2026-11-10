package com.pasich.encly.presentation.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about
import com.pasich.encly.core.resources.appearance_settings_subtitle
import com.pasich.encly.core.resources.backup_settings_subtitle
import com.pasich.encly.core.resources.backup_title
import com.pasich.encly.core.resources.import_settings_subtitle
import com.pasich.encly.core.resources.import_title
import com.pasich.encly.core.resources.main_drawer_faq
import com.pasich.encly.core.resources.security_subtitle
import com.pasich.encly.core.resources.security_title
import com.pasich.encly.core.resources.settings_about_desc
import com.pasich.encly.core.resources.settings_appearance
import com.pasich.encly.core.resources.settings_backup
import com.pasich.encly.core.resources.settings_faq_desc
import com.pasich.encly.core.resources.settings_general
import com.pasich.encly.core.resources.settings_import
import com.pasich.encly.core.resources.settings_language
import com.pasich.encly.core.resources.settings_notes_and_tasks
import com.pasich.encly.core.resources.settings_privacy
import com.pasich.encly.core.resources.settings_show_tasks
import com.pasich.encly.core.resources.settings_show_tasks_desc
import com.pasich.encly.core.resources.settings_simple_edit
import com.pasich.encly.core.resources.settings_simple_edit_desc
import com.pasich.encly.core.resources.settings_swipe_enabled
import com.pasich.encly.core.resources.settings_swipe_enabled_desc
import com.pasich.encly.core.resources.settings_swipe_left
import com.pasich.encly.core.resources.settings_swipe_right
import com.pasich.encly.core.resources.settings_swipe_section
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.presentation.components.swipe.choiceLabel
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Destinations the settings list links to. */
data class SettingsNavigation(
    val onAppearance: () -> Unit,
    val onSecurity: () -> Unit,
    val onBackup: () -> Unit,
    val onImportNotes: () -> Unit,
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
        swipe: SwipeSettings,
        navigation: SettingsNavigation,
    ): List<SettingsCategory> = listOf(
        general(viewModel, navigation),
        notesAndTasks(viewModel, showTasks, simpleEdit),
        swipeActions(viewModel, swipe),
        single(
            Res.string.settings_privacy,
            Res.string.security_title,
            Res.string.security_subtitle,
            EnclyIcons.Shield,
        ) {
            navigation.onSecurity()
        },
        single(
            Res.string.settings_backup,
            Res.string.backup_title,
            Res.string.backup_settings_subtitle,
            EnclyIcons.Download,
        ) {
            navigation.onBackup()
        },
        single(
            Res.string.settings_import,
            Res.string.import_title,
            Res.string.import_settings_subtitle,
            EnclyIcons.File,
        ) {
            navigation.onImportNotes()
        },
        SettingsCategory(
            titleRes = Res.string.about,
            items = listOf(
                SettingsItem.Navigation(
                    titleRes = Res.string.about,
                    subtitleRes = Res.string.settings_about_desc,
                    action = navigation.onAbout,
                    icon = EnclyIcons.Info,
                ),
                SettingsItem.Navigation(
                    titleRes = Res.string.main_drawer_faq,
                    subtitleRes = Res.string.settings_faq_desc,
                    action = navigation.onFaq,
                    icon = EnclyIcons.Help,
                ),
            ),
        ),
    )

    @Composable
    private fun general(viewModel: SettingsViewModel, navigation: SettingsNavigation) = SettingsCategory(
        titleRes = Res.string.settings_general,
        items = listOf(
            SettingsItem.Navigation(
                titleRes = Res.string.settings_appearance,
                subtitleRes = Res.string.appearance_settings_subtitle,
                action = navigation.onAppearance,
                icon = EnclyIcons.Palette,
            ),
            SettingsItem.Selection(
                titleRes = Res.string.settings_language,
                currentValue = stringResource(viewModel.currentLanguage().nativeName),
                action = { viewModel.setLanguageDialogVisibility(true) },
                icon = EnclyIcons.Globe,
            ),
        ),
    )

    private fun notesAndTasks(viewModel: SettingsViewModel, showTasks: Boolean, simpleEdit: Boolean) = SettingsCategory(
        titleRes = Res.string.settings_notes_and_tasks,
        items = listOf(
            SettingsItem.Switch(
                titleRes = Res.string.settings_show_tasks,
                subtitleRes = Res.string.settings_show_tasks_desc,
                checked = showTasks,
                icon = EnclyIcons.Checklist,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateShowTasks(it)) },
            ),
            SettingsItem.Switch(
                titleRes = Res.string.settings_simple_edit,
                subtitleRes = Res.string.settings_simple_edit_desc,
                checked = simpleEdit,
                icon = EnclyIcons.Type,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateSimpleEdit(it)) },
            ),
        ),
    )

    /** The master switch for swipes on note cards and, while it is on, the action for each direction. */
    @Composable
    private fun swipeActions(viewModel: SettingsViewModel, swipe: SwipeSettings) = SettingsCategory(
        titleRes = Res.string.settings_swipe_section,
        items = buildList {
            add(
                SettingsItem.Switch(
                    titleRes = Res.string.settings_swipe_enabled,
                    subtitleRes = Res.string.settings_swipe_enabled_desc,
                    checked = swipe.enabled,
                    icon = EnclyIcons.Sliders,
                    onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateSwipeEnabled(it)) },
                ),
            )
            if (swipe.enabled) {
                add(swipeRow(viewModel, SwipeSide.RIGHT, swipe.right))
                add(swipeRow(viewModel, SwipeSide.LEFT, swipe.left))
            }
        },
    )

    @Composable
    private fun swipeRow(viewModel: SettingsViewModel, side: SwipeSide, action: SwipeAction) = SettingsItem.Selection(
        titleRes = if (side == SwipeSide.RIGHT) Res.string.settings_swipe_right else Res.string.settings_swipe_left,
        currentValue = stringResource(action.choiceLabel()),
        action = { viewModel.chooseSwipeAction(side) },
        icon = if (side == SwipeSide.RIGHT) EnclyIcons.Chevron else EnclyIcons.Back,
    )

    /** A section holding one navigation row. */
    private fun single(
        section: StringResource,
        title: StringResource,
        subtitle: StringResource,
        icon: ImageVector,
        action: () -> Unit,
    ) = SettingsCategory(
        titleRes = section,
        items = listOf(SettingsItem.Navigation(titleRes = title, subtitleRes = subtitle, action = action, icon = icon)),
    )
}
