package com.pasich.encly.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.settings_editor_toolbar
import com.pasich.encly.core.resources.toolbar_move_down
import com.pasich.encly.core.resources.toolbar_move_up
import com.pasich.encly.core.resources.toolbar_reset
import com.pasich.encly.core.resources.toolbar_section_hidden
import com.pasich.encly.core.resources.toolbar_section_main
import com.pasich.encly.core.resources.toolbar_section_more
import com.pasich.encly.core.resources.toolbar_tool_hide
import com.pasich.encly.core.resources.toolbar_tool_show
import com.pasich.encly.domain.model.ToolbarLayout
import com.pasich.encly.domain.model.ToolbarTool
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.settings.AppearanceScreen
import com.pasich.encly.presentation.screen.settings.EditorToolbarScreen
import com.pasich.encly.presentation.viewmodel.EditorToolbarViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun show() {
        setNavScreen(viewModels(EditorToolbarViewModel(app.editorToolbar)), route = NavRoutes.EditorToolbarRoute.name) {
            EditorToolbarScreen(it)
        }
    }

    private val layout get() = app.editorToolbar.layout.value

    private fun hasMoveActions() = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    private fun customAction(row: Int, label: String) {
        val node = rule.onAllNodes(hasMoveActions())[row].fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    @Test
    fun itListsTheThreeSectionsAndEveryTool() {
        show()

        listOf(Res.string.toolbar_section_main, Res.string.toolbar_section_more, Res.string.toolbar_section_hidden)
            .forEach { rule.onNodeWithText(str(it)).assertExists() }
        assertEquals(ToolbarTool.entries.size, rule.onAllNodes(hasMoveActions()).fetchSemanticsNodes().size)
    }

    @Test
    fun theEyeHidesAToolAndBringsItBack() {
        show()

        rule.onAllNodesWithContentDescription(str(Res.string.toolbar_tool_hide))[0].performClick()
        rule.waitForIdle()
        assertEquals(listOf(ToolbarTool.HEADING), layout.hidden)

        rule.onAllNodes(hasContentDescription(str(Res.string.toolbar_tool_show)))[0].performScrollTo().performClick()
        rule.waitForIdle()
        // Back in its section, at the end of it.
        assertEquals(ToolbarTool.HEADING, layout.main.last())
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun resetRestoresTheShippedToolbarAndIsOfferedOnlyAfterAChange() {
        show()
        rule.onNodeWithText(str(Res.string.toolbar_reset)).performClick()
        assertEquals(ToolbarLayout.DEFAULT, layout)

        rule.onAllNodesWithContentDescription(str(Res.string.toolbar_tool_hide))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(Res.string.toolbar_reset)).performClick()
        rule.waitForIdle()

        assertEquals(ToolbarLayout.DEFAULT, layout)
    }

    @Test
    fun aToolMovesDownAndUpForTalkBack() {
        show()

        customAction(row = 0, label = str(Res.string.toolbar_move_down))
        assertEquals(ToolbarTool.LIST_CHECK, layout.main.first())

        customAction(row = 1, label = str(Res.string.toolbar_move_up))
        assertEquals(ToolbarTool.HEADING, layout.main.first())
    }

    @Test
    fun draggingAToolByItsHandleMovesItThroughTheList() {
        show()

        rule.onAllNodes(hasMoveActions())[0].performTouchInput {
            down(Offset(HANDLE_X, center.y))
            moveBy(Offset(0f, ROWS_DOWN * height))
            up()
        }
        rule.waitForIdle()

        assertEquals(2, layout.main.indexOf(ToolbarTool.HEADING))
    }

    @Test
    fun appearanceOpensTheEditorToolbar() {
        setNavScreen(
            viewModels(app.appearance()),
            route = NavRoutes.AppearanceRoute.name,
            liveTheme = app.settings.theme,
        ) {
            AppearanceScreen(it)
        }

        scrollToText(str(Res.string.settings_editor_toolbar))
        rule.onNodeWithText(str(Res.string.settings_editor_toolbar)).performClick()

        waitFor { currentRoute() == NavRoutes.EditorToolbarRoute.name }
    }

    private companion object {
        const val HANDLE_X = 30f
        const val ROWS_DOWN = 2f
    }
}
