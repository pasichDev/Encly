package com.pasich.encly.domain.model

/**
 * What swiping a note card can do, stored by ordinal. Append new actions without reordering
 * these values, so a stored choice keeps its meaning.
 */
enum class SwipeAction {
    /** Pins the note, or unpins it. Not available for an archived note. */
    PIN,

    /** Archives the note, or takes it out of the archive. */
    ARCHIVE,

    /** The next colour of the palette, wrapping from the last back to none. */
    NEXT_COLOR,

    /** Moves the note to the trash, after a confirmation. */
    DELETE,

    NONE,
}

/** Which way a card is swiped: [RIGHT] is start to end and [LEFT] end to start, whatever the layout direction. */
enum class SwipeSide { RIGHT, LEFT }

/** The swipe settings: a master switch and one action per direction. */
data class SwipeSettings(
    val enabled: Boolean = true,
    val right: SwipeAction = SwipeAction.PIN,
    val left: SwipeAction = SwipeAction.ARCHIVE,
) {
    /** The action for [side]; [SwipeAction.NONE] while swipes are off. */
    fun actionFor(side: SwipeSide): SwipeAction = when {
        !enabled -> SwipeAction.NONE
        side == SwipeSide.RIGHT -> right
        else -> left
    }

    companion object {
        /** No swipe at all: what a list uses until the stored settings are read. */
        val Off = SwipeSettings(enabled = false)
    }
}
