package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.presentation.pane.NoteTabs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/**
 * The open notes of the expanded layout's detail pane. The screen asks for it from the home destination's
 * entry, so it outlives the home composition while another page is on top, and goes with the entry when the
 * vault locks (the lock screen replaces the whole back stack): no tab, and no title, survives a re-lock.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class NoteTabsViewModel(lockEvents: VaultLockEvents = NeverLocked) : ViewModel() {
    /** The tabs. */
    val tabs = NoteTabs()

    init {
        // The tabs are dropped the moment the session locks, not when the lock screen gets to replace this entry.
        clearOnLock(lockEvents) { tabs.clear() }
    }

    override fun onCleared() {
        tabs.clear()
        super.onCleared()
    }
}
