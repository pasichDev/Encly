package com.pasich.encly.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window gives the UI (Material window width classes). [Compact] is the phone layout and
 * the default of every composable that no host sized: a screen drawn on its own looks like it always did.
 */
enum class WidthClass {
    /** Phones, and any window narrower than [MEDIUM_MIN_WIDTH] or lower than [MIN_HEIGHT_FOR_WIDE]: today's UI. */
    Compact,

    /** Large phones unfolded, small tablets, narrow desktop windows: a navigation rail, centred content. */
    Medium,

    /** Tablets, unfolded devices and desktop windows: a permanent drawer, notes beside the open note. */
    Expanded,
    ;

    /** Whether the UI is laid out for more than the phone. */
    val isWide: Boolean get() = this != Compact

    companion object {
        /** The width from which the layout is [Medium]. */
        val MEDIUM_MIN_WIDTH: Dp = 600.dp

        /** The width from which the layout is [Expanded]. */
        val EXPANDED_MIN_WIDTH: Dp = 840.dp

        /** A window lower than this is [Compact] whatever its width, so a phone held sideways stays a phone. */
        val MIN_HEIGHT_FOR_WIDE: Dp = 480.dp

        /** The class of a window [width] wide and [height] high. */
        fun of(width: Dp, height: Dp): WidthClass = when {
            height < MIN_HEIGHT_FOR_WIDE || width < MEDIUM_MIN_WIDTH -> Compact
            width < EXPANDED_MIN_WIDTH -> Medium
            else -> Expanded
        }
    }
}

/** The width class the UI is laid out in; `EnclyApp` provides it from the window, tests provide it directly. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

/** How tightly the UI is packed: [Touch] is the phone spacing, [Desktop] the tighter one for a pointer. */
enum class DensityProfile {
    /** The spacing the app has always had, sized for fingers. */
    Touch,

    /** Tighter paddings and row heights for an expanded window driven by a mouse and keyboard. */
    Desktop,
    ;

    companion object {
        /** [Desktop] only for an [WidthClass.Expanded] window on a [pointerFirst] device, else [Touch]. */
        fun of(widthClass: WidthClass, pointerFirst: Boolean): DensityProfile =
            if (widthClass == WidthClass.Expanded && pointerFirst) Desktop else Touch
    }
}

/** The density the UI is packed in; `EnclyApp` provides it and [EnclyTheme] turns it into [EnclySpacing]. */
val LocalDensityProfile = staticCompositionLocalOf { DensityProfile.Touch }

/** Whether the device is driven by a mouse and keyboard first (a desktop), as the platform says. */
internal expect fun isPointerFirstDevice(): Boolean

/** Whether the UI is driven by a mouse and keyboard first; the platform says, tests provide it directly. */
val LocalPointerFirst = staticCompositionLocalOf { isPointerFirstDevice() }

/** The spacing tokens for a [profile]; [DensityProfile.Touch] is the default [EnclySpacing]. */
fun enclySpacingFor(profile: DensityProfile): EnclySpacing = when (profile) {
    DensityProfile.Touch -> EnclySpacing()

    DensityProfile.Desktop -> EnclySpacing(
        gutter = 20.dp,
        listGutter = 12.dp,
        cardGap = 8.dp,
        section = 20.dp,
        rowGap = 12.dp,
        fieldGap = 14.dp,
        topBarHeight = 52.dp,
        buttonHeight = 44.dp,
        textButtonHeight = 36.dp,
        rowHeight = 44.dp,
        drawerWidth = 280.dp,
    )
}
