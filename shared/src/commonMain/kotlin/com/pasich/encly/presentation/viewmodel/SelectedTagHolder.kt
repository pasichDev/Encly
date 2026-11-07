package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Tag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * The tag chip the note list is filtered by, shared by the tag row and the note list of one window. It belongs to
 * the open session: a lock drops it at once, so the next vault (the decoy one after a duress PIN) never starts
 * filtered by, or naming, a tag of the vault that was closed. A host with several windows gives each window its own
 * (see [WindowSelections]); [close] ends the watch on the lock when the window goes.
 */
class SelectedTagHolder(lockEvents: VaultLockEvents = NeverLocked) {
    private val _selectedTagFlow = MutableSharedFlow<Tag>(replay = 1)
    val selectedTagFlow: SharedFlow<Tag> = _selectedTagFlow.asSharedFlow()

    // Unconfined: the reset runs inside the lock itself, before any later unlock can publish.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        scope.launch {
            lockEvents.locked.collect { locked -> if (locked) _selectedTagFlow.tryEmit(Tag()) }
        }
    }

    fun selectTag(tag: Tag) {
        _selectedTagFlow.tryEmit(tag)
    }

    /** The window this belongs to is gone: stop watching the lock. */
    fun close() {
        scope.cancel()
    }
}

/**
 * Which window's [SelectedTagHolder] the ViewModels being created now get. A window's ViewModel factory sets it
 * around every creation (see `AppViewModelFactory.forWindow`), on the UI thread where they are created; nothing
 * set means the process-wide one of a host with a single window.
 */
object WindowSelections {
    @Volatile
    private var creating: SelectedTagHolder? = null

    /** Runs [create] so that every [SelectedTagHolder] it asks for is [holder]. */
    fun <T> creatingFor(holder: SelectedTagHolder, create: () -> T): T {
        val before = creating
        creating = holder
        try {
            return create()
        } finally {
            creating = before
        }
    }

    internal fun current(): SelectedTagHolder? = creating
}

/** The tag selection of a host with one window, which also serves a window that has none of its own. */
@SingleIn(AppScope::class)
@Inject
class SelectedTags(lockEvents: VaultLockEvents) {
    private val shared = SelectedTagHolder(lockEvents)

    /** The holder for a ViewModel being created now. */
    fun forViewModel(): SelectedTagHolder = WindowSelections.current() ?: shared
}

@ContributesTo(AppScope::class)
@BindingContainer
object SelectedTagModule {
    @Provides
    fun provideSelectedTagHolder(tags: SelectedTags): SelectedTagHolder = tags.forViewModel()
}
