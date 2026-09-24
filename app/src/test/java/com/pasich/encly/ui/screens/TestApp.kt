package com.pasich.encly.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupMapper
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.presentation.viewmodel.AppearanceViewModel
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.LockNowViewModel
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.MainListStateViewModel
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.presentation.viewmodel.SelectedTagHolder
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.anyCharArray
import com.pasich.encly.utils.DeviceCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * The app's data layer as fakes, and every ViewModel built on it the way Hilt would. The
 * vault's key handling is a Mockito [SecurityManager]; tests stub what they need.
 */
internal class TestApp(context: Context) {
    val notes = TestNotesRepository()
    val tags = FakeTagsRepository()
    val tasks = FakeTasksRepository()
    val settings = FakeSettingsRepository()
    val security: SecurityManager = mock(SecurityManager::class.java)
    val documents: BackupDocuments = mock(BackupDocuments::class.java)
    val store = InMemoryVaultDataStore()
    val backupManager = BackupManager(security, store, InMemorySharedPreferences())
    val sessionLock = SessionLockManager(security)
    val selectedTag = SelectedTagHolder()
    init {
        // The app is in the foreground, so a completed unlock is published, not re-locked.
        sessionLock.onStart(mock(androidx.lifecycle.LifecycleOwner::class.java))
        withAuth(recoveryPhrase = false)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appContext = context

    fun withNotes(vararg notes: Note, tag: Tag? = null) = apply {
        this.notes.allNotesWithTags.value = notes.map { NoteWithTag(note = it, tag = tag) }
    }

    fun withTags(vararg tags: Tag) = apply { this.tags.tags.value = tags.toList() }

    fun withTasks(vararg tasks: Task) = apply { this.tasks.tasks.value = tasks.toList() }

    /**
     * A vault that can be created and opened: new phrases are [phrase], any PIN is accepted, and
     * a recovery phrase is valid when its BIP39 checksum holds.
     */
    fun withWorkingVault(phrase: String) = apply {
        `when`(security.generateMnemonicCode()).thenAnswer { phrase.toCharArray() }
        `when`(security.initializeNewVault(anyChars())).thenReturn(true)
        `when`(security.configurePin(anyCharArray())).thenReturn(true)
        `when`(security.verifyPin(anyCharArray())).thenReturn(true)
        `when`(security.isValidRecoveryPhrase(anyChars() ?: CharArray(0))).thenAnswer {
            runCatching { MnemonicCode(it.getArgument<CharArray>(0).copyOf()).validate() }.isSuccess
        }
    }

    /** The vault's stored security settings, as the Security page reads them. */
    fun withAuth(recoveryPhrase: Boolean, biometric: BiometricStatus = BiometricStatus.UNAVAILABLE) = apply {
        `when`(security.getSettingsAuth()).thenReturn(AuthSettings(AuthType.PIN, false, recoveryPhrase))
        `when`(security.biometricStatus()).thenReturn(biometric)
        `when`(security.hasRecoverySeed()).thenReturn(recoveryPhrase)
    }

    /** A backup file of [notes] sealed with [phrase], as an export would write it. */
    suspend fun backupFile(phrase: String, vararg notes: Note): ByteArray {
        val source = InMemoryVaultDataStore().apply { notes.forEach { insertNote(it) } }
        val plaintext = BackupPayloadCodec.encode(BackupMapper.toPayload(source.snapshot(), exportedAt = 1))
        return BackupCipher.seal(plaintext, BackupSecret.RecoveryPhrase(phrase.toCharArray()))
    }

    /** A picked document named [name] holding [bytes]. */
    fun document(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://test/$name")
        `when`(documents.read(uri)).thenReturn(bytes)
        `when`(documents.displayName(uri)).thenReturn(name)
        return uri
    }

    private fun anyChars(): CharArray? = ArgumentMatchers.any()

    private val observeNotes get() = ObserveNotesUseCase(notes, Dispatchers.Default)

    fun tagList() = TagListViewModel(tags, ReorderTagsUseCase(tags), selectedTag, notes)

    fun noteList(repository: NotesRepository = notes) = NoteListViewModel(
        observeNotesUseCase = ObserveNotesUseCase(repository, Dispatchers.Default),
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(notes),
        updateNoteTagUseCase = UpdateNoteTagUseCase(notes),
        selectedTagHolder = selectedTag,
        settingsRepository = settings,
        updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(notes),
    )

    fun search() = NoteSearchViewModel(observeNotes, Dispatchers.Default)

    fun tasksVm() = TasksViewModel(tasks, UpdateTaskStatusUseCase(tasks))

    fun statistics() = StatisticViewModel(tags, tasks)

    fun settingsVm() = SettingsViewModel(settings)

    fun mainListState() = MainListStateViewModel(settings)

    fun lockNow() = LockNowViewModel(sessionLock)

    fun trash() = TrashViewModel(notes, UpdateNoteTrashStatusUseCase(notes), CleanTrashNotesUseCase(notes))

    fun appearance(dynamicColors: Boolean = false) = AppearanceViewModel(
        settings,
        mock(DeviceCapabilities::class.java).also {
            `when`(it.supportsDynamicColors()).thenReturn(dynamicColors)
        },
    )

    fun lock() = LockViewModel(security, sessionLock)

    fun securitySettings() = SecuritySettingsViewModel(security, KeyboardPrivacy(InMemorySharedPreferences()))

    fun backup() = BackupViewModel(
        backupManager = backupManager,
        phraseSetup = BackupPhraseSetup(security),
        securityManager = security,
        sessionLockManager = sessionLock,
        documents = documents,
    )

    private val pendingRestore by lazy { PendingRestore(backupManager) }

    fun onboarding() = OnboardingViewModel(OnboardingUseCase(security, backupManager, pendingRestore), documents)

    fun authSetup() = AuthSetupViewModel(security, sessionLock, pendingRestore)

    fun editor(noteId: Long = -1L, copySource: Long = -1L, readTrashOnly: Boolean = false) = EditNoteViewModel(
        notesRepository = notes,
        savedStateHandle = SavedStateHandle(
            mapOf("idNote" to noteId, "copySource" to copySource, "isReadTrashOnly" to readTrashOnly),
        ),
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(notes),
        settingsRepository = settings,
        appScope = appScope,
        // Main, not a worker: Compose tests run effects on an unconfined dispatcher, so an exit
        // (save, then pop) would otherwise resume on the worker and navigate from it.
        ioDispatcher = Dispatchers.Main,
        copyTitle = { appContext.getString(com.pasich.encly.R.string.untitled) + " (Copy)" },
    )

    /** The ViewModels the home screen and its drawer, chips and task card ask for. */
    fun home(noteList: NoteListViewModel = noteList()): Array<androidx.lifecycle.ViewModel> = arrayOf(
        mainListState(),
        settingsVm(),
        noteList,
        search(),
        lockNow(),
        tagList(),
        tasksVm(),
        statistics(),
    )
}
