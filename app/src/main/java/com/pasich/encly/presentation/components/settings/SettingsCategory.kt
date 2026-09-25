package com.pasich.encly.presentation.components.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsCategory(@param:StringRes val titleRes: Int, val items: List<SettingsItem>)

sealed class SettingsItem(
    @param:StringRes open val titleRes: Int,
    @param:StringRes open val subtitleRes: Int? = null,
    open val isEnabled: Boolean = true,
    open val requiresValidation: Boolean = false,
    /** Leading icon of the row, in `primary`. */
    open val icon: ImageVector? = null,
) {
    data class Switch(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        override val isEnabled: Boolean = true,
        override val requiresValidation: Boolean = false,
        val validationMessage: String? = null,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, requiresValidation, icon)

    data class Navigation(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val action: () -> Unit,
        override val isEnabled: Boolean = true,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, icon = icon)

    data class Selection(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val currentValue: String,
        val action: () -> Unit,
        override val isEnabled: Boolean = true,
        override val icon: ImageVector? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, icon = icon)
}
