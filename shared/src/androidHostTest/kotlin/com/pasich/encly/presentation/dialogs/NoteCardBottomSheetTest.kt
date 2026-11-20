package com.pasich.encly.presentation.dialogs

import androidx.activity.ComponentDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.back
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.edit
import com.pasich.encly.core.resources.main_drawer_tags
import com.pasich.encly.core.resources.no_tag
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_blue
import com.pasich.encly.core.resources.note_color_teal
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_description_none
import com.pasich.encly.core.resources.note_description_placeholder
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_tags_more
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.tag_hidden
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.core.resources.task_description_placeholder
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.presentation.screen.NoteActionsSheet
import com.pasich.encly.presentation.screen.NoteSheetState
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.screens.ComposeScreenTest
import com.pasich.encly.ui.screens.TestApp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/**
 * The long-press sheet of a note: its quick action tiles, colour swatches, tag chips and description row,
 * the pages "+N" and the description row lead to (and the way back), and which action each reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
class NoteCardBottomSheetTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }
    private val actions = mutableListOf<NoteAction>()
    private var dismissed = 0

    private val tags = listOf(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home"))

    private fun show(
        note: Note = Note(id = 5, title = "Groceries", date = 1_700_000_000_000L),
        visible: Boolean = true,
        allTags: List<Tag> = tags,
    ) {
        app.tags.tags.value = allTags
        setScreen(viewModels(app.tagList())) {
            NoteCardBottomSheet(
                isVisible = visible,
                item = NoteWithTags(note, allTags.filter { it.id in note.tagIds }),
                onAction = { actions += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    /** Tags "Tag 1" to "Tag [count]": more than the sheet shows as chips. */
    private fun manyTags(count: Int = 8) = (1..count).map { Tag(id = it.toLong(), nameTag = "Tag $it") }

    private fun openDescription() {
        rule.onNodeWithText(str(Res.string.note_description_none)).performClick()
        rule.waitForIdle()
        assertEquals("the first page made way", 0, countText(str(Res.string.duplicate)))
    }

    private fun openTags(more: String = "+2") {
        rule.onNodeWithText(more).performClick()
        rule.waitForIdle()
        assertEquals("the first page made way", 0, countText(str(Res.string.duplicate)))
    }

    private fun pageHeight() = rule.onNodeWithTag(NOTE_SHEET_PAGE_TAG).fetchSemanticsNode().size.height

    /** The system's Back, as the sheet's own window gets it. */
    private fun pressBack() {
        rule.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    @Test
    fun aHiddenSheetShowsNothing() {
        show(visible = false)

        assertEquals(0, countText("Groceries"))
    }

    @Test
    fun aPlainNoteShowsItsTilesSectionsAndDescriptionRow() {
        show()

        assertEquals(1, countText("Groceries"))
        listOf(
            str(Res.string.note_pin),
            str(Res.string.note_archive),
            str(Res.string.duplicate),
            str(Res.string.delete),
            str(Res.string.note_color_title).uppercase(),
            str(Res.string.main_drawer_tags).uppercase(),
            "Work",
            "Home",
            str(Res.string.note_description_none),
        ).forEach { assertEquals(it, 1, countText(it)) }
        assertEquals("edit is a tap on the note, not a tile", 0, countText(str(Res.string.edit)))
        assertEquals("tags exist, so no muted placeholder", 0, countText(str(Res.string.no_tag)))
        assertEquals(0, countText(str(Res.string.note_description_placeholder)))
    }

    @Test
    fun theNotesColourTagsAndDescriptionAreShownAsSelected() {
        show(
            Note(
                id = 5,
                title = "Groceries",
                tagIds = setOf(2),
                color = NoteColor.TEAL.key,
                description = "Weekly shop\nand the market",
                isPinned = true,
            ),
        )

        rule.onNode(hasText("Home") and isOn()).assertExists()
        rule.onNode(hasText("Work") and isOff()).assertExists()
        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).assertIsSelected()
        rule.onNodeWithContentDescription(str(Res.string.note_color_blue)).assertIsNotSelected()
        assertEquals(1, countText("Weekly shop"))
        assertEquals(0, countText(str(Res.string.note_description_none)))
        assertEquals("a pinned note offers Unpin", 1, countText(str(Res.string.note_unpin)))
    }

    @Test
    fun anArchivedNoteOffersUnarchiveAndNoPin() {
        show(Note(id = 5, title = "Groceries", isArchived = true))

        assertEquals(1, countText(str(Res.string.note_unarchive)))
        assertEquals(0, countText(str(Res.string.note_pin)))
        assertEquals(0, countText(str(Res.string.note_archive)))
    }

    @Test
    fun aNoteWithoutATitleIsCalledUntitled() {
        show(Note(id = 5, title = "", date = 1L))

        assertEquals(1, countText(str(Res.string.untitled)))
    }

    @Test
    fun theTilesReportTheirActions() {
        show()

        rule.onNodeWithText(str(Res.string.note_pin)).performClick()
        rule.onNodeWithText(str(Res.string.note_archive)).performClick()
        rule.onNodeWithText(str(Res.string.duplicate)).performClick()

        assertEquals(
            listOf(NoteAction.Pin(true), NoteAction.Archive(true), NoteAction.Duplicate),
            actions,
        )
    }

    @Test
    fun unpinAndUnarchiveReportTheOppositeState() {
        show(Note(id = 5, title = "Groceries", isPinned = true))
        rule.onNodeWithText(str(Res.string.note_unpin)).performClick()
        assertEquals(listOf<NoteAction>(NoteAction.Pin(false)), actions)
    }

    @Test
    fun deletingTakesASecondTap() {
        show()

        rule.onNodeWithText(str(Res.string.delete)).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<NoteAction>(), actions)

        rule.onNodeWithText(str(Res.string.tap_again_to_delete)).performClick()
        assertEquals(listOf<NoteAction>(NoteAction.Delete), actions)
    }

    @Test
    fun anArmedDeleteTileIsALiveRegionSoTalkBackSpeaksTheNewLabel() {
        show()
        rule.onNodeWithText(str(Res.string.delete)).performClick()
        rule.waitForIdle()

        rule.onNode(hasText(str(Res.string.tap_again_to_delete)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun aTagChipHasAtLeastA48DpTouchTarget() {
        show()

        // The pill stays 36 dp; the touch bounds around it are the 48 dp minimum.
        val touch = rule.onNode(hasText("Work") and isOff()).fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("chip touch target is ${touch.height}px, needs $minimum", touch.height >= minimum - 1f)
    }

    @Test
    fun aHiddenTagChipIsMarkedHiddenAndAPlainOneIsNot() {
        show(allTags = listOf(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Secret", isVisible = false)))

        rule.onNode(hasText("Secret"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(Res.string.tag_hidden)))
        rule.onNode(hasText("Work"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun theSectionTitlesAreHeadings() {
        show()

        rule.onNode(hasText(str(Res.string.main_drawer_tags).uppercase()))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNode(hasText(str(Res.string.note_color_title).uppercase()))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun aColourSwatchAppliesAtOnceAndKeepsTheSheetOpen() {
        show()

        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).performClick()
        rule.waitForIdle()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeColor(NoteColor.TEAL)), actions)
        assertEquals(1, countText(str(Res.string.duplicate)))
        assertEquals(0, dismissed)
    }

    @Test
    fun aTagChipTogglesTheTagAtOnce() {
        show(Note(id = 5, title = "Groceries", tagIds = setOf(2)))

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Home").performClick()

        assertEquals(
            listOf(NoteAction.ChangeTag(1, tagged = true), NoteAction.ChangeTag(2, tagged = false)),
            actions,
        )
        assertEquals("the sheet stays open", 1, countText(str(Res.string.duplicate)))
    }

    @Test
    fun withoutTagsAMutedPlaceholderStandsInForTheChips() {
        show(allTags = emptyList())

        assertEquals(1, countText(str(Res.string.no_tag)))
    }

    @Test
    fun whileTheTagsAreBeingReadNeitherChipsNorThePlaceholderShow() {
        val loading = object : TagsRepository by app.tags {
            override fun getTags(): Flow<List<Tag>> = flow { awaitCancellation() }
        }
        val tagList = TagListViewModel(loading, ReorderTagsUseCase(loading), app.selectedTag, app.notes)
        setScreen(viewModels(tagList)) {
            NoteCardBottomSheet(
                isVisible = true,
                item = NoteWithTags(Note(id = 5, title = "Groceries", date = 1L), emptyList()),
                onAction = { actions += it },
                onDismiss = { dismissed++ },
            )
        }

        assertEquals("the section is there", 1, countText(str(Res.string.main_drawer_tags).uppercase()))
        assertEquals("but \"No tag\" does not flash before the tags", 0, countText(str(Res.string.no_tag)))
    }

    @Test
    fun theMoreChipIsAButtonReadOutAsHowManyMoreTags() {
        show(allTags = manyTags())

        rule.onNode(hasText("+2") and hasContentDescription(plural(Res.plurals.note_tags_more, 2, 2)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        rule.onNode(hasText("Tag 1") and isOff()).assertExists()
    }

    @Test
    fun theDescriptionRowIsReadOutWithItsName() {
        show(Note(id = 5, title = "Groceries", description = "Weekly shop"))

        val label = str(Res.string.task_description_placeholder)
        rule.onNode(hasText("Weekly shop") and hasContentDescription("$label, Weekly shop")).assertHasClickAction()
    }

    @Test
    fun manyTagsFoldIntoAMoreChipThatOpensTheFullTagsPage() {
        show(Note(id = 5, title = "Groceries", tagIds = setOf(8)), allTags = manyTags())

        assertEquals(1, countText("Tag 6"))
        assertEquals("the rest are folded", 0, countText("Tag 7"))
        assertEquals("the note's own tag shows even past the fold", 1, countText("Tag 8"))
        openTags(more = "+1")

        waitForText("Tag 7")
        rule.onNodeWithText("Tag 8").performScrollTo().performClick()
        assertEquals(listOf<NoteAction>(NoteAction.ChangeTag(8, tagged = false)), actions)
        assertEquals("the page stays open for the next tag", 0, countText(str(Res.string.duplicate)))
    }

    @Test
    fun theTagsPageNoTagRowClearsEveryTag() {
        show(Note(id = 5, title = "Groceries", tagIds = setOf(1)), allTags = manyTags())
        openTags()
        waitForText("Tag 7")

        rule.onNodeWithText(str(Res.string.no_tag)).performClick()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeTag(0, tagged = false)), actions)
    }

    @Test
    fun thePagesBackButtonReturnsToTheFirstPage() {
        show(allTags = manyTags())
        openTags()

        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()

        assertEquals(1, countText(str(Res.string.duplicate)))
        assertEquals(0, dismissed)
    }

    @Test
    fun systemBackOnAPageReturnsToTheFirstPageAndKeepsTheSheetOpen() {
        show()
        openDescription()

        pressBack()

        assertEquals("back on the first page", 1, countText(str(Res.string.duplicate)))
        assertEquals("the sheet was not closed", 0, dismissed)
        assertEquals(1, countText("Groceries"))
    }

    @Test
    fun everyPageIsAsHighAsTheFirstPage() {
        show(allTags = manyTags())
        val first = pageHeight()

        openTags()
        assertEquals("tags", first, pageHeight())
        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()
        assertEquals("back from tags", first, pageHeight())

        openDescription()
        assertEquals("description", first, pageHeight())
        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()
        assertEquals("back from description", first, pageHeight())
    }

    @Test
    fun savingADescriptionReportsItTrimmedAndReturnsToTheRows() {
        show()
        openDescription()

        rule.onNode(hasSetTextAction()).performTextInput("  Buy milk  ")
        rule.onNodeWithText(str(Res.string.save)).performClick()
        rule.waitForIdle()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeDescription("Buy milk")), actions)
        assertEquals("the rows are back", 1, countText(str(Res.string.duplicate)))
    }

    @Test
    fun goingBackFromTheDescriptionReportsNothing() {
        show(Note(id = 5, title = "Groceries", description = "Weekly shop"))
        rule.onNodeWithText("Weekly shop").performClick()
        rule.waitForIdle()

        rule.onNode(hasSetTextAction()).performTextInput(" and more")
        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()

        assertEquals(emptyList<NoteAction>(), actions)
        assertEquals(1, countText("Weekly shop"))
        assertEquals(1, countText(str(Res.string.duplicate)))
    }

    @Test
    fun anActionRowRunsOnlyOnceTheSheetHasSlidAway() {
        val duplicated = mutableListOf<Long>()
        val note = Note(id = 5, title = "Groceries", date = 1L)
        app.tags.tags.value = tags
        setScreen(viewModels(app.tagList())) {
            val sheet =
                remember { NoteSheetState().apply { open(NoteListItem(NoteWithTags(note, emptyList()), null, "")) } }
            NoteActionsSheet(
                sheet = sheet,
                observeNote = { flowOf(NoteWithTags(note, emptyList())) },
                onEvent = {},
                onEdit = {},
                onDuplicate = { duplicated += it.id },
            )
        }
        rule.mainClock.autoAdvance = false

        rule.onNodeWithText(str(Res.string.duplicate)).performClick()
        rule.waitForIdle()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }

        assertEquals("nothing runs under the sheet", emptyList<Long>(), duplicated)
        assertEquals("the sheet is still there, sliding away", 1, countText("Groceries"))

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf(5L), duplicated)
        assertEquals(0, countText("Groceries"))
    }
}
