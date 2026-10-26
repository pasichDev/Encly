package com.pasich.encly.presentation.components.swipe

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.noteColorStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** How far a card is dragged, as a share of its width, before the action is armed. */
internal const val SWIPE_THRESHOLD = 0.4f
private const val ICON_MIN_SCALE = 0.6f

/**
 * [action] on [note] as the event the lists already handle, or null when it does nothing there.
 * Pinning is not available for an archived note (it is never pinned); archiving one takes it out
 * of the archive.
 */
internal fun SwipeAction.toEvent(note: Note): NoteListEvent? = when (effectiveFor(note)) {
    SwipeAction.PIN -> NoteListEvent.PinNote(note, !note.isPinned)
    SwipeAction.ARCHIVE -> NoteListEvent.ArchiveNote(note, !note.isArchived)
    SwipeAction.NEXT_COLOR -> NoteListEvent.ChangeColor(note, NoteColor.fromKey(note.color).next())
    SwipeAction.DELETE -> NoteListEvent.NoteToTrash(note)
    SwipeAction.NONE -> null
}

/** [this] as it applies to [note]: a pin means nothing for an archived note. */
internal fun SwipeAction.effectiveFor(note: Note): SwipeAction =
    if (this == SwipeAction.PIN && note.isArchived) SwipeAction.NONE else this

/** Whether the card leaves the list when [this] runs (it springs back for the others). */
private fun SwipeAction.slidesOut(): Boolean = this == SwipeAction.ARCHIVE

/**
 * A note card the user can swipe: the action configured for each direction (see
 * [SwipeSettings]) is armed past [SWIPE_THRESHOLD] and runs once, on release. Behind the card a
 * background in the action's own colour and icon is revealed, the icon growing and fading in as
 * the threshold nears, with a haptic tick when it is crossed. Archive slides the card out; pin
 * and colour spring it back; delete springs it back too and asks first. The same actions are
 * offered to TalkBack as custom actions on the card, where a swipe is not an option.
 *
 * [content] draws the card and takes the modifier that carries those accessibility actions.
 */
@Composable
fun SwipeableNote(
    note: Note,
    settings: SwipeSettings,
    onEvent: (NoteListEvent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val currentNote by rememberUpdatedState(note)
    val currentOnEvent by rememberUpdatedState(onEvent)
    var confirmDelete by remember { mutableStateOf(false) }
    val right = settings.actionFor(SwipeSide.RIGHT).effectiveFor(note)
    val left = settings.actionFor(SwipeSide.LEFT).effectiveFor(note)

    /** Runs [action] on the note; true when the card should slide out. */
    fun run(action: SwipeAction): Boolean {
        val target = currentNote
        val effective = action.effectiveFor(target)
        if (effective == SwipeAction.DELETE) confirmDelete = true else effective.toEvent(target)?.let(currentOnEvent)
        return effective.slidesOut()
    }

    val drag = rememberSwipeState(right, left, note, ::run)
    val accessibilityActions = listOf(right, left).filter { it != SwipeAction.NONE }.distinct()
        .map { action -> action to stringResource(action.noteLabel(note)) }
    val cardModifier = if (accessibilityActions.isEmpty()) {
        Modifier
    } else {
        Modifier.semantics {
            customActions = accessibilityActions.map { (action, label) ->
                CustomAccessibilityAction(label) {
                    run(action)
                    true
                }
            }
        }
    }

    ConfirmDialog(
        isVisible = confirmDelete,
        titleText = stringResource(R.string.swipe_delete_title),
        messageText = stringResource(R.string.swipe_delete_message),
        onConfirm = {
            confirmDelete = false
            onEvent(NoteListEvent.NoteToTrash(note))
        },
        onDismiss = { confirmDelete = false },
        destructive = true,
    )

    Box(
        modifier = modifier
            .onSizeChanged { drag.width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                state = drag.draggable,
                orientation = Orientation.Horizontal,
                enabled = right != SwipeAction.NONE || left != SwipeAction.NONE,
                onDragStopped = { drag.release() },
            ),
    ) {
        val offset = drag.offset.value
        if (offset != 0f) {
            val towardsEnd = offset > 0f
            SwipeBackground(
                action = if (towardsEnd) right else left,
                note = note,
                towardsEnd = towardsEnd,
                progress = abs(offset) / drag.width,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(modifier = Modifier.offset { IntOffset(drag.offset.value.roundToInt(), 0) }) { content(cardModifier) }
    }
}

/**
 * The drag of one card: its offset, which only goes toward a direction that has an action, and
 * what happens on release. Past the threshold the action runs once, then the card slides out
 * (archive) or springs back; short of it, it springs back without running anything.
 */
@Stable
private class SwipeState(
    private val scope: CoroutineScope,
    private val right: () -> SwipeAction,
    private val left: () -> SwipeAction,
    private val run: (SwipeAction) -> Boolean,
    private val reduced: () -> Boolean,
) {
    val offset = Animatable(0f)
    var width by mutableFloatStateOf(1f)

    val draggable = DraggableState { delta ->
        val next = offset.value + delta
        val allowed = when {
            next > 0f -> right() != SwipeAction.NONE
            next < 0f -> left() != SwipeAction.NONE
            else -> true
        }
        if (allowed) scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(next.coerceIn(-width, width)) }
    }

    fun release() {
        val current = offset.value
        val action = if (current > 0f) right() else left()
        val armed = abs(current) / width >= SWIPE_THRESHOLD && action != SwipeAction.NONE
        scope.launch {
            val slidesOut = armed && run(action)
            val target = if (slidesOut) width * sign(current) else 0f
            offset.animateTo(target, if (reduced()) snap() else spring(dampingRatio = BOUNCE, stiffness = STIFFNESS))
        }
    }

    suspend fun comeBack() {
        offset.animateTo(0f, if (reduced()) snap() else spring(stiffness = STIFFNESS))
    }

    private companion object {
        const val BOUNCE = Spring.DampingRatioMediumBouncy
        const val STIFFNESS = Spring.StiffnessMediumLow
    }
}

@Composable
private fun rememberSwipeState(
    right: SwipeAction,
    left: SwipeAction,
    note: Note,
    run: (SwipeAction) -> Boolean,
): SwipeState {
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val currentRight by rememberUpdatedState(right)
    val currentLeft by rememberUpdatedState(left)
    val currentRun by rememberUpdatedState(run)
    val currentReduced by rememberUpdatedState(reduced)
    val state = remember(scope) {
        SwipeState(scope, { currentRight }, { currentLeft }, { currentRun(it) }, { currentReduced })
    }
    // A note that stays in the list after its swipe (archived in a hidden tag's view) comes back in.
    LaunchedEffect(note.isArchived, note.id) {
        if (state.offset.value != 0f) {
            delay(COME_BACK_DELAY_MS)
            state.comeBack()
        }
    }
    return state
}

private const val COME_BACK_DELAY_MS = 400L

/** The panel revealed behind a dragged card: the action's colour, with its icon at the near edge. */
@Composable
private fun SwipeBackground(
    action: SwipeAction,
    note: Note,
    towardsEnd: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val look = swipeLook(action, note)
    val reduced = rememberReducedMotion()
    val armed = progress >= SWIPE_THRESHOLD && action != SwipeAction.NONE
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(armed) {
        if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    // How close the drag is to arming the action; with reduced motion the icon simply stands there.
    val near = if (reduced) 1f else (progress / SWIPE_THRESHOLD).coerceIn(0f, 1f)
    Box(
        contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(look.container),
    ) {
        Icon(
            look.icon,
            contentDescription = null,
            tint = look.content,
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.l)
                .scale(ICON_MIN_SCALE + (1f - ICON_MIN_SCALE) * near)
                .graphicsLayer(alpha = near),
        )
    }
}

private class SwipeLook(val container: Color, val content: Color, val icon: ImageVector)

/** The colours and icon of [action] on [note]: pin primary, archive tertiary, colour the next swatch, delete error. */
@Composable
private fun swipeLook(action: SwipeAction, note: Note): SwipeLook {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        SwipeAction.PIN -> SwipeLook(
            scheme.primary,
            scheme.onPrimary,
            if (note.isPinned) EnclyIcons.PinOff else EnclyIcons.Pin,
        )

        SwipeAction.ARCHIVE -> SwipeLook(
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
            if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
        )

        SwipeAction.NEXT_COLOR -> {
            val style = noteColorStyle(NoteColor.fromKey(note.color).next(), scheme)
            SwipeLook(
                style?.accent ?: scheme.surfaceContainerHigh,
                style?.onAccent ?: scheme.onSurface,
                EnclyIcons.Palette,
            )
        }

        SwipeAction.DELETE -> SwipeLook(scheme.errorContainer, scheme.onErrorContainer, EnclyIcons.Trash)

        SwipeAction.NONE -> SwipeLook(scheme.surface, scheme.onSurface, EnclyIcons.Close)
    }
}

/** Whether the system asks for no animation (animator duration scale 0, "remove animations"). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}
