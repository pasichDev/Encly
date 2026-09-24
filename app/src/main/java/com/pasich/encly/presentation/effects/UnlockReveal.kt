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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.lerp
import com.pasich.encly.R
import kotlin.math.hypot
import kotlin.math.max

private const val GROW_MS = 450
private const val FADE_MS = 150

/** The glyph is gone by this share of the growth; the tile turns to `surface` from [WASH_FROM]. */
private const val GLYPH_OUT = 0.25f
private const val WASH_FROM = 0.4f

/** The tile's corner radius as a share of its width, as on the lock screen's logo (20 of 64 dp). */
private const val TILE_CORNER = 20f / 64f

/** The design system's standard curve: a quick start that settles gently, no overshoot. */
private val Grow = CubicBezierEasing(a = 0.3f, b = 0f, c = 0.2f, d = 1f)

/**
 * Where the lock screen's logo sits (root coordinates) and whether the unlock reveal is playing.
 * The lock screen reports the bounds and calls [start] once the vault opens.
 */
@Stable
class UnlockRevealState {
    var logoBounds by mutableStateOf<Rect?>(null)
    var running by mutableStateOf(false)
        private set

    fun start() {
        if (logoBounds != null) running = true
    }

    internal fun finish() {
        running = false
    }
}

val LocalUnlockReveal = staticCompositionLocalOf { UnlockRevealState() }

/**
 * The unlock reveal, drawn above the NavHost: the logo tile grows from its place on the lock
 * screen until it covers the window, turning into the notes list's ground, then fades away over
 * the list. It draws only; touches pass through to the list.
 */
@Composable
fun UnlockRevealOverlay(state: UnlockRevealState, modifier: Modifier = Modifier) {
    val bounds = state.logoBounds
    if (!state.running || bounds == null) return
    val colors = MaterialTheme.colorScheme
    val glyph = painterResource(R.drawable.ic_launcher_foreground)
    val grow = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, tween(GROW_MS, easing = Grow))
        alpha.animateTo(0f, tween(FADE_MS))
        state.finish()
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = grow.value
        val center = bounds.center
        val cover = max(
            max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
            max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
        )
        val half = lerp(bounds.width / 2, cover, p)
        val wash = ((p - WASH_FROM) / (1f - WASH_FROM)).coerceIn(0f, 1f)
        drawRoundRect(
            color = lerp(colors.primary, colors.surface, wash),
            topLeft = Offset(center.x - half, center.y - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(lerp(bounds.width * TILE_CORNER, half, p)),
            alpha = alpha.value,
        )
        val glyphAlpha = (1f - p / GLYPH_OUT).coerceIn(0f, 1f)
        if (glyphAlpha > 0f) {
            translate(bounds.left, bounds.top) {
                with(glyph) {
                    draw(bounds.size, alpha = glyphAlpha, colorFilter = ColorFilter.tint(colors.onPrimary))
                }
            }
        }
    }
}
