package com.pasich.encly.presentation.effects

/**
 * The motion of each destination's current transition, decided when the transition starts (by
 * [enterFor] and [exitFor]) and read when a frame is drawn (by [screenFade]). Destinations not in it
 * do not fade.
 */
internal object ScreenMotions {
    private const val KEPT = 16
    private val byEntry = HashMap<String, ScreenMotion>()
    private val order = ArrayDeque<String>()

    fun record(entryId: String, motion: ScreenMotion) {
        if (byEntry.put(entryId, motion) != null) order.remove(entryId)
        order.addLast(entryId)
        while (order.size > KEPT) byEntry.remove(order.removeFirst())
    }

    fun of(entryId: String): ScreenMotion = byEntry[entryId] ?: ScreenMotion.NONE
}
