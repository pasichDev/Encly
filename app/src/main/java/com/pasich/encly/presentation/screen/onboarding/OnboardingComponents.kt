package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.pasich.encly.ui.theme.EnclyTheme

/** Content padding of a step body: the 24 dp gutter. */
internal val StepPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = EnclyTheme.spacing.gutter)
