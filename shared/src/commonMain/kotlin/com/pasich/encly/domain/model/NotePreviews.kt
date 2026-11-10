package com.pasich.encly.domain.model

import com.pasich.encly.core.platform.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Show note previews" (Settings → Security, on by default). Off, the note lists show a note's title, tag and
 * date but not the first lines of its text, so nothing of a note's content is read over the owner's shoulder.
 * Search still matches the full text. The flag is read at once, from the app's flags, so the first list drawn
 * after an unlock already follows it.
 */
@SingleIn(AppScope::class)
@Inject
class NotePreviews(private val flags: KeyValueStore) {
    private val _show = MutableStateFlow(flags.getBoolean(SHOW_KEY, true))
    val show: StateFlow<Boolean> = _show.asStateFlow()

    fun setShow(enabled: Boolean) {
        flags.edit { putBoolean(SHOW_KEY, enabled) }
        _show.value = enabled
    }

    private companion object {
        const val SHOW_KEY = "show_note_previews"
    }
}
