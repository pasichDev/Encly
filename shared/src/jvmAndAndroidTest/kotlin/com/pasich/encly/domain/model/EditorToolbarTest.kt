package com.pasich.encly.domain.model

import com.pasich.encly.core.backup.BackupSettings
import com.pasich.encly.presentation.viewmodel.EditorToolbarViewModel
import com.pasich.encly.testutil.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarTest {
    private val default = ToolbarLayout.DEFAULT

    private fun ToolbarLayout.everyToolOnce() {
        val all = main + more + hidden
        assertEquals(ToolbarTool.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun theDefaultLayoutHoldsEveryToolOnceWithCodeInTheRowAndTheTableBehindTheChevron() {
        default.everyToolOnce()
        assertTrue(ToolbarTool.CODE in default.main)
        assertTrue(ToolbarTool.TABLE in default.more)
        assertEquals(ToolbarSection.MAIN, default.sectionOf(ToolbarTool.QUOTE))
        assertEquals(ToolbarSection.MORE, default.sectionOf(ToolbarTool.LINK))
        assertEquals(ToolbarSection.HIDDEN, default.toggled(ToolbarTool.LINK).sectionOf(ToolbarTool.LINK))
        assertEquals(default.main, default.tools(ToolbarSection.MAIN))
        assertEquals(default.more, default.tools(ToolbarSection.MORE))
        assertEquals(emptyList<ToolbarTool>(), default.tools(ToolbarSection.HIDDEN))
    }

    @Test
    fun aToolIsHiddenAndComesBackWhereItIsByDefault() {
        val hidden = default.toggled(ToolbarTool.QUOTE).toggled(ToolbarTool.LINK)
        hidden.everyToolOnce()
        assertEquals(listOf(ToolbarTool.QUOTE, ToolbarTool.LINK), hidden.hidden)

        val back = hidden.toggled(ToolbarTool.QUOTE).toggled(ToolbarTool.LINK)
        assertEquals(ToolbarTool.QUOTE, back.main.last())
        assertEquals(ToolbarTool.LINK, back.more.last())
        assertTrue(back.hidden.isEmpty())
    }

    @Test
    fun movingATrailsDownTheListAndCrossesIntoTheNextSection() {
        val down = default.moved(ToolbarTool.HEADING, 1)
        assertEquals(ToolbarTool.LIST_CHECK, down.main.first())
        assertEquals(1, down.main.indexOf(ToolbarTool.HEADING))

        // The last tool of the row, one place on, is the first behind the chevron.
        val across = default.moved(ToolbarTool.CODE, 1)
        assertEquals(ToolbarSection.MORE, across.sectionOf(ToolbarTool.CODE))
        assertEquals(ToolbarTool.CODE, across.more.first())

        // The first tool behind the chevron, one place up, is the last of the row.
        val up = default.moved(ToolbarTool.TEXT, -1)
        assertEquals(ToolbarTool.TEXT, up.main.last())

        // All the way to the end is hidden; all the way to the start is the first tool of the row.
        assertEquals(ToolbarSection.HIDDEN, default.moved(ToolbarTool.HEADING, 99).sectionOf(ToolbarTool.HEADING))
        assertEquals(ToolbarTool.SEPARATOR, default.moved(ToolbarTool.SEPARATOR, -99).main.first())
        assertEquals(default, default.moved(ToolbarTool.HEADING, 0))
        down.everyToolOnce()
        across.everyToolOnce()
    }

    @Test
    fun theLayoutIsStoredAsTextAndReadBackExactly() {
        val custom = default.moved(ToolbarTool.TABLE, -20).toggled(ToolbarTool.QUOTE).moved(ToolbarTool.H4, -3)

        assertEquals(custom, ToolbarLayout.decode(custom.encode()))
        assertEquals(default, ToolbarLayout.decode(default.encode()))
        assertTrue(custom.encode().startsWith("main:TABLE,"))
    }

    @Test
    fun unreadableOrNoTextIsTheDefaultLayout() {
        assertEquals(default, ToolbarLayout.decode(null))
        assertEquals(default, ToolbarLayout.decode(""))
        assertEquals(default, ToolbarLayout.decode("nonsense"))
        assertEquals(default, ToolbarLayout.decode("bar:HEADING;baz:TEXT"))
        // A section word with no colon names nothing.
        assertEquals(default, ToolbarLayout.decode("main"))
    }

    @Test
    fun unknownToolsAreDroppedDuplicatesKeepTheirFirstPlaceAndMissingToolsGoWhereTheyAreByDefault() {
        val decoded = ToolbarLayout.decode("main:TABLE,GONE,TABLE, QUOTE;more:QUOTE,TEXT;hidden:H1")

        decoded.everyToolOnce()
        assertEquals(listOf(ToolbarTool.TABLE, ToolbarTool.QUOTE), decoded.main.take(2))
        assertTrue("a tool this text leaves out is in the row by default", ToolbarTool.HEADING in decoded.main)
        assertTrue("or behind the chevron by default", ToolbarTool.LINK in decoded.more)
        assertEquals(listOf(ToolbarTool.H1), decoded.hidden)
        assertFalse(ToolbarTool.QUOTE in decoded.more)
    }

    @Test
    fun theButtonsShrinkAsTheRowFills() {
        // A 411 dp screen leaves 391 dp for the toolbar's row.
        assertEquals(ToolbarScale(44, 22), toolbarScale(buttons = 4, availableDp = 391))
        assertEquals(ToolbarScale(44, 22), toolbarScale(buttons = 9, availableDp = 391))
        assertEquals(ToolbarScale(40, 20), toolbarScale(buttons = 10, availableDp = 391))
        assertEquals(ToolbarScale(36, 18), toolbarScale(buttons = 11, availableDp = 391))
        assertEquals(ToolbarScale(36, 18), toolbarScale(buttons = 30, availableDp = 391))
        assertEquals(ToolbarScale(36, 18), toolbarScale(buttons = 9, availableDp = 300))
    }

    @Test
    fun theChoiceIsStoredAndFollowedBeforeAnyScreenAsksAndResetRestoresTheDefault() {
        val flags = InMemoryKeyValueStore()
        assertEquals(default, EditorToolbar(flags).layout.value)

        val custom = default.toggled(ToolbarTool.CODE)
        EditorToolbar(flags).set(custom)
        assertEquals(custom, EditorToolbar(flags).layout.value)

        val toolbar = EditorToolbar(flags)
        toolbar.reset()
        assertEquals(default, EditorToolbar(flags).layout.value)
    }

    @Test
    fun theViewModelMovesHidesAndResets() {
        val toolbar = EditorToolbar(InMemoryKeyValueStore())
        val viewModel = EditorToolbarViewModel(toolbar)

        viewModel.move(ToolbarTool.HEADING, 1)
        assertEquals(ToolbarTool.LIST_CHECK, viewModel.layout.value.main.first())
        viewModel.toggle(ToolbarTool.HEADING)
        assertEquals(listOf(ToolbarTool.HEADING), viewModel.layout.value.hidden)
        viewModel.reset()
        assertEquals(default, viewModel.layout.value)
    }

    @Test
    fun theBackupAcceptsOnlyAWellFormedLayoutOfKnownTools() {
        fun accepted(value: String) =
            BackupSettings.accepted(mapOf(BackupSettings.EDITOR_TOOLBAR to value)).isNotEmpty()

        assertTrue(accepted(default.encode()))
        assertTrue(accepted("main:HEADING;more:;hidden:"))
        assertFalse(accepted("main:NOT_A_TOOL"))
        assertFalse(accepted("tools:HEADING"))
        assertFalse(accepted("main"))
        assertFalse(accepted(""))
        assertFalse(accepted("main:" + "HEADING,".repeat(60)))
    }
}
