package com.pasich.encly.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.pasich.encly.R
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.ui.theme.colorSchemeFor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** The busiest screens in each of the five themes, light and dark: they render in that theme. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ThemedScreensTest(private val palette: ThemePalette, private val dark: Boolean) : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private val settings get() = ThemeSettings(palette = palette)
    private var surface: Color = Color.Unspecified

    private fun assertThemed() {
        assertEquals(colorSchemeFor(palette, dark || palette.isAlwaysDark).surface, surface)
        assertBodyNotEmpty()
    }

    private val probe: @androidx.compose.runtime.Composable () -> Unit = {
        val shown = MaterialTheme.colorScheme.surface
        SideEffect { surface = shown }
    }

    @Test
    fun notes() {
        app.withNotes(
            Note(id = 1, title = "Groceries", value = BlockConverter.blocksToJson(listOf(text("milk"))), date = 1L),
            Note(id = 2, title = "Trip", value = BlockConverter.blocksToJson(listOf(text("tickets"))), date = 2L),
        )
        app.withTasks(Task(id = 1, title = "Pay rent", priority = 2))
        setNavScreen(
            viewModels(*app.home()),
            route = NavRoutes.HomeRoute.name,
            settings = settings,
            dark = dark,
        ) { nav ->
            probe()
            MainRootScreen(nav)
        }
        waitForText("Groceries")
        assertThemed()
    }

    @Test
    fun editor() {
        Note(
            id = 5,
            title = "Plan",
            value = BlockConverter.blocksToJson(
                listOf(
                    Block.HBlock(MutableStateFlow("Heading"), blockType = BlockType.H2),
                    text("A paragraph"),
                    Block.ListBlock(
                        MutableStateFlow(listOf(ItemListBlock("done", true))),
                        blockType = BlockType.LIST_CHECK,
                    ),
                    Block.QuoteBlock(MutableStateFlow("A quote")),
                    Block.SeparatorBlock(),
                ),
            ),
            date = 1L,
        ).also { app.withNotes(it) }
        setNavScreen(
            viewModels(app.editor(noteId = 5), app.tagList()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
            settings = settings,
            dark = dark,
        ) { nav ->
            probe()
            EditNoteScreen(nav)
        }
        navigateTo("${NavRoutes.EditNoteRoute.name}/5")
        waitForText("A quote")
        assertThemed()
    }

    @Test
    fun tasks() {
        app.withTasks(Task(id = 1, title = "Pay rent", priority = 2), Task(id = 2, title = "Done", isCompleted = true))
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS, settings = settings, dark = dark) { nav ->
            probe()
            TasksScreen(nav)
        }
        waitForText("Pay rent")
        assertThemed()
    }

    @Test
    fun lock() {
        setNavScreen(
            viewModels(app.lock()),
            route = NavRoutes.LockRoute.name,
            settings = settings,
            dark = dark,
        ) { nav ->
            probe()
            LockScreen(nav)
        }
        waitForText(str(R.string.lock_title))
        assertThemed()
    }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun themes(): List<Array<Any>> = ThemePalette.entries.flatMap { palette ->
            listOf(false, true).map { dark -> arrayOf<Any>(palette, dark) }
        }
    }
}
