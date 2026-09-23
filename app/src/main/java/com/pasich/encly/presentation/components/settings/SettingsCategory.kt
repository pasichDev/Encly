package com.pasich.encly.presentation.components.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsCategory(@param:StringRes val titleRes: Int, val items: List<SettingsItem>)

sealed class SettingsItem(
    @param:StringRes open val titleRes: Int,
    @param:StringRes open val subtitleRes: Int? = null,
    open val isEnabled: Boolean = true,
    open val requiresValidation: Boolean = false,
) {
    data class Switch(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        override val isEnabled: Boolean = true,
        override val requiresValidation: Boolean = false,
        val validationMessage: String? = null,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled, requiresValidation)

    data class Navigation(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val action: () -> Unit,
        val endIcon: ImageVector? = null,
        override val isEnabled: Boolean = true,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled)

    data class Selection(
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val subtitleRes: Int? = null,
        val currentValue: String,
        val action: () -> Unit,
        val endIcon: ImageVector? = null,
        override val isEnabled: Boolean = true,
    ) : SettingsItem(titleRes, subtitleRes, isEnabled)
}
