package com.pasich.encly.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.close
import com.pasich.encly.core.resources.dialog_dismiss
import com.pasich.encly.core.resources.main_drawer_settings
import com.pasich.encly.core.resources.main_drawer_tasks
import com.pasich.encly.core.resources.note_open
import com.pasich.encly.core.resources.note_open_in_new_tab
import com.pasich.encly.core.resources.open_menu
import com.pasich.encly.core.resources.pane_select_note
import com.pasich.encly.core.resources.sort_by
import com.pasich.encly.core.resources.swipe_choice_delete
import com.pasich.encly.core.resources.swipe_delete_title
import com.pasich.encly.core.resources.view_options
import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.presentation.dialogs.SHEET_DIALOG_TAG
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.pane.CenteredContent
import com.pasich.encly.presentation.pane.NOTE_TAB_TAG
import com.pasich.encly.presentation.pane.NavigationPage
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import com.pasich.encly.presentation.viewmodel.NoteTabsViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.LocalPointerFirst
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * The layout at each window size: the phone layout at compact (the default of every other test), a rail at
 * medium, and at expanded the permanent drawer with the notes list beside the open notes, whose tabs are
 * opened, switched and closed. The window is 1280 dp wide; the class each test lays out in is provided
 * directly, as the app does from the window's measured size.
 */
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
class AdaptiveLayoutTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    /** The editors the detail pane makes: each reads its note from its own destination's arguments. */
    private inner class PaneFactory : MetroViewModelFactory() {
        override val viewModelProviders =
            mapOf<KClass<out androidx.lifecycle.ViewModel>, () -> androidx.lifecycle.ViewModel>(
                TagListViewModel::class to { app.tagList() },
                MarkdownExportViewModel::class to { app.markdownExport() },
            )

        override val assistedFactoryProviders =
            mapOf<KClass<out androidx.lifecycle.ViewModel>, () -> ViewModelAssistedFactory>(
                EditNoteViewModel::class to {
                    object : ViewModelAssistedFactory {
                        override fun create(extras: CreationExtras): androidx.lifecycle.ViewModel {
                            val args = extras.createSavedStateHandle()
                            return app.editor(
                                noteId = args["idNote"] ?: -1L,
                                copySource = args["copySource"] ?: -1L,
                            )
                        }
                    }
                },
            )
    }

    private fun note(id: Long, title: String, text: String = "") = Note(
        id = id,
        title = title,
        value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow(text)))),
        date = 1_700_000_000_000 + id,
    )

    /** Whether the window is driven by a mouse first, as the desktop is; a phone or tablet is not. */
    private var pointerFirst = false

    private fun showHome(widthClass: WidthClass, vararg notes: Note) {
        app.withNotes(*notes)
        setNavScreen(viewModels(*app.home(), NoteTabsViewModel()), route = NavRoutes.HomeRoute.name) { nav ->
            CompositionLocalProvider(
                LocalWidthClass provides widthClass,
                LocalPointerFirst provides pointerFirst,
                LocalMetroViewModelFactory provides PaneFactory(),
            ) { WithShortcuts { MainRootScreen(nav) } }
        }
    }

    private fun listCard(title: String) =
        rule.onNode(hasText(title) and hasClickAction() and !isTab and !hasSetTextAction())

    private val isTab = hasTestTag(NOTE_TAB_TAG)

    private fun tabs(title: String? = null) = rule.onAllNodes(
        if (title == null) isTab else isTab and hasText(title),
    ).fetchSemanticsNodes()

    // Selected through its click action: a tap in the middle of a small tab could land on its close button.
    private fun selectTab(title: String) = rule.onNode(isTab and hasText(title))
        .performSemanticsAction(SemanticsActions.OnClick)

    private fun closeButton(title: String) = rule.onNode(
        hasContentDescription("${str(Res.string.close)} $title") and hasClickAction(),
    )

    private fun count(description: String) =
        rule.onAllNodes(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun openFromList(title: String) {
        rule.onNode(hasText(title) and hasClickAction()).performClick()
        waitFor { tabs(title).isNotEmpty() }
    }

    @Test
    fun aCompactWindowKeepsTheDrawerButtonAndHasNoRailOrPane() {
        showHome(WidthClass.Compact, note(1, "Trip"))

        assertEquals(1, count(str(Res.string.open_menu)))
        assertEquals(0, count(str(Res.string.main_drawer_tasks)))
        assertEquals(0, countText(str(Res.string.pane_select_note)))
    }

    @Test
    fun aMediumWindowHasARailInsteadOfTheDrawerButton() {
        showHome(WidthClass.Medium, note(1, "Trip"))

        assertEquals(0, count(str(Res.string.open_menu)))
        assertTrue(count(str(Res.string.main_drawer_tasks)) > 0)
        assertEquals(0, countText(str(Res.string.pane_select_note)))
        assertEquals(1, countText("Trip"))
    }

    @Test
    fun aMediumWindowOpensANoteFullScreenLikeAPhone() {
        showHome(WidthClass.Medium, note(1, "Trip"))

        rule.onNode(hasText("Trip") and hasClickAction()).performClick()
        waitFor { currentRouteName() == NavRoutes.EditNoteRoute.name }
    }

    @Test
    fun anExpandedWindowShowsTheDrawerTheNotesAndAnEmptyPane() {
        showHome(WidthClass.Expanded, note(1, "Trip"))

        assertEquals(0, count(str(Res.string.open_menu)))
        assertTrue(countText(str(Res.string.main_drawer_tasks)) > 0)
        assertEquals(1, countText("Trip"))
        assertEquals(1, countText(str(Res.string.pane_select_note)))
        assertTrue(tabs().isEmpty())
    }

    @Test
    fun pickingANoteOpensItInTheDetailPaneAndStaysOnTheNotesScreen() {
        showHome(WidthClass.Expanded, note(1, "Trip", "body"))

        openFromList("Trip")

        assertEquals(NavRoutes.HomeRoute.name, currentRouteName())
        assertEquals(0, countText(str(Res.string.pane_select_note)))
        // The list row, the tab and the editor's title field.
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, tabs("Trip").size)
    }

    @Test
    fun theShownNoteIsRecordedOnTheHomeEntryForARelockReturn() {
        showHome(WidthClass.Expanded, note(5, "Trip"))

        openFromList("Trip")

        waitFor {
            rule.runOnIdle {
                nav.getBackStackEntry(NavRoutes.HomeRoute.name).savedStateHandle.get<Long>(RelockReturn.OPEN_NOTE_ID)
            } == 5L
        }
    }

    @Test
    fun twoNotesAreTwoTabsAndAClickOnATabShowsItsNote() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))

        openFromList("Trip")
        openFromList("Plan")

        assertEquals(2, tabs().size)
        waitFor { rule.onAllNodes(hasText("Plan") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }

        selectTab("Trip")
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, rule.onAllNodes(hasText("Plan") and hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(2, tabs().size)
    }

    @Test
    fun anOpenNoteIsNotOpenedTwice() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))

        openFromList("Trip")
        openFromList("Plan")
        rule.onNode(hasText("Trip") and hasClickAction() and !isTab).performClick()
        rule.waitForIdle()

        assertEquals(2, tabs().size)
    }

    @Test
    fun leavingATabSavesItsEdits() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))
        openFromList("Trip")
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText("Trip") and hasSetTextAction())[0].performTextInput(" two")

        openFromList("Plan")

        waitFor { app.notes.updatedNotes.any { it.id == 1L && it.title.contains("two") } }
    }

    @Test
    fun closingTheShownTabLeavesTheEmptyPaneWhenItWasTheLastOne() {
        showHome(WidthClass.Expanded, note(1, "Trip"))
        openFromList("Trip")

        closeButton("Trip").performClick()

        waitFor { tabs().isEmpty() }
        waitForText(str(Res.string.pane_select_note))
    }

    @Test
    fun closingTheShownTabShowsTheOtherOne() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))
        openFromList("Trip")
        openFromList("Plan")

        closeButton("Plan").performClick()

        waitFor { tabs().size == 1 }
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun closingABackgroundTabKeepsTheShownNote() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))
        openFromList("Trip")
        openFromList("Plan")

        closeButton("Trip").performClick()

        waitFor { tabs().size == 1 }
        assertEquals(1, tabs("Plan").size)
    }

    @Test
    fun viewOptionsAreABottomSheetOnAPhone() {
        showHome(WidthClass.Compact)
        rule.onNodeWithContentDescription(str(Res.string.view_options)).performClick()
        waitForText(str(Res.string.sort_by))
        assertEquals(0, rule.onAllNodesWithTag(SHEET_DIALOG_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun viewOptionsAreADialogInAnExpandedWindow() {
        showHome(WidthClass.Expanded)
        rule.onNodeWithContentDescription(str(Res.string.view_options)).performClick()
        waitForText(str(Res.string.sort_by))
        rule.onNodeWithTag(SHEET_DIALOG_TAG).assertWidthIsEqualTo(480.dp)
    }

    @Test
    fun aPageIsHeldToItsMaximumWidthInAWideWindowAndFillsAPhone() {
        fun show(widthClass: WidthClass) = setScreen {
            CompositionLocalProvider(LocalWidthClass provides widthClass) {
                CenteredContent { Box(Modifier.fillMaxSize().testTag("page")) }
            }
        }
        show(WidthClass.Expanded)
        rule.onNodeWithTag("page").assertWidthIsEqualTo(720.dp)
    }

    @Test
    fun aPageFillsTheWindowOfAPhone() {
        setScreen {
            CompositionLocalProvider(LocalWidthClass provides WidthClass.Compact) {
                CenteredContent { Box(Modifier.fillMaxSize().testTag("page")) }
            }
        }
        rule.onNodeWithTag("page").assertWidthIsEqualTo(1280.dp)
    }

    private fun showPage(widthClass: WidthClass) {
        setNavScreen(
            viewModels(app.statistics()),
            route = NavRoutes.SettingsRoute.name,
            start = NavRoutes.HomeRoute.name,
        ) { nav ->
            CompositionLocalProvider(LocalWidthClass provides widthClass) {
                NavigationPage(nav, NavRoutes.SettingsRoute) { Text("the page") }
            }
        }
        navigateTo(NavRoutes.SettingsRoute.name)
    }

    @Test
    fun aPageHasNoNavigationOnAPhone() {
        showPage(WidthClass.Compact)

        waitForText("the page")
        assertEquals(0, countText(str(Res.string.main_drawer_settings)))
        assertEquals(0, count(str(Res.string.main_drawer_settings)))
    }

    @Test
    fun aPageHasTheDrawerInAnExpandedWindowAndNavigatesWithoutGrowingTheBackStack() {
        showPage(WidthClass.Expanded)
        // The drawer is on the page, wired to the app's navigation.
        waitForText("the page")
        rule.onNode(hasText(str(Res.string.main_drawer_tasks)) and hasClickAction()).performClick()
        waitFor { currentRouteName() == NavRoutes.TasksRoute.name }

        rule.runOnIdle { nav.popBackStack() }

        assertEquals(NavRoutes.HomeRoute.name, currentRouteName())
    }

    @Test
    fun aPageHasARailInAMediumWindow() {
        showPage(WidthClass.Medium)

        waitForText("the page")
        assertTrue(count(str(Res.string.main_drawer_settings)) > 0)
        rule.onNodeWithText("the page").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aRightClickOnANoteOffersOpenInNewTabAndItAddsABackgroundTab() {
        pointerFirst = true
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))
        openFromList("Trip")

        listCard("Plan").performMouseInput { rightClick() }
        waitForText(str(Res.string.note_open_in_new_tab))
        rule.onNodeWithText(str(Res.string.note_open_in_new_tab)).performClick()

        waitFor { tabs("Plan").isNotEmpty() }
        assertEquals(2, tabs().size)
        // The note being read stays on screen.
        waitFor { rule.onAllNodes(hasText("Trip") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aClickWithTheCommandKeyAddsABackgroundTabInsteadOfOpeningTheNote() {
        pointerFirst = true
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))
        openFromList("Trip")

        listCard("Plan").performKeyInput { keyDown(Key.CtrlLeft) }
        listCard("Plan").performMouseInput { click() }
        listCard("Plan").performKeyInput { keyUp(Key.CtrlLeft) }

        waitFor { tabs("Plan").isNotEmpty() }
        assertEquals(2, tabs().size)
        assertEquals(0, rule.onAllNodes(hasText("Plan") and hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun moveToTrashInTheRightClickMenuAsksFirst() {
        pointerFirst = true
        showHome(WidthClass.Expanded, note(1, "Trip"))
        waitFor { listCard("Trip").isDisplayedSafely() }

        listCard("Trip").performMouseInput { rightClick() }
        waitForText(str(Res.string.swipe_choice_delete))
        rule.onNodeWithText(str(Res.string.swipe_choice_delete)).performClick()

        waitForText(str(Res.string.swipe_delete_title))
        rule.onNodeWithText(str(Res.string.dialog_dismiss)).performClick()
        waitFor { countText(str(Res.string.swipe_delete_title)) == 0 }
        assertTrue(app.notes.updatedNotes.none { it.isTrash })
    }

    @Test
    fun theNoteShownInThePaneIsMarkedInTheList() {
        showHome(WidthClass.Expanded, note(1, "Trip"), note(2, "Plan"))

        openFromList("Trip")

        waitFor { listCard("Trip").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(false, listCard("Plan").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withoutAPointerARightClickOpensNoMenuAndTheListHasNoNewTabEntry() {
        showHome(WidthClass.Expanded, note(1, "Trip"))
        waitFor { listCard("Trip").isDisplayedSafely() }

        listCard("Trip").performMouseInput { rightClick() }
        rule.waitForIdle()

        assertEquals(0, countText(str(Res.string.note_open_in_new_tab)))
        assertEquals(0, countText(str(Res.string.note_open)))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aCompactWindowWithAMouseHasTheMenuButNoTabs() {
        pointerFirst = true
        showHome(WidthClass.Compact, note(1, "Trip"))
        waitFor { listCard("Trip").isDisplayedSafely() }

        listCard("Trip").performMouseInput { rightClick() }
        waitForText(str(Res.string.note_open))

        assertEquals(0, countText(str(Res.string.note_open_in_new_tab)))
        assertEquals(false, listCard("Trip").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    private fun SemanticsNodeInteraction.isDisplayedSafely() = runCatching { assertExists() }.isSuccess
}
