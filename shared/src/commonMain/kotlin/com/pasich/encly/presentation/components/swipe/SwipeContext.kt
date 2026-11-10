package com.pasich.encly.presentation.components.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.presentation.viewmodel.NoteListEvent

/**
 * What a swipeable card needs from its list: the [settings], how to carry out an action
 * ([perform] says whether the write succeeded), and [reducedMotion], read once per list.
 */
@Stable
class SwipeContext(
    val settings: SwipeSettings,
    val perform: suspend (NoteListEvent) -> Boolean,
    val reducedMotion: Boolean,
)

/** The [SwipeContext] of a list: rebuilt only when the settings change, with the system's motion setting read once. */
@Composable
fun rememberSwipeContext(settings: SwipeSettings, perform: suspend (NoteListEvent) -> Boolean): SwipeContext {
    val reduced = rememberReducedMotion()
    return remember(settings, perform, reduced) { SwipeContext(settings, perform, reduced) }
}
