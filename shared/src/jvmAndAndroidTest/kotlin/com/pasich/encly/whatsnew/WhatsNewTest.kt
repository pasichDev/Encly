package com.pasich.encly.whatsnew

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.PurgeExpiredTrashUseCase
import com.pasich.encly.domain.usecase.note.TRASH_RETENTION_MILLIS
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.presentation.viewmodel.StartupViewModel
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

/** "What's new" after an update: shown once per version, never on a fresh install, in the app's language. */
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewTest {
    private val flags = InMemoryKeyValueStore()

    private fun app(version: String) = object : AppInfo {
        override val appName = "Encly"
        override val versionName = version
        override val donationsEnabled = false
        override val storeRatingEnabled = false
        override val storePageLinks = emptyList<String>()
    }

    private fun tracker(version: String) = WhatsNewTracker(flags, app(version))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun anUpdateIsDueOnceAndThenNot() {
        tracker("2.3.0").recordFreshInstall()

        assertTrue(tracker("2.4.0").takeUnseen())
        assertFalse(tracker("2.4.0").takeUnseen())
        assertTrue(tracker("2.5.0").takeUnseen())
    }

    @Test
    fun aFreshInstallStartsOnThisVersionAndIsNeverDue() {
        tracker("2.4.0").recordFreshInstall()

        assertFalse(tracker("2.4.0").takeUnseen())
        assertFalse(tracker("2.4.0").takeUnseen())
    }

    @Test
    fun recordingAFreshInstallKeepsAVersionAlreadyRecorded() {
        tracker("2.3.0").recordFreshInstall()
        tracker("2.4.0").recordFreshInstall()

        assertTrue("the older recorded version still counts as the one before", tracker("2.4.0").takeUnseen())
    }

    @Test
    fun anInstallFromBeforeTheFeatureHasNothingRecordedAndIsShownTheUpdate() {
        assertTrue(tracker("2.4.0").takeUnseen())
        assertFalse(tracker("2.4.0").takeUnseen())
    }

    @Test
    fun aDowngradeIsNotAnUpdateButIsRemembered() {
        tracker("2.4.0").recordFreshInstall()

        assertFalse(tracker("2.3.0").takeUnseen())
        assertTrue("the way back up is an update again", tracker("2.4.0").takeUnseen())
    }

    @Test
    fun aBuildSuffixIsNotAnotherVersionNumber() {
        tracker("2.4.0").recordFreshInstall()

        assertFalse(tracker("2.4.0-debug").takeUnseen())
        assertTrue(tracker("2.4.1-debug").takeUnseen())
    }

    @Test
    fun aVersionNameThatIsNotANumberCountsAsNew() {
        tracker("dev").recordFreshInstall()

        assertTrue(tracker("2.4.0").takeUnseen())
    }

    @Test
    fun everyAppLanguageHasItsStoreFolder() {
        val folders = mapOf(
            "en" to "en-US",
            "uk" to "uk",
            "de" to "de-DE",
            "fr" to "fr-FR",
            "es" to "es-ES",
            "it" to "it-IT",
            "pl" to "pl-PL",
            "pt" to "pt-PT",
            "nl" to "nl-NL",
            "ar" to "ar",
        )
        folders.forEach { (tag, folder) -> assertEquals(tag, folder, Changelogs.folderFor(tag)) }
        assertEquals("pt-PT", Changelogs.folderFor("pt-BR"))
        assertEquals("uk", Changelogs.folderFor("uk_UA"))
    }

    @Test
    fun anUnsupportedOrMissingLanguageReadsEnglish() {
        assertEquals("en-US", Changelogs.folderFor("ja-JP"))
        assertEquals("en-US", Changelogs.folderFor("he"))
        assertEquals("en-US", Changelogs.folderFor(null))
        assertEquals("en-US", Changelogs.folderFor(""))
    }

    @Test
    fun theNotesAreInTheAppsLanguageElseEnglish() {
        val bundled = mapOf(
            "en-US" to "Encly 2.4.0\n• English change",
            "de-DE" to "Encly 2.4.0\n• Deutsche Änderung",
        )

        assertEquals("• Deutsche Änderung", Changelogs.notes("de", bundled)?.body)
        // French has no file for this version, Japanese is not an app language: both read English.
        assertEquals("• English change", Changelogs.notes("fr", bundled)?.body)
        assertEquals("• English change", Changelogs.notes("ja", bundled)?.body)
        assertEquals("Encly 2.4.0", Changelogs.notes("de", bundled)?.heading)
    }

    @Test
    fun noNotesAtAllMeansNothingToShow() {
        assertNull(Changelogs.notes("en", emptyMap()))
        assertNull(Changelogs.notes("en", mapOf("en-US" to "  \n ")))
        assertEquals("", Changelogs.notes("en", mapOf("en-US" to "Encly 2.4.0"))?.body)
    }

    @Test
    fun theBundledNotesAreTheStoreChangelogOfThisVersion() {
        val root = listOf(File(".."), File(".")).first { File(it, "version.properties").isFile }
        val version = Properties().apply { File(root, "version.properties").inputStream().use { load(it) } }
        fun part(name: String) = version.getProperty(name).trim().toInt()
        val code = part("VERSION_MAJOR") * 10_000 + part("VERSION_MINOR") * 100 + part("VERSION_PATCH")
        assertEquals(code, BundledChangelogs.VERSION_CODE)

        val expected = File(root, "fastlane/metadata/android").listFiles().orEmpty()
            .map { File(it, "changelogs/$code.txt") }
            .filter { it.isFile }
            .associate { it.parentFile.parentFile.name to it.readText().trim() }
        assertEquals(expected, BundledChangelogs.notes)
        // Every language of the app has its notes for the version, since the store has them.
        assertEquals(10, BundledChangelogs.notes.size)
        assertNotNull(Changelogs.current("uk"))
        assertTrue(BundledChangelogs.notes.getValue("uk").startsWith(Changelogs.current("uk")!!.heading))
    }

    private fun startup(version: String, repository: TestNotesRepository = TestNotesRepository()) = StartupViewModel(
        tracker(version),
        PurgeExpiredTrashUseCase(repository),
    )

    @Test
    fun theUpdateSheetIsDueOnceAfterTheUnlockAndGoesWhenDismissed() {
        startup("2.3.0").onAppStart(freshInstall = true)

        val after = startup("2.4.0")
        assertNull("nothing before the vault is unlocked", after.whatsNewDue.value)
        after.onUnlocked("en", bundled = mapOf("en-US" to "Encly 2.4.0\n• A change"))
        assertEquals("• A change", after.whatsNewDue.value?.body)

        after.dismissWhatsNew()
        assertNull(after.whatsNewDue.value)
        // The next unlock of the same version, here a fresh ViewModel, has nothing to show.
        val again = startup("2.4.0")
        again.onUnlocked("en", bundled = mapOf("en-US" to "Encly 2.4.0\n• A change"))
        assertNull(again.whatsNewDue.value)
    }

    @Test
    fun aFreshInstallIsShownNothingAfterItsFirstUnlock() {
        val viewModel = startup("2.4.0")
        viewModel.onAppStart(freshInstall = true)

        viewModel.onUnlocked("en", bundled = mapOf("en-US" to "Encly 2.4.0\n• A change"))

        assertNull(viewModel.whatsNewDue.value)
    }

    @Test
    fun aRestartThatIsNotAFreshInstallDoesNotHideTheUpdate() {
        startup("2.3.0").onAppStart(freshInstall = true)

        val viewModel = startup("2.4.0")
        viewModel.onAppStart(freshInstall = false)
        viewModel.onUnlocked("en", bundled = mapOf("en-US" to "Encly 2.4.0\n• A change"))

        assertNotNull(viewModel.whatsNewDue.value)
    }

    @Test
    fun aVersionWithoutNotesShowsNoSheet() {
        startup("2.3.0").onAppStart(freshInstall = true)
        val viewModel = startup("2.4.0")

        viewModel.onUnlocked("en", bundled = emptyMap())

        assertNull(viewModel.whatsNewDue.value)
    }

    @Test
    fun unlockingCleansTheTrashOfExpiredNotesOnce() {
        val repository = TestNotesRepository()
        val now = System.currentTimeMillis()
        repository.trashNotes.value = listOf(
            Note(id = 1, isTrash = true, trashedAt = now - TRASH_RETENTION_MILLIS - 60_000),
            Note(id = 2, isTrash = true, trashedAt = now),
        )
        val viewModel = startup("2.4.0", repository)

        viewModel.onUnlocked("en", bundled = emptyMap())
        viewModel.onUnlocked("en", bundled = emptyMap())

        assertEquals(listOf(1L), repository.deletedIds)
    }
}
