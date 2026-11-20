package com.pasich.encly.presentation.effects

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun rememberImeVisible(): Boolean = WindowInsets.isImeVisible

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal actual fun windowWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp
