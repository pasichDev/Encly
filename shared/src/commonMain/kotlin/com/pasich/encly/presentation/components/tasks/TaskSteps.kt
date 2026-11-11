package com.pasich.encly.presentation.components.tasks

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.input.ImeAction
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.edit
import com.pasich.encly.core.resources.step_add
import com.pasich.encly.core.resources.subtask_add
import com.pasich.encly.core.resources.subtask_delete
import com.pasich.encly.core.resources.tag_move_down
import com.pasich.encly.core.resources.tag_move_up
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.platform.LocalAppWindow
import com.pasich.encly.presentation.designsystem.EnclyAddStep
import com.pasich.encly.presentation.designsystem.EnclyStepField
import com.pasich.encly.presentation.designsystem.EnclyStepRow
import com.pasich.encly.presentation.effects.rememberImeVisible
import com.pasich.encly.presentation.viewmodel.InlineSubtaskEdit
import com.pasich.encly.presentation.viewmodel.InlineSubtaskTarget
import com.pasich.encly.ui.theme.EnclyMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

/** What the list's one inline step field reports back (see TasksViewModel). */
@Immutable
class InlineStepActions(
    val onTextChange: (String) -> Unit,
    /** The keyboard's key: a new step is added and the field stays for the next one; a rename is saved. */
    val onSubmit: () -> Unit,
    /** Focus left the field for [InlineSubtaskTarget]: save it and close. */
    val onClose: (InlineSubtaskTarget) -> Unit,
)

/** What a task's steps can do on the list. */
@Immutable
class StepActions(
    val onToggle: (Subtask, Boolean) -> Unit,
    val onStartAdding: (taskId: Long) -> Unit,
    /** The count button of the task's row: show all its steps, or fold them to the next one. */
    val onSetOpen: (taskId: Long, open: Boolean) -> Unit,
    val change: StepChangeActions,
    val inline: InlineStepActions,
)

/** What can be done to one step from its row: a long press renames it; TalkBack is offered all three. */
@Immutable
class StepChangeActions(
    val onStartRenaming: (Subtask) -> Unit,
    /** "Move up/down": the step [movedId] takes the place of [targetId]. */
    val onMove: (taskId: Long, movedId: Long, targetId: Long) -> Unit,
    val onDelete: (Subtask) -> Unit,
)

/** Where a step stands among the steps shown with it: the ones it can change places with. */
class StepNeighbours(val above: Subtask?, val below: Subtask?) {
    companion object {
        /** A step shown alone: it has no place to move to. */
        val None = StepNeighbours(null, null)
    }
}

/** The words of a task's steps, read once for the whole list rather than by every row. */
@Immutable
class StepLabels(val change: StepChangeLabels, val field: String, val add: String, val addDescription: String)

/** What TalkBack is offered on a step. */
@Immutable
class StepChangeLabels(val rename: String, val moveUp: String, val moveDown: String, val delete: String)

@Composable
internal fun rememberStepLabels(): StepLabels {
    val rename = stringResource(Res.string.edit)
    val moveUp = stringResource(Res.string.tag_move_up)
    val moveDown = stringResource(Res.string.tag_move_down)
    val delete = stringResource(Res.string.subtask_delete)
    val field = stringResource(Res.string.step_add)
    val add = stringResource(Res.string.step_add)
    val addDescription = stringResource(Res.string.subtask_add)
    return remember(rename, moveUp, moveDown, delete, field, add, addDescription) {
        StepLabels(StepChangeLabels(rename, moveUp, moveDown, delete), field, add, addDescription)
    }
}

/**
 * A step of a task on the list. Tick it in place; a long press turns its title into a field to rename it
 * ([edit] is the list's inline field when it is this step's). TalkBack is offered Rename, Move up, Move
 * down and Delete on the title.
 */
@Composable
internal fun StepRow(
    step: Subtask,
    edit: InlineSubtaskEdit?,
    actions: StepActions,
    labels: StepLabels,
    neighbours: StepNeighbours,
    modifier: Modifier = Modifier,
) {
    if (edit != null && edit.target == InlineSubtaskTarget.Rename(step.taskId, step.id)) {
        InlineStepField(
            edit = edit,
            label = labels.field,
            imeAction = ImeAction.Done,
            actions = actions.inline,
            modifier = modifier,
            // Where it stands: the arrows of the keyboard bar move it, and the list follows it.
            place = neighbours.above?.id to neighbours.below?.id,
            checked = step.isCompleted,
            onCheckedChange = { actions.onToggle(step, it) },
        )
        return
    }
    val change = actions.change
    val offered = remember(step, neighbours, change, labels) {
        fun action(label: String, run: () -> Unit) = CustomAccessibilityAction(label) {
            run()
            true
        }
        val words = labels.change
        listOfNotNull(
            action(words.rename) { change.onStartRenaming(step) },
            neighbours.above?.let { above -> action(words.moveUp) { change.onMove(step.taskId, step.id, above.id) } },
            neighbours.below?.let { below -> action(words.moveDown) { change.onMove(step.taskId, step.id, below.id) } },
            action(words.delete) { change.onDelete(step) },
        )
    }
    EnclyStepRow(
        title = step.title,
        checked = step.isCompleted,
        onCheckedChange = { actions.onToggle(step, it) },
        onLongPress = { change.onStartRenaming(step) },
        onLongPressLabel = labels.change.rename,
        actions = offered,
        modifier = modifier,
    )
}

/** "+ Step" under a task's steps, or the field it turned into ([edit], when the list's inline field is this one). */
@Composable
internal fun AddStepRow(
    taskId: Long,
    edit: InlineSubtaskEdit?,
    actions: StepActions,
    labels: StepLabels,
    modifier: Modifier = Modifier,
) {
    if (edit != null && edit.target == InlineSubtaskTarget.Add(taskId)) {
        InlineStepField(
            edit = edit,
            label = labels.field,
            // Next: the step is added and the field stays, empty, for the one after it.
            imeAction = ImeAction.Next,
            actions = actions.inline,
            modifier = modifier,
        )
    } else {
        EnclyAddStep(
            text = labels.add,
            description = labels.addDescription,
            onClick = { actions.onStartAdding(taskId) },
            modifier = modifier,
        )
    }
}

/**
 * The list's one inline step field. It takes focus (and the keyboard) once it is laid out and stays in
 * view above the keyboard. It closes, saving through [InlineStepActions.onClose], when it loses focus, on
 * Back, or when the keyboard is hidden while it is focused.
 */
@Composable
private fun InlineStepField(
    edit: InlineSubtaskEdit,
    label: String,
    imeAction: ImeAction,
    actions: InlineStepActions,
    modifier: Modifier = Modifier,
    place: Any? = null,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val target = edit.target
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val currentActions by rememberUpdatedState(actions)
    val close = { currentActions.onClose(target) }
    // A rotation disposes the field, which takes its focus away: that is not the user leaving it
    // (the field lives in the ViewModel and comes back open, with its text).
    val window = LocalAppWindow.current

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    BackHandler(onBack = close)
    CloseWhenKeyboardHides(focused = focused, onClose = { if (!window.isChangingConfigurations) close() })
    // Scroll to the field when it takes focus and each time the keyboard has come to rest at a new height;
    // not on every frame of the keyboard's rise (the list keeps the focused text in view meanwhile).
    val keyboard = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused, keyboard, density) {
        if (!focused) return@LaunchedEffect
        bringIntoView.bringIntoView()
        snapshotFlow { keyboard.getBottom(density) }.collectLatest {
            delay(KEYBOARD_REST_MS)
            bringIntoView.bringIntoView()
        }
    }

    // Moved to another place among its task's steps: scroll to it there, now and once its row has arrived.
    LaunchedEffect(place) {
        if (place != null && focused) {
            bringIntoView.bringIntoView()
            delay(MOVE_SETTLE_MS)
            bringIntoView.bringIntoView()
        }
    }

    EnclyStepField(
        value = edit.text,
        onValueChange = actions.onTextChange,
        label = label,
        imeAction = imeAction,
        onImeAction = actions.onSubmit,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.bringIntoViewRequester(bringIntoView),
        fieldModifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    focused = true
                } else if (focused) {
                    focused = false
                    if (!window.isChangingConfigurations) close()
                }
            },
    )
}

/**
 * Calls [onClose] when the keyboard goes away while the field is [focused]: Back (which the
 * keyboard takes first) or its own hide key. Only after it has been seen open, so a hardware
 * keyboard (never shown) does not close the field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloseWhenKeyboardHides(focused: Boolean, onClose: () -> Unit) {
    val keyboardVisible = rememberImeVisible()
    var seenOpen by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(focused, keyboardVisible) {
        when {
            !focused -> seenOpen = false

            keyboardVisible -> seenOpen = true

            seenOpen -> {
                seenOpen = false
                // A rotation hides the keyboard too, just before it disposes the field. Wait a
                // moment: a disposed field cancels this, and the field comes back open with its
                // text instead of saving a half-typed title.
                delay(KEYBOARD_HIDE_SETTLE_MS)
                currentOnClose()
            }
        }
    }
}

/** A moved row has arrived after this long (the list moves its items over [EnclyMotion.LONG_MS]). */
private const val MOVE_SETTLE_MS = EnclyMotion.LONG_MS + 50L

/** The keyboard has come to rest once its height stayed the same for this long. */
private const val KEYBOARD_REST_MS = 100L

/** How long a hidden keyboard must stay hidden before it closes the inline field. */
private const val KEYBOARD_HIDE_SETTLE_MS = 250L
