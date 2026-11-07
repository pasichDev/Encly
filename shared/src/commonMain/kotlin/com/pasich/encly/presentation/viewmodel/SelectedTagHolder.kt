package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Tag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The tag chip the note list is filtered by, shared by the tag row and the note list. It belongs to the open
 * session: a lock drops it at once, so the next vault (the decoy one after a duress PIN) never starts filtered by,
 * or naming, a tag of the vault that was closed.
 */
@SingleIn(AppScope::class)
@Inject
class SelectedTagHolder(lockEvents: VaultLockEvents = NeverLocked) {
    private val _selectedTagFlow = MutableSharedFlow<Tag>(replay = 1)
    val selectedTagFlow: SharedFlow<Tag> = _selectedTagFlow.asSharedFlow()

    init {
        // Unconfined: the reset runs inside the lock itself, before any later unlock can publish.
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch {
            lockEvents.locked.collect { locked -> if (locked) _selectedTagFlow.tryEmit(Tag()) }
        }
    }

    fun selectTag(tag: Tag) {
        _selectedTagFlow.tryEmit(tag)
    }
}
