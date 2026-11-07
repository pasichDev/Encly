package com.pasich.encly.presentation.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.pasich.encly.shared.resources.Res
import com.pasich.encly.shared.resources.ic_launcher_foreground
import org.jetbrains.compose.resources.painterResource
import kotlin.math.hypot
import kotlin.math.max

/**
 * The tile grows until the window is the logo's colour... Grow, the frames that follow and the push add up to
 * about 0.5 s, so the colour never reads as a green screen the user waits behind. There is no fixed pause
 * under the cover: the list is rendered by its own skeleton while its notes load, so it can already move.
 */
internal const val UNLOCK_GROW_MS = 200

/** ...then the notes list slides in from the end side (the right, left to right) and pushes that colour out. */
internal const val UNLOCK_PUSH_MS = 250

/** The glyph is gone by this share of the growth. */
private const val GLYPH_OUT = 0.25f

/** The tile's corner radius as a share of its width, as on the lock screen's logo (20 of 64 dp). */
private const val TILE_CORNER = 20f / 64f

/** The design system's standard curve: a quick start that settles gently, no overshoot. */
private val UnlockEasing = CubicBezierEasing(a = 0.3f, b = 0f, c = 0.2f, d = 1f)

/** Material's emphasized decelerate: the list glides in fast and settles slowly into place. */
private val PushEasing = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)

/** The short reveal: the lock screen fades out, then the app fades in. */
internal const val QUICK_OUT_MS = 100
internal const val QUICK_IN_MS = 160

/** Frames the first screen gets to compose and draw under the cover before it starts to rise. */
internal const val SETTLE_FRAMES = 3

/** The lock screen has faded under the backdrop by this share of the growth. */
private const val LOCK_OUT = 0.4f

/**
 * Where the lock screen's logo sits (root coordinates) and whether the unlock reveal is playing.
 * The lock screen reports the bounds and calls [start] once the vault opens; [contentOffset] is
 * how far the screens sit right of their place meanwhile (1 = just off the right edge).
 */
@Stable
class UnlockRevealState {
    var logoBounds by mutableStateOf<Rect?>(null)
    var running by mutableStateOf(false)
        private set
    var contentOffset by mutableFloatStateOf(0f)
        private set
    private var onCovered: (() -> Unit)? = null

    /**
     * Plays the reveal and runs [onCovered] (the navigation away from the lock screen) once the
     * logo colour fills the window, so the first screen composes out of sight. Without the logo's
     * bounds it navigates straight away.
     */
    fun start(onCovered: () -> Unit) {
        if (running) return
        if (logoBounds == null) {
            onCovered()
            return
        }
        this.onCovered = onCovered
        full = !playedFull
        playedFull = true
        running = true
    }

    /**
     * Whether this run is the full reveal. Only the first unlock after the app starts gets it;
     * coming back to an unlocked-before app gets a short cross-fade instead.
     */
    var full by mutableStateOf(true)
        private set
    private var playedFull = false

    internal fun cover() {
        contentOffset = 1f
        onCovered?.invoke()
        onCovered = null
    }

    /**
     * Stops a reveal that is playing (the session re-locked meanwhile): the pending navigation
     * is dropped and the screens are back in place, so nothing is left covering the lock screen.
     */
    fun cancel() {
        onCovered = null
        running = false
        contentOffset = 0f
    }

    internal fun push(progress: Float) {
        contentOffset = 1f - progress
    }

    internal fun finish() {
        contentOffset = 0f
        running = false
    }
}

/** Provided by App next to its [UnlockRevealOverlay]; without it (previews, tests) unlock just navigates. */
val LocalUnlockReveal = staticCompositionLocalOf<UnlockRevealState?> { null }

/** [UnlockRevealState.start] where App provides the reveal; elsewhere (previews, tests) runs [leave] at once. */
fun UnlockRevealState?.revealThen(leave: () -> Unit) {
    if (this != null) start(leave) else leave()
}

/**
 * The unlock reveal, drawn above the NavHost as one continuous move. The logo tile grows from its
 * place while the lock screen fades under a `surface` backdrop, until the window is `primary`.
 * Only then does the app leave the lock screen ([UnlockRevealState.start]), so the notes list
 * composes behind the colour; after a few frames it slides in from the right
 * ([UnlockRevealState.contentOffset], applied by App) and pushes the colour out the left. It
 * swallows touches while it plays, so the lock screen's keypad can't be tapped on the way out.
 */
@Composable
fun UnlockRevealOverlay(state: UnlockRevealState, modifier: Modifier = Modifier) {
    val bounds = state.logoBounds
    val animationsOff = rememberAnimationsOff()
    when {
        !state.running || bounds == null -> Unit
        animationsOff -> LeaveAtOnce(state)
        !state.full -> QuickUnlockFade(state, modifier)
        else -> FullUnlockReveal(state, bounds, modifier)
    }
}

/** The system asks for no animation: leave the lock screen at once, no colour, no slide. */
@Composable
private fun LeaveAtOnce(state: UnlockRevealState) {
    LaunchedEffect(Unit) {
        state.cover()
        state.push(1f)
        state.finish()
    }
}

@Composable
private fun FullUnlockReveal(state: UnlockRevealState, bounds: Rect, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val glyph = painterResource(Res.drawable.ic_launcher_foreground)
    val grow = remember { Animatable(0f) }
    val push = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, tween(UNLOCK_GROW_MS, easing = UnlockEasing))
        state.cover()
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        push.animateTo(1f, tween(UNLOCK_PUSH_MS, easing = PushEasing)) { state.push(value) }
        state.finish()
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val p = grow.value
        if (p < 1f) {
            val center = bounds.center
            val cover = max(
                max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
                max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
            )
            val half = lerp(bounds.width / 2, cover, p)
            drawRect(color = colors.surface, alpha = (p / LOCK_OUT).coerceIn(0f, 1f))
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(center.x - half, center.y - half),
                size = Size(half * 2, half * 2),
                cornerRadius = CornerRadius(lerp(bounds.width * TILE_CORNER, half, p)),
            )
            val glyphAlpha = (1f - p / GLYPH_OUT).coerceIn(0f, 1f)
            if (glyphAlpha > 0f) {
                translate(bounds.left, bounds.top) {
                    with(glyph) {
                        draw(bounds.size, alpha = glyphAlpha, colorFilter = ColorFilter.tint(colors.onPrimary))
                    }
                }
            }
        } else {
            // A flat sheet, so the edge the list pushes against is a straight line.
            val towardsStart = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
            drawRect(color = colors.primary, topLeft = Offset(towardsStart * push.value * size.width, 0f))
        }
    }
}

/**
 * The short reveal for later unlocks: the lock screen fades into the ground, the app leaves it
 * out of sight, and the screen underneath fades in. No logo, no slide.
 */
@Composable
private fun QuickUnlockFade(state: UnlockRevealState, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val cover = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        cover.animateTo(1f, tween(QUICK_OUT_MS))
        state.cover()
        state.push(1f)
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        cover.animateTo(0f, tween(QUICK_IN_MS, easing = UnlockEasing))
        state.finish()
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        drawRect(color = surface, alpha = cover.value)
    }
}
