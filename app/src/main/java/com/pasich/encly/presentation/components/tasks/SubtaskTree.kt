package com.pasich.encly.presentation.components.tasks

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.R
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.presentation.designsystem.CheckboxSize
import com.pasich.encly.presentation.designsystem.EnclyAddSubtaskButton
import com.pasich.encly.presentation.designsystem.EnclyCheckbox
import com.pasich.encly.presentation.designsystem.EnclyExpandButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyRowIconButton
import com.pasich.encly.presentation.designsystem.EnclyStepTransition
import com.pasich.encly.presentation.designsystem.EnclySubtaskAddIcon
import com.pasich.encly.presentation.designsystem.EnclySubtaskField
import com.pasich.encly.presentation.designsystem.EnclySubtaskRow
import com.pasich.encly.presentation.viewmodel.InlineSubtaskEdit
import com.pasich.encly.presentation.viewmodel.InlineSubtaskTarget
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableColumn

/** What the list's inline sub-task field reports back (see TasksViewModel). */
@Immutable
class InlineSubtaskActions(
    val onTextChange: (String) -> Unit,
    /** The keyboard's Done. */
    val onDone: () -> Unit,
    /** Focus left the field for [InlineSubtaskTarget]: save it and close. */
    val onClose: (InlineSubtaskTarget) -> Unit,
    /** The ✕ of a sub-task being renamed. */
    val onDelete: () -> Unit,
)

/** What a task's sub-tasks can do on the list. */
@Immutable
class SubtaskTreeActions(
    val onToggle: (Subtask, Boolean) -> Unit,
    val onStartAdding: (taskId: Long) -> Unit,
    val onStartRenaming: (Subtask) -> Unit,
    /**
     * Drag or "Move up/down" within the task's tree: the sub-task [movedId] takes the place of
     * [targetId]. The tree calls [onFailed] back when the new order was not stored.
     */
    val onMove: (taskId: Long, movedId: Long, targetId: Long, onFailed: () -> Unit) -> Unit,
    /** The chevron: opens the whole tree, or folds it back to the next step. */
    val onToggleTree: (taskId: Long) -> Unit,
    val inline: InlineSubtaskActions,
)

/** How long a ticked next step stays, struck through, before the following one slides up. */
private const val NEXT_STEP_HOLD_MS = 600L

/**
 * A task's next step while its tree is folded: its first open sub-task as one leaf, with the
 * chevron that opens the whole tree. Ticked, it stays struck through for a moment and then the
 * following open one slides up into its place (at once with animations off); taps on it wait
 * until that is over, so a double tap cannot tick the new one. With every sub-task done it shows
 * the one ticked last (or the last one), struck through and dimmed. [subtasks] is not empty.
 */
@Composable
internal fun NextStepLeaf(
    taskId: Long,
    subtasks: List<Subtask>,
    actions: SubtaskTreeActions,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val animate = rememberAnimationsEnabled()
    val firstOpen = subtasks.firstOrNull { !it.isCompleted }
    var shownId by remember(taskId) { mutableLongStateOf(firstOpen?.id ?: subtasks.last().id) }
    val targetId = firstOpen?.id ?: subtasks.find { it.id == shownId }?.id ?: subtasks.last().id
    val currentSubtasks by rememberUpdatedState(subtasks)
    LaunchedEffect(targetId) {
        if (targetId == shownId) return@LaunchedEffect
        val leaving = currentSubtasks.find { it.id == shownId }
        if (animate && leaving?.isCompleted == true) delay(NEXT_STEP_HOLD_MS)
        shownId = targetId
    }
    val step = updateTransition(targetState = shownId, label = "nextStep")
    val moving = shownId != targetId || step.currentState != step.targetState
    val expand = stringResource(R.string.subtasks_show)
    EnclyStepTransition(step = step, animate = animate, modifier = modifier) { id ->
        val subtask = subtasks.find { it.id == id }
        if (subtask != null) {
            EnclySubtaskRow(
                title = subtask.title,
                checked = subtask.isCompleted,
                onCheckedChange = { actions.onToggle(subtask, it) },
                isLast = true,
                dimmed = firstOpen == null,
                enabled = enabled && !moving,
                onClick = { actions.onToggleTree(taskId) },
                onClickLabel = expand,
                checkboxDescription = stringResource(R.string.subtask_next_step, subtask.title),
                trailing = {
                    EnclyExpandButton(expanded = false, contentDescription = expand, onClick = {
                        actions.onToggleTree(taskId)
                    })
                },
            )
        }
    }
}

/** False when the system's animator duration scale is 0 (animations turned off). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * A task's whole tree: each of [subtasks] in order (tick in place; a tap on the title renames it
 * inline; long-press drags it within the tree, with "Move up/down" for TalkBack), and always last
 * the add leaf, which turns into the inline field for a new one. A task without sub-tasks shows
 * only that leaf, as "First step". The first row carries the chevron that folds the tree back.
 * [edit] is the list's inline field when it belongs to this task; [dimmed] for a task that is done.
 */
@Composable
internal fun SubtaskTree(
    taskId: Long,
    subtasks: List<Subtask>,
    edit: InlineSubtaskEdit?,
    actions: SubtaskTreeActions,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    // Shows a dropped row in its new place until the stored order comes back (see moveRow).
    val rows = remember(subtasks) { mutableStateOf(subtasks) }
    val currentSubtasks by rememberUpdatedState(subtasks)
    val collapse = stringResource(R.string.subtasks_hide)
    val chevron: @Composable () -> Unit = {
        EnclyExpandButton(expanded = true, contentDescription = collapse, onClick = { actions.onToggleTree(taskId) })
    }
    val move = { from: Int, to: Int ->
        rows.moveRow(from, to) { moved, target ->
            actions.onMove(taskId, moved, target) { rows.value = currentSubtasks }
        }
    }
    Column(modifier = modifier) {
        ReorderableColumn(
            list = rows.value,
            onSettle = move,
            onMove = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        ) { index, subtask, dragging ->
            key(subtask.id) {
                val renaming = edit != null && edit.target == InlineSubtaskTarget.Rename(taskId, subtask.id)
                val reorder = Modifier.longPressDraggableHandle(
                    enabled = enabled && !renaming,
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
                // Given to the title: the node TalkBack focuses (a plain row is not one).
                val moves = moveActions(
                    moveUp = { move(index, index - 1) }.takeIf { index > 0 },
                    moveDown = { move(index, index + 1) }.takeIf { index < rows.value.lastIndex },
                )
                if (renaming) {
                    RenameField(subtask, edit, actions, enabled)
                } else {
                    EnclySubtaskRow(
                        title = subtask.title,
                        checked = subtask.isCompleted,
                        onCheckedChange = { actions.onToggle(subtask, it) },
                        // The add leaf always closes the tree.
                        isLast = false,
                        modifier = reorder,
                        dimmed = dimmed,
                        enabled = enabled,
                        onClick = { actions.onStartRenaming(subtask) },
                        onClickLabel = stringResource(R.string.edit),
                        customActions = moves,
                        dragging = dragging,
                        trailing = chevron.takeIf { index == 0 },
                    )
                }
            }
        }
        AddLeaf(
            taskId,
            edit,
            actions,
            enabled,
            first = subtasks.isEmpty(),
            trailing = chevron.takeIf { subtasks.isEmpty() },
        )
    }
}

/**
 * Moves the row at [from] to [to] at once and asks [store] to store that by ids (the moved row
 * and the one whose place it takes); the caller puts the stored order back if that fails.
 */
private fun MutableState<List<Subtask>>.moveRow(from: Int, to: Int, store: (movedId: Long, targetId: Long) -> Unit) {
    val current = value
    if (from !in current.indices || to !in current.indices || from == to) return
    value = current.toMutableList().apply { add(to, removeAt(from)) }
    store(current[from].id, current[to].id)
}

/** "Move up" and "Move down" for TalkBack, where the row can move that way. */
@Composable
private fun moveActions(moveUp: (() -> Unit)?, moveDown: (() -> Unit)?): List<CustomAccessibilityAction> {
    val up = stringResource(R.string.tag_move_up)
    val down = stringResource(R.string.tag_move_down)
    return listOfNotNull(
        moveUp?.let { action ->
            CustomAccessibilityAction(up) {
                action()
                true
            }
        },
        moveDown?.let { action ->
            CustomAccessibilityAction(down) {
                action()
                true
            }
        },
    )
}

/** The add leaf (└ + Sub-task, or + First step), or the inline field it turned into. */
@Suppress("LongParameterList") // The leaf's state and callbacks, split out of SubtaskTree.
@Composable
private fun AddLeaf(
    taskId: Long,
    edit: InlineSubtaskEdit?,
    actions: SubtaskTreeActions,
    enabled: Boolean,
    first: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    val label = stringResource(if (first) R.string.subtask_first_step else R.string.subtask_placeholder)
    if (edit != null && edit.target == InlineSubtaskTarget.Add(taskId)) {
        InlineSubtaskField(edit = edit, label = label, actions = actions.inline)
    } else {
        EnclyAddSubtaskButton(
            text = label,
            onClick = { actions.onStartAdding(taskId) },
            description = stringResource(R.string.subtask_add),
            enabled = enabled,
            trailing = trailing,
        )
    }
}

/** A sub-task row while it is renamed: its checkbox, the field with its title, and ✕ to delete it. */
@Composable
private fun RenameField(subtask: Subtask, edit: InlineSubtaskEdit, actions: SubtaskTreeActions, enabled: Boolean) {
    InlineSubtaskField(
        edit = edit,
        label = stringResource(R.string.subtask_placeholder),
        actions = actions.inline,
        isLast = false,
        done = subtask.isCompleted,
        leading = {
            EnclyCheckbox(
                checked = subtask.isCompleted,
                onCheckedChange = { actions.onToggle(subtask, it) },
                size = CheckboxSize.SMALL,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = subtask.title },
            )
        },
        trailing = {
            EnclyRowIconButton(
                icon = EnclyIcons.Close,
                contentDescription = stringResource(R.string.subtask_delete),
                onClick = actions.inline.onDelete,
            )
        },
    )
}

/**
 * The list's one inline sub-task field. It takes focus (and the keyboard) when it appears and
 * stays in view above the keyboard. It closes, saving through [InlineSubtaskActions.onClose],
 * when it loses focus, on Back, or when the keyboard is hidden while it is focused.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList") // EnclySubtaskField's slots, passed through.
@Composable
private fun InlineSubtaskField(
    edit: InlineSubtaskEdit,
    label: String,
    actions: InlineSubtaskActions,
    modifier: Modifier = Modifier,
    isLast: Boolean = true,
    done: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val target = edit.target
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val currentActions by rememberUpdatedState(actions)
    val close = { currentActions.onClose(target) }
    // A rotation disposes the field, which takes its focus away: that is not the user leaving it
    // (the field lives in the ViewModel and comes back open, with its text).
    val activity = LocalActivity.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = close)
    CloseWhenKeyboardHides(focused = focused, onClose = close)
    // The list is ime-padded: once the keyboard is up (or its height changes), scroll to the field.
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboardHeight) {
        if (focused) bringIntoView.bringIntoView()
    }

    EnclySubtaskField(
        value = edit.text,
        onValueChange = actions.onTextChange,
        label = label,
        isLast = isLast,
        onDone = actions.onDone,
        modifier = modifier.bringIntoViewRequester(bringIntoView),
        fieldModifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    focused = true
                } else if (focused) {
                    focused = false
                    if (activity?.isChangingConfigurations != true) close()
                }
            },
        done = done,
        leading = leading ?: { EnclySubtaskAddIcon() },
        trailing = trailing,
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
    val keyboardVisible = WindowInsets.isImeVisible
    var seenOpen by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(focused, keyboardVisible) {
        when {
            !focused -> seenOpen = false

            keyboardVisible -> seenOpen = true

            seenOpen -> {
                seenOpen = false
                currentOnClose()
            }
        }
    }
}
