package com.pasich.encly.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.pasich.encly.R
import com.pasich.encly.data.importer.ImportLosses
import com.pasich.encly.data.importer.ImportPreview
import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.data.importer.ReviewNote
import com.pasich.encly.presentation.screen.importnotes.ImportNotesActions
import com.pasich.encly.presentation.screen.importnotes.ImportNotesContent
import com.pasich.encly.presentation.viewmodel.ImportFailure
import com.pasich.encly.presentation.viewmodel.ImportResult
import com.pasich.encly.presentation.viewmodel.ImportStep
import com.pasich.encly.presentation.viewmodel.ReviewFilter
import com.pasich.encly.presentation.viewmodel.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every state of the Import notes page, stateless, with recorded callbacks. */
class ImportNotesScreenTest : ComposeScreenTest() {
    private val calls = mutableListOf<String>()

    private val actions = ImportNotesActions(
        onChooseFiles = { calls += "files" },
        onChooseFolder = { calls += "folder" },
        onImport = { calls += "import" },
        onCancel = { calls += "cancel" },
        onChooseSource = { calls += "source:${it.name}" },
        onToggle = { calls += "toggle:$it" },
        onToggleAll = { calls += "all" },
        onFilter = { calls += "filter:${it.name}" },
        onExpand = { calls += "expand:$it" },
    )

    private val current = mutableStateOf<ImportStep>(ImportStep.Start)
    private var shown = false

    /** Shows [step]; a second call swaps the step in the same composition. */
    private fun show(step: ImportStep) {
        current.value = step
        if (shown) {
            rule.waitForIdle()
        } else {
            shown = true
            setScreen { ImportNotesContent(current.value, actions, onBack = { calls += "back" }) }
        }
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun row(uid: String, title: String, existing: Boolean = false, trashed: Boolean = false) = ReviewNote(
        uid = uid,
        title = title,
        snippet = "snippet of $title",
        hasChecklist = uid == "a",
        tag = if (uid == "a") "home" else null,
        trashed = trashed,
        date = 1_700_000_000_000,
        existing = existing,
    )

    private val rows = listOf(
        row("a", "Groceries"),
        row("b", "Old draft", trashed = true),
        row("c", "Seen before", existing = true),
        row("d", ""),
    )

    private val plain =
        ImportPreview(source = ImportSource.GOOGLE_KEEP, notes = 4, checklists = 1, tags = 1, trashed = 1)

    private val preview = plain.copy(
        duplicates = 1,
        losses = ImportLosses(pinned = 2, attachments = 3),
        unsupported = 1,
        filesSkipped = 2,
    )

    private fun review(
        selected: Set<String> = setOf("a", "b", "d"),
        filter: ReviewFilter = ReviewFilter.ALL,
        header: ImportPreview = plain,
    ) = ImportStep.Preview(header, ReviewState(rows, selected, filter))

    @Test
    fun startListsTheSupportedAppsAndTheTwoPickers() {
        show(ImportStep.Start)

        rule.onNodeWithText(str(R.string.import_intro)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_choose_files)).assertIsEnabled().performClick()
        rule.onNodeWithText(str(R.string.import_choose_folder)).performClick()
        assertEquals(listOf("files", "folder"), calls)
        ImportSource.entries.forEach { source ->
            rule.onNodeWithText(str(source.title)).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(str(source.formats)).assertExists()
            // How to export and what is lost unfold on a tap, so the list stays short.
            rule.onNodeWithText(str(source.howTo)).assertDoesNotExist()
            rule.onNodeWithText(str(source.title)).performClick()
            rule.onNodeWithText(str(source.howTo)).assertExists()
            rule.onNodeWithText(str(source.losses)).assertExists()
        }
        assertBodyNotEmpty(minTexts = 4)
    }

    @Test
    fun reviewShowsTheDetectedSourceCountsLossesAndEveryNote() {
        show(review(header = preview))

        rule.onNodeWithText(str(R.string.import_preview_title)).assertIsDisplayed()
        rule.onNodeWithText(
            str(R.string.import_detected, str(R.string.import_keep_name), plural(R.plurals.backup_count_notes, 4, 4)),
        ).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_stat_checklists, 1)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_stat_destination)).assertExists()
        rule.onNode(hasText(str(R.string.import_loss_pinned, 2), substring = true)).assertExists()
        rule.onNode(hasText(str(R.string.import_loss_unsupported, 1), substring = true)).assertExists()
        rule.onNodeWithText(str(R.string.import_review_selected, 3, 3)).assertIsDisplayed()
        scrollTo("Groceries")
        rule.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun newNotesAreTickedAndAnExistingOneIsNotAndCannotBe() {
        show(review())

        scrollTo(str(R.string.import_untitled))
        val boxes = rule.onAllNodes(isToggleable(), useUnmergedTree = true)
        val shown = boxes.fetchSemanticsNodes().size
        // The last rows are on screen: Old draft, Seen before, Untitled, in that order.
        boxes[shown - 3].assertIsOn()
        boxes[shown - 2].assertIsOff().assertIsNotEnabled()
        boxes[shown - 1].assertIsOn()
        rule.onNode(hasText(str(R.string.import_badge_in_vault), substring = true)).assertExists()
        rule.onNodeWithText(str(R.string.import_untitled)).assertExists()
    }

    @Test
    fun tappingACheckboxTogglesAndTappingARowExpandsIt() {
        show(review())

        scrollTo("Old draft")
        val boxes = rule.onAllNodes(isToggleable(), useUnmergedTree = true)
        boxes[boxes.fetchSemanticsNodes().size - 3].performClick()
        rule.onNodeWithText("Old draft").performClick()
        assertEquals(listOf("toggle:b", "expand:b"), calls)
    }

    @Test
    fun selectNoneDisablesImportAndSelectAllEnablesIt() {
        show(review(selected = emptySet()))

        rule.onNodeWithText(str(R.string.import_action_import_count, 0)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.import_select_all)).performClick()
        assertEquals(listOf("all"), calls)
    }

    @Test
    fun whenEveryNoteIsAlreadyHereThereIsNothingToSelect() {
        val seen = listOf(row("c", "Seen before", existing = true))
        show(ImportStep.Preview(plain, ReviewState(seen, emptySet(), ReviewFilter.ALL)))

        rule.onNodeWithText(str(R.string.import_review_selected, 0, 0)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_select_all)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.import_select_none)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.import_action_import_count, 0)).assertIsNotEnabled()
    }

    @Test
    fun withASelectionImportIsEnabledAndCountsIt() {
        show(review())

        rule.onNodeWithText(str(R.string.import_select_none)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_action_import_count, 3)).assertIsEnabled().performClick()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        assertEquals(listOf("import", "cancel"), calls)
    }

    @Test
    fun filtersListOnlyWhatTheyShowAndReportTheTap() {
        show(review(filter = ReviewFilter.TRASH).let { it.copy(review = it.review.copy(rows = it.review.rows)) })

        rule.onNodeWithText("Old draft").assertIsDisplayed()
        rule.onNodeWithText("Groceries").assertDoesNotExist()
        rule.onNodeWithText(str(R.string.import_filter_new)).performClick()
        assertEquals(listOf("filter:NEW"), calls)
    }

    @Test
    fun anExpandedRowShowsItsReadOnlyText() {
        val open = review()
        val step = open.copy(review = open.review.copy(expanded = "a", expandedText = "☑ Milk\n☐ Eggs"))
        show(step)

        rule.onNodeWithText("☑ Milk\n☐ Eggs").assertIsDisplayed()
    }

    @Test
    fun ambiguousSelectionsOfferEverySourceAndReportTheChoice() {
        show(ImportStep.ChooseSource)

        rule.onNodeWithText(str(R.string.import_choose_source_title)).assertIsDisplayed()
        ImportSource.entries.forEach { rule.onNodeWithText(str(it.title)).assertExists() }
        rule.onNodeWithText(str(R.string.import_joplin_name)).performClick()
        assertEquals(listOf("source:JOPLIN"), calls)
    }

    @Test
    fun theSummaryShowsTheCountsAndTheOriginalsStayUntouched() {
        show(ImportStep.Done(ImportResult(imported = 3, duplicates = 1, notImported = 2, tagsAdded = 1)))

        rule.onNodeWithText(str(R.string.import_done_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_done_body, 3, 1, 2)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_done_hint)).assertIsDisplayed()
        rule.onNode(hasText(str(R.string.done)) and hasClickAction()).performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun everyErrorHasItsOwnMessageAndWayBack() {
        val messages = mapOf(
            ImportFailure.ENCRYPTED_STANDARD_NOTES to R.string.import_error_encrypted_sn,
            ImportFailure.ENCRYPTED_NOTESNOOK to R.string.import_error_encrypted_nn,
            ImportFailure.UNSUPPORTED to R.string.import_error_unsupported,
            ImportFailure.TOO_LARGE to R.string.import_error_too_large,
            ImportFailure.NOTHING_FOUND to R.string.import_error_nothing,
            ImportFailure.UNREADABLE to R.string.import_error_unreadable,
            ImportFailure.NO_PICKER to R.string.import_error_no_picker,
            ImportFailure.VAULT to R.string.import_error_vault,
            ImportFailure.IMPORT to R.string.import_error_failed,
        )
        assertEquals(ImportFailure.entries.toSet(), messages.keys)
        messages.forEach { (failure, message) ->
            calls.clear()
            show(ImportStep.Failed(failure))
            rule.onNodeWithText(str(message)).assertIsDisplayed()
            rule.onNodeWithText(str(R.string.import_error_title)).assertIsDisplayed()
            rule.onNodeWithText(str(R.string.import_choose_again)).performClick()
            assertEquals(listOf("cancel"), calls)
        }
    }
}
