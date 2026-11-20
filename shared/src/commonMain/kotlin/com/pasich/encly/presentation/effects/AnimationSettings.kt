package com.pasich.encly.presentation.effects

import androidx.compose.runtime.Composable

/**
 * Whether the system asks for no animation at all ("remove animations" / animator duration scale 0 on
 * Android). Read once when it enters the composition; false where the platform has no such setting.
 */
@Composable
internal expect fun rememberAnimationsOff(): Boolean
