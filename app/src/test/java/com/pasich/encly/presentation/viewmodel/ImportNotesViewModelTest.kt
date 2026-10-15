package com.pasich.encly.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.backup.VaultSnapshot
import com.pasich.encly.data.importer.ImportFixtures
import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.data.importer.SafImportFiles
import com.pasich.encly.data.model.Tag
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Detect -> review -> import -> summary, with an in-memory vault. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportNotesViewModelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var security: SecurityManager
    private lateinit var sessionLock: SessionLockManager
    private lateinit var store: InMemoryVaultDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        security = mock(SecurityManager::class.java)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        sessionLock = SessionLockManager(security)
        sessionLock.onStart(mock(LifecycleOwner::class.java))
        store = InMemoryVaultDataStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vault: VaultDataStore = store) =
        ImportNotesViewModel(SafImportFiles(context), vault, sessionLock, dispatcher)

    private fun keep(title: String, text: String, created: Long, label: String? = null, trashed: Boolean = false) =
        "$title.json" to (
            """{"createdTimestampUsec":$created,"userEditedTimestampUsec":$created,"title":"$title",""" +
                """"textContent":"$text","isTrashed":$trashed""" +
                (label?.let { ""","labels":[{"name":"$it"}]""" } ?: "") + "}"
            )

    private val files = arrayOf(
        keep("One", "first", 1_000_000_000_000_000, label = "Work"),
        keep("Two", "second", 1_000_000_001_000_000, label = "Home"),
        keep("Three", "third", 1_000_000_002_000_000, trashed = true),
    )

    private fun ImportNotesViewModel.preview() = (step.value as ImportStep.Preview)

    @Test
    fun detectsPreviewsAndImportsEverythingNewByDefault() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))

        val preview = vm.preview()
        assertEquals(ImportSource.GOOGLE_KEEP, preview.preview.source)
        assertEquals(3, preview.preview.notes)
        assertEquals(3, preview.review.selected.size)
        assertEquals(1, preview.preview.trashed)
        // Nothing is written before the user confirms.
        assertTrue(store.notes.isEmpty())

        vm.confirm()

        val done = (vm.step.value as ImportStep.Done).result
        assertEquals(3, done.imported)
        assertEquals(0, done.duplicates)
        assertEquals(2, done.tagsAdded)
        assertEquals(listOf("One", "Two", "Three"), store.notes.map { it.title })
        assertEquals(listOf(false, false, true), store.notes.map { it.isTrash })
    }

    @Test
    fun theReviewListCarriesRowsWithoutTheirFullText() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))

        val rows = vm.preview().review.rows
        assertEquals(listOf("One", "Two", "Three"), rows.map { it.title })
        assertEquals("first", rows[0].snippet)
        assertEquals("Work", rows[0].tag)
        assertEquals(listOf(false, false, true), rows.map { it.trashed })
    }

    @Test
    fun anImportedFileImportedAgainIsAllDuplicatesAndNothingIsSelected() {
        viewModel().apply {
            begin(ImportFixtures.textSource(*files))
            confirm()
        }

        val again = viewModel()
        again.begin(ImportFixtures.textSource(*files))

        val review = again.preview().review
        assertEquals(3, again.preview().preview.duplicates)
        assertTrue(review.rows.all { it.existing })
        assertTrue(review.selected.isEmpty())
        // Duplicates cannot be ticked, and an empty selection imports nothing.
        again.toggle(review.rows.first().uid)
        assertTrue(again.preview().review.selected.isEmpty())
        again.confirm()
        assertTrue(again.step.value is ImportStep.Preview)
        assertEquals(3, store.notes.size)
    }

    @Test
    fun onlyTheSelectedNotesAndTheirTagsAreWritten() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))
        val uids = vm.preview().review.rows.associate { it.title to it.uid }

        vm.toggleAll()
        assertTrue(vm.preview().review.selected.isEmpty())
        vm.toggle(uids.getValue("Two"))
        vm.toggle(uids.getValue("Three"))
        assertEquals(setOf(uids.getValue("Two"), uids.getValue("Three")), vm.preview().review.selected)
        vm.toggle(uids.getValue("Three"))
        vm.confirm()

        val done = (vm.step.value as ImportStep.Done).result
        assertEquals(1, done.imported)
        assertEquals(2, done.notImported)
        assertEquals(listOf("Two"), store.notes.map { it.title })
        assertEquals(listOf("Home"), store.tags.map { it.nameTag })
    }

    @Test
    fun selectAllAndNoneToggleAndTheFilterOnlyChangesTheView() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))

        vm.toggleAll()
        assertTrue(vm.preview().review.selected.isEmpty())
        vm.toggleAll()
        assertEquals(3, vm.preview().review.selected.size)

        vm.setFilter(ReviewFilter.TRASH)
        assertEquals(listOf("Three"), vm.preview().review.visible.map { it.title })
        assertEquals(3, vm.preview().review.selected.size)
        vm.setFilter(ReviewFilter.NEW)
        assertEquals(3, vm.preview().review.visible.size)
    }

    @Test
    fun expandingARowShowsItsTextAndOnlyOneAtATime() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))
        val rows = vm.preview().review.rows

        vm.expand(rows[0].uid)
        assertEquals("first", vm.preview().review.expandedText)
        vm.expand(rows[1].uid)
        assertEquals("second", vm.preview().review.expandedText)
        vm.expand(rows[1].uid)
        assertEquals(null, vm.preview().review.expanded)
    }

    @Test
    fun aTagTheVaultHasIsReusedByName() {
        runBlocking { store.insertTag(Tag(nameTag = "work", position = 0, uid = "mine")) }
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))
        vm.confirm()

        assertEquals(listOf("work", "Home"), store.tags.map { it.nameTag })
        assertEquals(store.tags.first().id, store.notes.first { it.title == "One" }.tagId)
    }

    @Test
    fun cancelWritesNothingAndForgetsEverything() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))

        vm.cancel()

        assertEquals(ImportStep.Start, vm.step.value)
        vm.confirm()
        assertEquals(ImportStep.Start, vm.step.value)
        assertTrue(store.notes.isEmpty() && store.tags.isEmpty())
    }

    @Test
    fun aRelockClearsTheParsedNotesAndTheSelection() {
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))
        assertTrue(vm.step.value is ImportStep.Preview)

        sessionLock.requireUnlock()

        assertEquals(ImportStep.Start, vm.step.value)
        // Nothing is left to import, toggle or expand.
        vm.confirm()
        vm.toggleAll()
        vm.expand("anything")
        assertEquals(ImportStep.Start, vm.step.value)
        assertTrue(store.notes.isEmpty())
    }

    @Test
    fun aRelockWhileChoosingTheSourceClearsTheReadFiles() {
        val vm = viewModel()
        vm.begin(
            ImportFixtures.textSource(
                files[0],
                "n.md" to "---\ntitle: T\ncreated_at: 2024-01-01\nupdated_at: 2024-01-01\n---\nbody",
            ),
        )
        assertEquals(ImportStep.ChooseSource, vm.step.value)

        sessionLock.requireUnlock()
        vm.chooseSource(ImportSource.GOOGLE_KEEP)

        assertEquals(ImportStep.Start, vm.step.value)
    }

    @Test
    fun anAmbiguousSelectionAsksAndTheChoiceStillValidates() {
        val vm = viewModel()
        val md = "n.md" to "---\ntitle: T\ncreated_at: 2024-01-01\nupdated_at: 2024-01-01\n---\nbody"
        vm.begin(ImportFixtures.textSource(files[0], md))
        assertEquals(ImportStep.ChooseSource, vm.step.value)

        vm.chooseSource(ImportSource.JOPLIN)
        assertEquals(ImportStep.Failed(ImportFailure.UNSUPPORTED), vm.step.value)

        vm.cancel()
        vm.begin(ImportFixtures.textSource(files[0], md))
        vm.chooseSource(ImportSource.GOOGLE_KEEP)
        assertEquals(1, vm.preview().preview.notes)
    }

    @Test
    fun encryptedAndUnsupportedSelectionsFailWithGuidance() {
        val sn = """{"version":"004","items":[],"keyParams":{}}"""
        viewModel().apply {
            begin(ImportFixtures.textSource("backup.json" to sn))
            assertEquals(ImportStep.Failed(ImportFailure.ENCRYPTED_STANDARD_NOTES), step.value)
        }
        viewModel().apply {
            begin(ImportFixtures.textSource("my.nnbackup" to "x"))
            assertEquals(ImportStep.Failed(ImportFailure.ENCRYPTED_NOTESNOOK), step.value)
        }
        viewModel().apply {
            begin(ImportFixtures.textSource("data.json" to """{"a":1}"""))
            assertEquals(ImportStep.Failed(ImportFailure.UNSUPPORTED), step.value)
        }
        viewModel().apply {
            begin(ImportFixtures.textSource())
            assertEquals(ImportStep.Failed(ImportFailure.NOTHING_FOUND), step.value)
        }
        assertTrue(store.notes.isEmpty())
    }

    @Test
    fun aSelectionOverTheLimitsIsTooLarge() {
        val big = ByteArray(5 * 1024 * 1024)
        val zip = ImportFixtures.zip(*(1..16).map { "f$it.md" to big }.toTypedArray())
        val vm = viewModel()
        vm.begin(ImportFixtures.source("bomb.zip" to zip))
        assertEquals(ImportStep.Failed(ImportFailure.TOO_LARGE), vm.step.value)
    }

    @Test
    fun aFailedTransactionLeavesTheVaultUntouched() {
        store.failOnInsert = 3
        val vm = viewModel()
        vm.begin(ImportFixtures.textSource(*files))

        vm.confirm()

        assertEquals(ImportStep.Failed(ImportFailure.IMPORT), vm.step.value)
        assertTrue(store.notes.isEmpty() && store.tags.isEmpty())
    }

    @Test
    fun aVaultThatCannotBeBoundFailsBeforeReading() {
        val locked = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = error("Vault locked")
        }
        val vm = viewModel(locked)
        vm.begin(ImportFixtures.textSource(*files))
        assertEquals(ImportStep.Failed(ImportFailure.VAULT), vm.step.value)
    }

    @Test
    fun aUnreadableFileIsReportedNotFatal() {
        val vm = viewModel()
        vm.begin(
            ImportFixtures.source(
                files[0].first to files[0].second.toByteArray(),
                "bad.json" to byteArrayOf(0x7b, 0xff.toByte()),
            ),
        )
        assertEquals(1, vm.preview().preview.filesSkipped)
        vm.confirm()
        assertEquals(1, (vm.step.value as ImportStep.Done).result.notImported)
    }

    @Test
    fun theStartPageIsTheInitialStepAndThePickerGraceIsAskedFor() {
        val vm = viewModel()
        assertEquals(ImportStep.Start, vm.step.value)
        vm.onPickerLaunched()
        vm.onPickerUnavailable()
        assertEquals(ImportStep.Failed(ImportFailure.NO_PICKER), vm.step.value)
    }

    @Test
    fun aReadCancelledWhileItRunsLeavesNoPlanOrReadFilesBehind() {
        lateinit var vm: ImportNotesViewModel
        val cancelling = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = this

            override suspend fun snapshot(): VaultSnapshot {
                vm.cancel() // the user taps Cancel while the notes are being looked at
                return store.snapshot()
            }
        }
        // The read runs on its own dispatcher, so begin() has stored its job before Cancel arrives.
        val io = StandardTestDispatcher()
        vm = ImportNotesViewModel(SafImportFiles(context), cancelling, sessionLock, io)

        vm.begin(ImportFixtures.textSource(*files))
        io.scheduler.advanceUntilIdle()

        assertEquals(ImportStep.Start, vm.step.value)
        assertNull(held(vm, "plan"))
        assertNull(held(vm, "pending"))
    }

    private fun held(vm: ImportNotesViewModel, name: String): Any? =
        ImportNotesViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm)
}
