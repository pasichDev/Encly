package com.pasich.encly.desktop.window

import com.pasich.encly.core.platform.KeyValueStore

/** A window rectangle in AWT pixels (logical, as AWT reports them). */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    /** The area of the overlap with [other], 0 when they do not touch. */
    fun overlapArea(other: WindowBounds): Long {
        val w = minOf(right, other.right) - maxOf(x, other.x)
        val h = minOf(bottom, other.bottom) - maxOf(y, other.y)
        return if (w > 0 && h > 0) w.toLong() * h else 0L
    }
}

/** What is remembered of the window between runs. [bounds] is null for a first run (the OS places it). */
data class SavedWindow(val width: Int, val height: Int, val x: Int?, val y: Int?, val maximised: Boolean)

/**
 * The window's size, position and maximised state in the app's flags file. What comes back is always usable:
 * a size below the minimum or above the biggest screen is clamped, and a position that would put the window
 * (almost) off every connected screen (a monitor that is gone) is dropped, so the OS centres it.
 */
class WindowStateStore(
    private val flags: KeyValueStore,
    private val minWidth: Int = MIN_WIDTH,
    private val minHeight: Int = MIN_HEIGHT,
) {
    fun load(screens: List<WindowBounds>): SavedWindow? {
        if (!flags.contains(KEY_WIDTH) || !flags.contains(KEY_HEIGHT)) return null
        val biggest = screens.maxByOrNull { it.width.toLong() * it.height }
        val width = flags.getLong(KEY_WIDTH, 0L).toInt().coerceAtLeast(minWidth).let { w ->
            if (biggest == null) w else w.coerceAtMost(biggest.width)
        }
        val height = flags.getLong(KEY_HEIGHT, 0L).toInt().coerceAtLeast(minHeight).let { h ->
            if (biggest == null) h else h.coerceAtMost(biggest.height)
        }
        val x = flags.getLong(KEY_X, Long.MIN_VALUE).takeIf { flags.contains(KEY_X) }?.toInt()
        val y = flags.getLong(KEY_Y, Long.MIN_VALUE).takeIf { flags.contains(KEY_Y) }?.toInt()
        val onScreen = x != null && y != null && isVisible(WindowBounds(x, y, width, height), screens)
        return SavedWindow(
            width = width,
            height = height,
            x = if (onScreen) x else null,
            y = if (onScreen) y else null,
            maximised = flags.getBoolean(KEY_MAXIMISED, false),
        )
    }

    /** Remembers the window. The size and position of a maximised window are kept from before it was maximised. */
    fun save(bounds: WindowBounds?, maximised: Boolean) {
        flags.edit {
            putBoolean(KEY_MAXIMISED, maximised)
            if (bounds != null && !maximised) {
                putLong(KEY_WIDTH, bounds.width.toLong())
                putLong(KEY_HEIGHT, bounds.height.toLong())
                putLong(KEY_X, bounds.x.toLong())
                putLong(KEY_Y, bounds.y.toLong())
            }
        }
    }

    private fun isVisible(bounds: WindowBounds, screens: List<WindowBounds>): Boolean {
        if (screens.isEmpty()) return true
        val needed = minOf(bounds.width.toLong() * bounds.height / VISIBLE_DIVISOR, MIN_VISIBLE_AREA)
        return screens.any { bounds.overlapArea(it) >= needed }
    }

    companion object {
        const val MIN_WIDTH = 720
        const val MIN_HEIGHT = 520
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 720

        // A window counts as on a screen when at least a quarter of it (or 200 x 100 px) is.
        private const val VISIBLE_DIVISOR = 4
        private const val MIN_VISIBLE_AREA = 200L * 100

        private const val KEY_WIDTH = "window_width"
        private const val KEY_HEIGHT = "window_height"
        private const val KEY_X = "window_x"
        private const val KEY_Y = "window_y"
        private const val KEY_MAXIMISED = "window_maximised"
    }
}
