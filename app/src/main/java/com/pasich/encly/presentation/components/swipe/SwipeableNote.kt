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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.swipe_delete_message
import com.pasich.encly.core.resources.swipe_delete_title
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
 * [SwipeSettings]; right is start to end, so it follows the layout direction) is armed past
 * [SWIPE_THRESHOLD] and runs once, on release, through [perform]. Behind the card a background in
 * the action's own colour and icon is revealed, the icon growing and fading in as the threshold
 * nears, with a haptic tick when it is crossed. Archive slides the card out once the write
 * succeeded; pin and colour spring it back; delete springs it back too and asks first; a failed
 * write springs it back with a buzz. The same actions are offered to TalkBack as custom actions on
 * the card, where a swipe is not an option.
 *
 * [content] draws the card and takes the modifier that carries those accessibility actions.
 * [reducedMotion] (the system's "remove animations") drops the decorative motion; read it once per list.
 */
@Composable
fun SwipeableNote(
    note: Note,
    swipe: SwipeContext,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val settings = swipe.settings
    val perform = swipe.perform
    val reducedMotion = swipe.reducedMotion
    val currentNote by rememberUpdatedState(note)
    val currentPerform by rememberUpdatedState(perform)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val right = settings.actionFor(SwipeSide.RIGHT).effectiveFor(note)
    val left = settings.actionFor(SwipeSide.LEFT).effectiveFor(note)

    /** Runs [action] on the note; true when the card should slide out. */
    suspend fun run(action: SwipeAction): Boolean {
        val target = currentNote
        val effective = action.effectiveFor(target)
        val event = effective.toEvent(target)
        return when {
            // Deleting asks first; the card stays where it is until the user confirms.
            effective == SwipeAction.DELETE -> false.also { confirmDelete = true }

            event == null -> false

            else -> currentPerform(event).also { done ->
                if (!done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } && effective.slidesOut()
        }
    }

    val directions = remember(right, left, rtl) { SwipeDirections(right, left, rtl) }
    val drag = rememberSwipeState(directions, note, reducedMotion, ::run)
    val accessibilityActions = listOf(right, left).filter { it != SwipeAction.NONE }.distinct()
        .map { action -> action to stringResource(action.noteLabel(note)) }
    val cardModifier = remember(accessibilityActions) {
        if (accessibilityActions.isEmpty()) {
            Modifier
        } else {
            Modifier.semantics {
                customActions = accessibilityActions.map { (action, label) ->
                    CustomAccessibilityAction(label) {
                        scope.launch { run(action) }
                        true
                    }
                }
            }
        }
    }

    ConfirmDialog(
        isVisible = confirmDelete,
        titleText = stringResource(Res.string.swipe_delete_title),
        messageText = stringResource(Res.string.swipe_delete_message),
        onConfirm = {
            confirmDelete = false
            scope.launch { perform(NoteListEvent.NoteToTrash(note)) }
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
        SwipeBackground(drag, directions, note, reducedMotion, Modifier.matchParentSize())
        // The offset is read while placing, not while composing: a drag frame does not recompose the card.
        // absoluteOffset: the drag is physical, so the card must not mirror in right-to-left layouts.
        Box(modifier = Modifier.absoluteOffset { IntOffset(drag.offset.value.roundToInt(), 0) }) {
            content(cardModifier)
        }
    }
}

/** What each swipe does, as the layout reads them: [right] is start to end, so [rtl] flips the physical side. */
@Stable
private class SwipeDirections(val right: SwipeAction, val left: SwipeAction, val rtl: Boolean) {
    /** The action of a drag to the physical right ([toRight]) or left. */
    fun actionAt(toRight: Boolean): SwipeAction = if (toRight != rtl) right else left
}

/**
 * The drag of one card: its offset, which only goes toward a direction that has an action, and
 * what happens on release. Past the threshold the action runs once, then the card slides out
 * (archive, once it succeeded) or springs back; short of it, it springs back without running anything.
 * The return never bounces: an overshoot would show the opposite side's background.
 */
@Stable
private class SwipeState(
    private val scope: CoroutineScope,
    private val directions: () -> SwipeDirections,
    private val run: suspend (SwipeAction) -> Boolean,
    private val reduced: () -> Boolean,
) {
    val offset = Animatable(0f)
    var width by mutableFloatStateOf(1f)

    /** The side the card is dragged to is toward the physical right. */
    val toRight: Boolean get() = offset.value > 0f

    val draggable = DraggableState { delta ->
        val next = offset.value + delta
        val allowed = next == 0f || directions().actionAt(next > 0f) != SwipeAction.NONE
        if (allowed) scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(next.coerceIn(-width, width)) }
    }

    fun release() {
        val current = offset.value
        val action = directions().actionAt(current > 0f)
        val armed = abs(current) / width >= SWIPE_THRESHOLD && action != SwipeAction.NONE
        scope.launch {
            val slidesOut = armed && run(action)
            animateTo(if (slidesOut) width * sign(current) else 0f)
        }
    }

    suspend fun comeBack() = animateTo(0f)

    private suspend fun animateTo(target: Float) {
        offset.animateTo(target, if (reduced()) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy))
    }
}

@Composable
private fun rememberSwipeState(
    directions: SwipeDirections,
    note: Note,
    reduced: Boolean,
    run: suspend (SwipeAction) -> Boolean,
): SwipeState {
    val scope = rememberCoroutineScope()
    val currentDirections by rememberUpdatedState(directions)
    val currentRun by rememberUpdatedState(run)
    val currentReduced by rememberUpdatedState(reduced)
    val state = remember(scope) {
        SwipeState(scope, { currentDirections }, { currentRun(it) }, { currentReduced })
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

/**
 * The panel revealed behind a dragged card: the action's colour, with its icon at the near edge. It
 * reads the drag in drawing and effects, never in composition, so a drag frame does not recompose it;
 * it recomposes only when the card is dragged to the other side.
 */
@Composable
private fun SwipeBackground(
    drag: SwipeState,
    directions: SwipeDirections,
    note: Note,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val toRight by remember(drag) { derivedStateOf { drag.toRight } }
    val action = directions.actionAt(toRight)
    val look = swipeLook(action, note)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(drag, action) {
        snapshotFlow { abs(drag.offset.value) / drag.width >= SWIPE_THRESHOLD && action != SwipeAction.NONE }
            .distinctUntilChanged()
            .collect { armed -> if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    // How close the drag is to arming the action; with reduced motion the icon simply stands there.
    fun near(): Float = if (reduced) 1f else (abs(drag.offset.value) / drag.width / SWIPE_THRESHOLD).coerceIn(0f, 1f)
    Box(
        contentAlignment = if (toRight != (LocalLayoutDirection.current == LayoutDirection.Rtl)) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
        modifier = modifier
            .graphicsLayer { alpha = if (drag.offset.value == 0f || action == SwipeAction.NONE) 0f else 1f }
            .clip(MaterialTheme.shapes.medium)
            .background(look.container),
    ) {
        Icon(
            look.icon,
            contentDescription = null,
            tint = look.content,
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.l)
                .graphicsLayer {
                    val scale = ICON_MIN_SCALE + (1f - ICON_MIN_SCALE) * near()
                    scaleX = scale
                    scaleY = scale
                    alpha = near()
                },
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
