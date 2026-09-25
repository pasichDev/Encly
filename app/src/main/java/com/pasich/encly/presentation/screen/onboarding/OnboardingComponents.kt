package com.pasich.encly.presentation.screen.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.pasich.encly.ui.theme.EnclyTheme

/** Content padding of a step body: the 24 dp gutter. */
internal val StepPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = EnclyTheme.spacing.gutter)

/** The footer of one step: the primary action and an optional text action under it. */
internal class FooterSpec(
    @param:StringRes val primary: Int,
    val onPrimary: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    @param:StringRes val secondary: Int? = null,
    val onSecondary: () -> Unit = {},
    val secondaryMuted: Boolean = false,
)
