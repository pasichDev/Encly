package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.PurgeExpiredTrashUseCase
import com.pasich.encly.whatsnew.BundledChangelogs
import com.pasich.encly.whatsnew.Changelogs
import com.pasich.encly.whatsnew.ReleaseNotes
import com.pasich.encly.whatsnew.WhatsNewTracker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the app does once per start and once per unlock, off any screen's own business: tells [WhatsNewTracker]
 * about a fresh install, and, when the vault has been opened, empties the trash of notes older than 28 days and
 * decides whether "What's new" is due, and whether the person who came from a version below 3.0.0 is told that the
 * trash now empties itself.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class StartupViewModel(
    private val whatsNew: WhatsNewTracker,
    private val purgeTrash: PurgeExpiredTrashUseCase,
    private val notes: NotesRepository,
) : ViewModel() {
    private val _whatsNewDue = MutableStateFlow<ReleaseNotes?>(null)

    /** The notes to show in the "What's new" sheet, or null when none is due (or it was dismissed). */
    val whatsNewDue: StateFlow<ReleaseNotes?> = _whatsNewDue.asStateFlow()

    private val _trashNoticeCount = MutableStateFlow<Int?>(null)

    /**
     * How many notes are in the trash, when this start is the first after an update from a version below 3.0.0 (it
     * did not empty the trash) and the trash has notes; else null. Shown once, then [dismissTrashNotice].
     */
    val trashNoticeCount: StateFlow<Int?> = _trashNoticeCount.asStateFlow()

    private var unlocked = false

    /** The app started: a [freshInstall] has seen no version before this one, so it has nothing new to read. */
    fun onAppStart(freshInstall: Boolean) {
        if (freshInstall) whatsNew.recordFreshInstall()
    }

    /** The vault is open and the home screen is shown (once per ViewModel): clean the trash, and check the version. */
    fun onUnlocked(languageTag: String, bundled: Map<String, String> = BundledChangelogs.notes) {
        if (unlocked) return
        unlocked = true
        val update = whatsNew.takeUpdate()
        viewModelScope.launch {
            purgeTrash()
            if (update?.fromBeforeTrashRetention == true) {
                val inTrash = notes.getTrashNotes().first().size
                if (inTrash > 0) _trashNoticeCount.value = inTrash
            }
        }
        if (update != null) _whatsNewDue.value = Changelogs.notes(languageTag, bundled)
    }

    fun dismissTrashNotice() {
        _trashNoticeCount.value = null
    }

    fun dismissWhatsNew() {
        _whatsNewDue.value = null
    }
}
