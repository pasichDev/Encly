package com.pasich.encly.presentation.components.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

data class SettingsCategory(val titleRes: StringResource, val items: List<SettingsItem>)

sealed class SettingsItem(
    open val titleRes: StringResource,
    open val subtitleRes: StringResource? = null,
    open val isEnabled: Boolean = true,
    open val requiresValidation: Boolean = false,
    /** Leading icon of the row, in `primary`. */
    open val icon: ImageVector? = null,
) {
    data class Switch(
        override val titleRes: StringResource,
        override val subtitleRes: StringResource? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        override val isEnabled: Boolean = true,
        override val requiresValidation: Boolean = false,
        val validationMessage: String? = null,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, requiresValidation, icon)

    data class Navigation(
        override val titleRes: StringResource,
        override val subtitleRes: StringResource? = null,
        val action: () -> Unit,
        override val isEnabled: Boolean = true,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, icon = icon)

    data class Selection(
        override val titleRes: StringResource,
        override val subtitleRes: StringResource? = null,
        val currentValue: String,
        val action: () -> Unit,
        override val isEnabled: Boolean = true,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, icon = icon)
}
