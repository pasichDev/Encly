package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps a sheet's slide whole while the keyboard moves under it. Material lays a sheet out in what the
 * keyboard leaves of the window, so every frame of a rising keyboard gives a slide that still runs a new end
 * and starts it over, from rest: a sheet whose field takes focus as it opens stopped half way, waited, then
 * jumped, and one closed with the keyboard up left the keyboard standing over the screen. Here the same two
 * slides run a second time, never restarted, measured from the keyboard's edge as it is in each frame; the
 * sheet is drawn at whichever is further along, its own or Material's. With no keyboard the two agree and
 * nothing changes; with one, the sheet rises on it as it opens and goes down with it as it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Modifier.slideWithKeyboard(sheetState: SheetState): Modifier {
    val slide = remember(sheetState) {
        SheetSlide(open = sheetState.currentValue != SheetValue.Hidden || sheetState.hasPartiallyExpandedState)
    }
    LaunchedEffect(slide) { slide.follow(sheetState) }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // Material's own place for the sheet in its container; unknown until its first layout.
            val material = runCatching { sheetState.requireOffset() }.getOrNull()
            val shift = if (material == null || !constraints.hasBoundedHeight) {
                0
            } else {
                slide.shift(container = constraints.maxHeight, sheet = placeable.height, material = material)
            }
            placeable.place(0, shift)
        }
    }
}

/** A sheet's two slides, run beside Material's: see [slideWithKeyboard]. */
@OptIn(ExperimentalMaterial3Api::class)
private class SheetSlide(open: Boolean) {
    private enum class Phase { OPENING, OPEN, CLOSING }

    private var phase by mutableStateOf(if (open) Phase.OPEN else Phase.OPENING)
    private val opened = Animatable(if (open) 1f else 0f)
    private val closed = Animatable(0f)

    /** The slide in has run to its end (a sheet that starts open has none to run). */
    private var arrived = open

    /** How far from Material's place the sheet is drawn: up while it opens, down while it closes. */
    fun shift(container: Int, sheet: Int, material: Float): Int = when (phase) {
        Phase.OPEN -> 0
        Phase.OPENING -> (minOf(material, container - opened.value * sheet) - material).roundToInt()
        Phase.CLOSING -> (maxOf(material, container - sheet + closed.value * sheet) - material).roundToInt()
    }

    suspend fun follow(sheetState: SheetState) = coroutineScope {
        if (phase == Phase.OPENING) {
            launch {
                opened.animateTo(1f, OpenSlide)
                arrived = true
                if (phase == Phase.OPENING && !sheetState.isAnimationRunning) phase = Phase.OPEN
            }
        }
        var closing: Job? = null
        snapshotFlow { sheetState.isAnimationRunning to sheetState.targetValue }.collect { (running, target) ->
            val hiding = running && target == SheetValue.Hidden
            when {
                hiding && phase != Phase.CLOSING -> {
                    phase = Phase.CLOSING
                    closing = launch {
                        closed.snapTo(0f)
                        closed.animateTo(1f, CloseSlide)
                    }
                }

                !hiding && phase == Phase.CLOSING -> {
                    closing?.cancel()
                    phase = Phase.OPEN
                }

                !running && phase == Phase.OPENING && arrived -> phase = Phase.OPEN
            }
        }
    }

    private companion object {
        // Material's own slides (its standard motion scheme: default spatial in, fast effects out), so the
        // sheet moves as it always did when there is no keyboard to follow.
        val OpenSlide = spring<Float>(dampingRatio = 0.9f, stiffness = 700f)
        val CloseSlide = spring<Float>(dampingRatio = 1f, stiffness = 3800f)
    }
}

/**
 * Sends the keyboard away as its sheet starts to close, and takes the focus from its field: left to the
 * window, the keyboard stays up until the sheet has gone and then drops by itself over the screen under it,
 * a field's selection handle with it. Call it in the sheet's content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HideKeyboardWithSheet(sheetState: SheetState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(sheetState, keyboard, focus) {
        snapshotFlow { sheetState.currentValue == SheetValue.Expanded && sheetState.targetValue == SheetValue.Hidden }
            .collect { closing ->
                if (closing) {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                }
            }
    }
}
