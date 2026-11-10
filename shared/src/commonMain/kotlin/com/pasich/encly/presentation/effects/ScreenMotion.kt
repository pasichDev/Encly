package com.pasich.encly.presentation.effects

import com.pasich.encly.presentation.navigation.NavRoutes

/** How two screens swap places. */
enum class ScreenMotion {
    /** The shared X axis: the new screen slides in as it fades in. */
    AXIS,

    /** A plain fade, so the screen does not slide against a note card growing into the editor. */
    FADE,

    /** No animation at all. */
    NONE,
}

private val LOCK_ROUTE_NAME = NavRoutes.LockRoute.name
private val EDITOR_ROUTE_PREFIX = "${NavRoutes.EditNoteRoute.name}/"
private val NOTE_LIST_ROUTES = setOf(NavRoutes.HomeRoute.name, NavRoutes.ArchiveRoute.name)

/**
 * The motion between the destinations with routes [from] and [to] (as `NavDestination.route`).
 *
 * - Any pair that involves the lock screen is [ScreenMotion.NONE]: a re-lock must never cross-fade
 *   the decrypted screen it leaves, and an unlock happens under the reveal overlay.
 * - A note list (Home, Archive) and the editor, either way, is [ScreenMotion.FADE]: the note card
 *   morphs into the editor (see [rememberNoteContainerModifier]), and sliding the screens as well
 *   would fight that.
 * - Everything else is [ScreenMotion.AXIS].
 */
fun screenMotion(from: String?, to: String?): ScreenMotion = when {
    from == LOCK_ROUTE_NAME || to == LOCK_ROUTE_NAME -> ScreenMotion.NONE
    (isNoteList(from) && isEditor(to)) || (isEditor(from) && isNoteList(to)) -> ScreenMotion.FADE
    else -> ScreenMotion.AXIS
}

private fun isNoteList(route: String?) = route in NOTE_LIST_ROUTES

private fun isEditor(route: String?) = route?.startsWith(EDITOR_ROUTE_PREFIX) == true
