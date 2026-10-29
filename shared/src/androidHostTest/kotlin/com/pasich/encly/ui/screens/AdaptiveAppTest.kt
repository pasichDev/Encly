package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.empty_notes
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.open_menu
import com.pasich.encly.core.resources.pane_select_note
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.presentation.navigation.EnclyApp
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import com.pasich.encly.presentation.viewmodel.NoteTabsViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.presentation.viewmodel.ThemeViewModel
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * The whole app in windows of each size, the size measured from the window itself: a phone gets the phone
 * layout, a desktop window the expanded one, and a re-lock leaves nothing of the open notes on screen.
 */
class AdaptiveAppTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun note(id: Long, title: String) = Note(
        id = id,
        title = title,
        value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow("body")))),
        date = 1_700_000_000_000 + id,
    )

    private inner class AppFactory(private val tabs: NoteTabsViewModel) : MetroViewModelFactory() {
        override val viewModelProviders = (
            listOf<ViewModel>(ThemeViewModel(app.settings), app.lock(), tabs) + app.home()
            ).associate { it::class to { it } } + mapOf<KClass<out ViewModel>, () -> ViewModel>(
            TagListViewModel::class to { app.tagList() },
            MarkdownExportViewModel::class to { app.markdownExport() },
        )

        override val assistedFactoryProviders = mapOf<KClass<out ViewModel>, () -> ViewModelAssistedFactory>(
            EditNoteViewModel::class to {
                object : ViewModelAssistedFactory {
                    override fun create(extras: CreationExtras): ViewModel =
                        app.editor(noteId = extras.createSavedStateHandle()["idNote"] ?: -1L)
                }
            },
        )
    }

    private val tabs = NoteTabsViewModel()

    private fun show() {
        app.withNotes(note(1, "Trip"))
        `when`(app.security.isDatabaseUnlocked()).thenReturn(true)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        rule.setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides AppFactory(tabs)) {
                EnclyApp(InitialStatus.MAIN, app.security, app.sessionLock)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun aPhoneWindowGetsThePhoneLayout() {
        show()

        waitFor { rule.onAllNodes(hasText("Trip")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(Res.string.open_menu)).assertExists()
        assertEquals(0, countText(str(Res.string.pane_select_note)))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun aDesktopWindowGetsTheExpandedLayout() {
        show()

        waitForText(str(Res.string.pane_select_note))
        assertEquals(0, rule.onAllNodes(hasText(str(Res.string.open_menu))).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun aReLockWithNotesOpenInThePaneLeavesNothingOfThemOnScreenOrInMemory() {
        show()
        waitFor { rule.onAllNodes(hasText("Trip") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText("Trip") and hasClickAction())[0].performClick()
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, tabs.tabs.tabs.size)

        rule.runOnIdle { app.sessionLock.requireUnlock() }

        waitFor { countText(str(Res.string.lock_title)) > 0 }
        assertEquals(0, countText("Trip"))
        assertEquals(0, countText(str(Res.string.empty_notes)))
        assertEquals(0, countText(str(Res.string.pane_select_note)))
        assertTrue(tabs.tabs.tabs.isEmpty())
    }
}
