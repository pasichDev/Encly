package com.pasich.encly.presentation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** The "Reduce Motion" accessibility setting, the iOS counterpart of Android's animator scale of 0. */
@Composable
internal actual fun rememberAnimationsOff(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
