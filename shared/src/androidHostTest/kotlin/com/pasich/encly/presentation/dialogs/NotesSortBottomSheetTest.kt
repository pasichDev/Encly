package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.sort_by
import com.pasich.encly.core.resources.view_switch_to_grid
import com.pasich.encly.core.resources.view_switch_to_list
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.ui.screens.ComposeScreenTest
import com.pasich.encly.ui.screens.TestApp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list's view options: every sort order, the list/grid switch, and closing after a choice. */
@OptIn(ExperimentalMaterial3Api::class)
class NotesSortBottomSheetTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }
    private var dismissed = 0
    private var toggled = 0

    private fun show(visible: Boolean = true, grid: Boolean = false) {
        val viewModel = app.noteList()
        setScreen(viewModels(viewModel)) {
            NotesSortBottomSheet(
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismiss = { dismissed++ },
                isGrid = grid,
                onToggleView = { toggled++ },
                isBottomSheetVisible = visible,
                noteListViewModel = viewModel,
            )
        }
    }

    @Test
    fun aHiddenSheetShowsNothing() {
        show(visible = false)

        assertEquals(0, countText(str(Res.string.sort_by)))
    }

    @Test
    fun theSheetListsEverySortOrder() {
        show()

        assertEquals(1, countText(str(Res.string.sort_by)))
        NoteSortOption.entries.forEach { assertEquals(it.name, 1, countText(str(it.labelRes))) }
    }

    @Test
    fun choosingASortOrderStoresItAndClosesTheSheet() {
        show()

        rule.onNodeWithText(str(NoteSortOption.UPDATED_ASC.labelRes)).performClick()

        waitFor { app.settings.sort.value == NoteSortOption.UPDATED_ASC }
        assertEquals(1, dismissed)
        assertEquals(0, toggled)
    }

    @Test
    fun aListOffersTheSwitchToTheGridAndTheOtherWayAround() {
        show(grid = false)
        assertEquals(1, countText(str(Res.string.view_switch_to_grid)))
        assertEquals(0, countText(str(Res.string.view_switch_to_list)))

        rule.onNodeWithText(str(Res.string.view_switch_to_grid)).performClick()

        assertEquals(1, toggled)
        assertEquals(1, dismissed)
    }

    @Test
    fun aGridOffersTheSwitchBackToTheList() {
        show(grid = true)

        assertEquals(1, countText(str(Res.string.view_switch_to_list)))
        assertEquals(0, countText(str(Res.string.view_switch_to_grid)))
    }
}
