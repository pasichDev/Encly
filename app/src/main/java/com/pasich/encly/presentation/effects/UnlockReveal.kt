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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.lerp
import com.pasich.encly.R
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max

/** The tile grows until the window is the logo's colour... */
private const val UNLOCK_GROW_MS = 300

/** ...then the notes list slides in from the right and pushes that colour out the left. */
private const val UNLOCK_PUSH_MS = 520

/** How long the list rests under the cover first, so its data and first layout land before it moves. */
private const val SETTLE_MS = 120L

/** The glyph is gone by this share of the growth. */
private const val GLYPH_OUT = 0.25f

/** The tile's corner radius as a share of its width, as on the lock screen's logo (20 of 64 dp). */
private const val TILE_CORNER = 20f / 64f

/** The design system's standard curve: a quick start that settles gently, no overshoot. */
private val UnlockEasing = CubicBezierEasing(a = 0.3f, b = 0f, c = 0.2f, d = 1f)

/** Material's emphasized decelerate: the list glides in fast and settles slowly into place. */
private val PushEasing = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)

/** Frames the first screen gets to compose and draw under the cover before it starts to rise. */
private const val SETTLE_FRAMES = 3

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
        running = true
    }

    internal fun cover() {
        contentOffset = 1f
        onCovered?.invoke()
        onCovered = null
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
 * composes behind the colour; after a short rest it slides in from the right
 * ([UnlockRevealState.contentOffset], applied by App) and pushes the colour out the left. It
 * swallows touches while it plays, so the lock screen's keypad can't be tapped on the way out.
 */
@Composable
fun UnlockRevealOverlay(state: UnlockRevealState, modifier: Modifier = Modifier) {
    val bounds = state.logoBounds
    if (!state.running || bounds == null) return
    val colors = MaterialTheme.colorScheme
    val glyph = painterResource(R.drawable.ic_launcher_foreground)
    val grow = remember { Animatable(0f) }
    val push = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, tween(UNLOCK_GROW_MS, easing = UnlockEasing))
        state.cover()
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        delay(SETTLE_MS)
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
            drawRect(color = colors.primary, topLeft = Offset(-push.value * size.width, 0f))
        }
    }
}
