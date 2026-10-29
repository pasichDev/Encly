package com.pasich.encly.presentation.dialogs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.clear
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.delete_tag
import com.pasich.encly.core.resources.dialog_confirm
import com.pasich.encly.core.resources.dialog_dismiss
import com.pasich.encly.core.resources.task_clear_completed_title
import com.pasich.encly.ui.screens.ComposeScreenTest
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Test

/** The yes/no dialogs: hidden unless asked, and a confirmation always closes the dialog it came from. */
class ConfirmationDialogsTest : ComposeScreenTest() {
    private val calls = mutableListOf<String>()

    private fun shown(id: StringResource) = countText(str(id)) > 0

    @Test
    fun aHiddenDeleteTagDialogShowsNothing() {
        setScreen { RequestDeleteTagsDialog(showDialog = false, onDismiss = { calls += "dismiss" }, onConfirm = {}) }

        assertEquals(0, countText(str(Res.string.delete_tag)))
    }

    @Test
    fun confirmingTheTagDeletionConfirmsThenDismisses() {
        setScreen {
            RequestDeleteTagsDialog(
                showDialog = true,
                onDismiss = { calls += "dismiss" },
                onConfirm = { calls += "confirm" },
            )
        }
        assertEquals(1, countText(str(Res.string.delete_tag)))

        rule.onNodeWithText(str(Res.string.delete)).performClick()

        assertEquals(listOf("confirm", "dismiss"), calls)
    }

    @Test
    fun cancellingTheTagDeletionOnlyDismisses() {
        setScreen {
            RequestDeleteTagsDialog(
                showDialog = true,
                onDismiss = { calls += "dismiss" },
                onConfirm = { calls += "confirm" },
            )
        }

        rule.onNodeWithText(str(Res.string.cancel)).performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun aHiddenClearCompletedDialogShowsNothing() {
        setScreen { RequestCleanCompleteTaskDialog(showDialog = false, onDismiss = {}, onConfirm = {}) }

        assertEquals(0, countText(str(Res.string.task_clear_completed_title)))
    }

    @Test
    fun confirmingTheClearOfCompletedTasksConfirmsThenDismisses() {
        setScreen {
            RequestCleanCompleteTaskDialog(
                showDialog = true,
                onDismiss = { calls += "dismiss" },
                onConfirm = { calls += "confirm" },
            )
        }
        assertEquals(1, countText(str(Res.string.task_clear_completed_title)))

        rule.onNodeWithText(str(Res.string.clear)).performClick()

        assertEquals(listOf("confirm", "dismiss"), calls)
    }

    @Test
    fun cancellingTheClearOfCompletedTasksOnlyDismisses() {
        setScreen {
            RequestCleanCompleteTaskDialog(
                showDialog = true,
                onDismiss = { calls += "dismiss" },
                onConfirm = { calls += "confirm" },
            )
        }

        rule.onNodeWithText(str(Res.string.cancel)).performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun theGenericConfirmDialogShowsItsTextAndRoutesBothButtons() {
        setScreen {
            ConfirmDialog(
                isVisible = true,
                titleText = "Erase?",
                messageText = "This cannot be undone.",
                onConfirm = { calls += "confirm" },
                onDismiss = { calls += "dismiss" },
                destructive = true,
            )
        }
        assertEquals(1, countText("Erase?"))
        assertEquals(1, countText("This cannot be undone."))

        rule.onNodeWithText(str(Res.string.dialog_dismiss)).performClick()
        rule.onNodeWithText(str(Res.string.dialog_confirm)).performClick()

        assertEquals(listOf("dismiss", "confirm"), calls)
    }

    @Test
    fun anInvisibleConfirmDialogShowsNothing() {
        setScreen {
            ConfirmDialog(isVisible = false, titleText = "Erase?", messageText = "m", onConfirm = {}, onDismiss = {})
        }

        assertEquals(0, countText("Erase?"))
        assertEquals(false, shown(Res.string.dialog_confirm))
    }
}
