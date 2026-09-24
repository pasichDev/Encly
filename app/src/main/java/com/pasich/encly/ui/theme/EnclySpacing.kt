package com.pasich.encly.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizes on a 4 pt base (design spec §3.2): 4, 8, 12, 16, 24, 32, 48, plus the named
 * gaps and sizes the canvas uses. Read it through [EnclyTheme.spacing]: screens never write dp
 * values of their own (NoDpLiteralsTest).
 */
@Immutable
data class EnclySpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    /** Side padding of reading and form screens (onboarding, lock, editor, settings). */
    val gutter: Dp = 24.dp,
    /** Side padding of list screens (the notes list). */
    val listGutter: Dp = 16.dp,
    /** Vertical gap between stacked cards. */
    val cardGap: Dp = 10.dp,
    /** Vertical gap between sections. */
    val section: Dp = 24.dp,
    /** Gap between a title and its supporting line. */
    val textGap: Dp = 2.dp,
    /** Gap between a label and its field, or a chip label and its count. */
    val labelGap: Dp = 6.dp,
    /** Gap between a leading icon and the text of a row. */
    val rowGap: Dp = 14.dp,
    /** Gap between stacked form fields. */
    val fieldGap: Dp = 18.dp,
    /** Gap between the Welcome facts. */
    val factGap: Dp = 20.dp,
    /** Gap between the blocks of the PIN and verify steps. */
    val stepGap: Dp = 28.dp,
    /** Gap between the Welcome hero and its facts. */
    val heroGap: Dp = 36.dp,
    /** Top padding of the Welcome hero. */
    val heroTop: Dp = 56.dp,
    /** Top padding of the Ready step. */
    val readyTop: Dp = 40.dp,
    /** Top padding of the lock screen. */
    val lockTop: Dp = 96.dp,
    /** Space above and below the PIN dots on the lock screen. */
    val lockDotsTop: Dp = 36.dp,
    val lockDotsBottom: Dp = 44.dp,
    /** Hairlines between rows of one grouped list. */
    val hairline: Dp = 1.dp,
    /** Focus rings, radio rings and checkbox borders. */
    val stroke: Dp = 2.dp,
    val topBarHeight: Dp = 64.dp,
    val buttonHeight: Dp = 52.dp,
    val textButtonHeight: Dp = 44.dp,
    val fabHeight: Dp = 56.dp,
    val minTouchTarget: Dp = 48.dp,
    /** Minimum height of a list row. */
    val rowHeight: Dp = 56.dp,
    val icon: Dp = 24.dp,
    val iconMedium: Dp = 22.dp,
    val iconSmall: Dp = 20.dp,
    val iconXSmall: Dp = 18.dp,
    /** An empty-state or lock tile. */
    val tile: Dp = 64.dp,
    /** The globe tile on an editor link card. */
    val tileSmall: Dp = 40.dp,
    /** Width of the navigation drawer on tablets. */
    val drawerWidth: Dp = 320.dp,
    /** The empty area under the last editor block; a tap there adds a block. */
    val editorTapArea: Dp = 200.dp,
)

val LocalSpacing = staticCompositionLocalOf { EnclySpacing() }
