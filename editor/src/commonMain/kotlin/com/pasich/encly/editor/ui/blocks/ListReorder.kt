package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.MAX_LIST_LEVEL
import com.pasich.encly.editor.model.siblingOffsetAt
import com.pasich.encly.editor.model.siblingStarts
import com.pasich.encly.editor.model.subtreeEnd
import com.pasich.encly.editor.theme.LocalEditorTheme

// Reordering and nesting list items by hand: the drag handle at the end of an item, TalkBack's actions on it, and the
// keys that indent, outdent and move the item the cursor is in.

/** The width of an item's drag handle, which is also the strip a drag or long press starts in. */
internal val ReorderHandleWidth = 28.dp
private val GripSize = 18.dp
private const val GRIP_ALPHA = 0.55f
private val DropLineWidth = 2.dp

/** The test tag of an item's drag handle. */
internal const val ITEM_HANDLE_TAG = "list-item-handle"

/** What a key does to the list item the cursor is in. */
internal enum class ListItemKey { INDENT, OUTDENT, MOVE_UP, MOVE_DOWN }

/**
 * The list-item key for [key] going [type] with the modifiers held ([command] is Ctrl or Cmd), null for any other. Tab
 * indents and Shift+Tab outdents; Alt+Shift with Up or Down moves the item. The block move chord (Ctrl or Cmd+Shift+Up
 * or Down) is a different one, and so is a plain Shift or Alt+arrow, which the text field keeps.
 */
internal fun listItemKeyOf(key: Key, type: KeyEventType, command: Boolean, alt: Boolean, shift: Boolean): ListItemKey? {
    if (type != KeyEventType.KeyDown || command) return null
    return when {
        key == Key.Tab && !alt -> if (shift) ListItemKey.OUTDENT else ListItemKey.INDENT
        key == Key.DirectionUp && alt && shift -> ListItemKey.MOVE_UP
        key == Key.DirectionDown && alt && shift -> ListItemKey.MOVE_DOWN
        else -> null
    }
}

/** The list-item key [this] event is, if any. */
internal fun KeyEvent.listItemKey(): ListItemKey? =
    listItemKeyOf(key, type, command = isCtrlPressed || isMetaPressed, alt = isAltPressed, shift = isShiftPressed)

/** What can be done to one item: which ways it can move, and whether it can go a level deeper or out. */
internal class ItemMoves(val up: Boolean, val down: Boolean, val indent: Boolean, val outdent: Boolean)

/** What each of [rows] (items with valid levels) can do, found in one pass over the list. */
internal fun movesOf(rows: List<ItemListBlock>): List<ItemMoves> {
    val seen = BooleanArray(MAX_LIST_LEVEL + 1)
    val hasBefore = rows.map { row ->
        val level = row.level
        seen[level].also {
            seen[level] = true
            for (deeper in level + 1..MAX_LIST_LEVEL) seen[deeper] = false
        }
    }
    seen.fill(false)
    val hasAfter = rows.asReversed().map { row ->
        val level = row.level
        seen[level].also {
            seen[level] = true
            for (deeper in level + 1..MAX_LIST_LEVEL) seen[deeper] = false
        }
    }.asReversed()
    return rows.mapIndexed { at, row ->
        val deepest = (at until rows.subtreeEnd(at)).maxOf { rows[it].level }
        ItemMoves(
            up = hasBefore[at],
            down = hasAfter[at],
            indent = at > 0 && row.level <= rows[at - 1].level && deepest < MAX_LIST_LEVEL,
            outdent = row.level > 0,
        )
    }
}

/**
 * What an item of a list can do and be asked to do: the list's [drag], what this item can do ([moves]), whether the
 * list may be changed at all ([editable]), whether it has several items to reorder ([reorderable]), and the two
 * edits, [onMove] and [onShift].
 */
internal class ItemContext(
    val drag: ListDrag,
    val moves: ItemMoves,
    val editable: Boolean,
    val reorderable: Boolean,
    val onMove: (itemId: String, by: Int) -> Unit,
    val onShift: (itemId: String, deeper: Boolean) -> Unit,
)

/**
 * Carries out [event] on item [itemId] if it is a list-item key (see [listItemKeyOf]). The key is taken whether or not
 * the item can go that way, so Tab never leaves the list for the next field half way through editing.
 */
internal fun ItemContext.handleKey(event: KeyEvent, itemId: String): Boolean {
    val key = event.listItemKey() ?: return false
    when (key) {
        ListItemKey.INDENT -> onShift(itemId, true)
        ListItemKey.OUTDENT -> onShift(itemId, false)
        ListItemKey.MOVE_UP -> onMove(itemId, -1)
        ListItemKey.MOVE_DOWN -> onMove(itemId, 1)
    }
    return true
}

/**
 * A drag of one item in progress: which item moves (with the ones nested under it), how far the pointer has taken it,
 * and where each row sits, so that dropping it can tell how many places it moved. The [rows] of the list are read when
 * it matters.
 */
internal class ListDrag(private val rows: () -> List<ItemListBlock>) {
    /** The item being dragged; null while none is. */
    var itemId by mutableStateOf<String?>(null)
        private set

    /** The items that move with it: the item and those nested under it. */
    var moving by mutableStateOf(emptySet<String>())
        private set

    /** How far the pointer has taken the item from where it started, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    private val tops = mutableMapOf<String, Float>()
    private val bottoms = mutableMapOf<String, Float>()

    /** Records where row [id] sits in the list, from its top to its bottom. */
    fun place(id: String, top: Float, bottom: Float) {
        tops[id] = top
        bottoms[id] = bottom
    }

    fun begin(id: String) {
        val rows = rows()
        val at = rows.indexOfFirst { it.id == id }
        if (at < 0) return
        itemId = id
        offset = 0f
        moving = (at until rows.subtreeEnd(at)).mapTo(HashSet()) { rows[it].id }
    }

    fun by(dy: Float) {
        offset += dy
    }

    /** How many places among its siblings the dragged item is now over: negative is up. */
    @Suppress("ReturnCount") // Whatever is missing, nothing is dragged or its place is not known: no move.
    fun placesMoved(): Int {
        val rows = rows()
        val id = itemId ?: return 0
        val at = rows.indexOfFirst { it.id == id }
        if (at < 0) return 0
        val top = tops[id] ?: return 0
        val bottom = bottoms[rows[rows.subtreeEnd(at) - 1].id] ?: return 0
        val centre = (top + bottom) / 2 + offset
        return rows.siblingOffsetAt(at, rows.map { tops[it.id] ?: 0f }, rows.map { bottoms[it.id] ?: 0f }, centre)
    }

    /** The vertical position of the line that shows where the dragged item would land; null when it would not move. */
    @Suppress("ReturnCount") // No drag, or no move: no line.
    fun dropLineAt(): Float? {
        val rows = rows()
        val id = itemId ?: return null
        val at = rows.indexOfFirst { it.id == id }
        val places = placesMoved()
        if (at < 0 || places == 0) return null
        val siblings = rows.siblingStarts(at)
        val target = siblings[siblings.indexOf(at) + places]
        return if (places < 0) tops[rows[target].id] else bottoms[rows[rows.subtreeEnd(target) - 1].id]
    }

    fun end() {
        itemId = null
        moving = emptySet()
        offset = 0f
    }
}

/**
 * Draws a line across the list at the height [y] gives, where a dragged item would land; nothing while it gives
 * null. [y] is read while drawing, so a drag redraws the line without recomposing the list.
 */
internal fun Modifier.dropLine(color: Color, y: () -> Float?): Modifier = drawWithContent {
    drawContent()
    y()?.let { line -> drawLine(color, Offset(0f, line), Offset(size.width, line), strokeWidth = DropLineWidth.toPx()) }
}

/** Reports the place of a row of item [id] to [drag], and moves the row (with those nested under it) while dragged. */
internal fun Modifier.dragged(drag: ListDrag, id: String): Modifier = this
    .onGloballyPositioned { coordinates ->
        val top = coordinates.positionInParent().y
        drag.place(id, top, top + coordinates.size.height)
    }
    .zIndex(if (id in drag.moving) 1f else 0f)
    .graphicsLayer { if (id in drag.moving) translationY = drag.offset }

/**
 * TalkBack's actions on item [itemId]: move it up or down and indent or outdent it, each only where the item can go
 * that way and the list may be changed.
 */
@Composable
private fun itemActions(itemId: String, context: ItemContext): List<CustomAccessibilityAction> {
    val strings = LocalEditorStrings.current
    val moves = context.moves
    val movable = context.editable && context.reorderable
    return buildList {
        fun action(label: EditorString, run: () -> Unit) =
            add(CustomAccessibilityAction(strings.text(label)) { run().let { true } })
        if (movable && moves.up) action(EditorString.LIST_ITEM_MOVE_UP) { context.onMove(itemId, -1) }
        if (movable && moves.down) action(EditorString.LIST_ITEM_MOVE_DOWN) { context.onMove(itemId, 1) }
        if (context.editable && moves.indent) action(EditorString.LIST_ITEM_INDENT) { context.onShift(itemId, true) }
        if (context.editable && moves.outdent) action(EditorString.LIST_ITEM_OUTDENT) { context.onShift(itemId, false) }
    }
}

/**
 * The handle of item [itemId] at the end of its row, aligned with the first line ([lineHeight] high, below the
 * [checklist]'s padding), shown while the row is [active] (written in or hovered) or the item is dragged. Dropping
 * it moves the item in the list.
 */
@Composable
internal fun RowScope.ItemHandle(
    itemId: String,
    context: ItemContext,
    active: Boolean,
    checklist: Boolean,
    lineHeight: Dp,
) {
    val actions = itemActions(itemId, context)
    ItemDragHandle(
        itemId = itemId,
        drag = context.drag,
        spec = HandleSpec(
            visible = active || context.drag.itemId == itemId,
            label = LocalEditorStrings.current.text(EditorString.LIST_ITEM_REORDER),
            height = lineHeight,
            topPadding = if (checklist) LocalEditorTheme.current.spacing.textGap else 0.dp,
        ),
        onDrop = { places -> context.onMove(itemId, places) },
        modifier = Modifier.align(Alignment.Top),
        semantics = { customActions = actions },
    )
}

/** How a handle looks: whether it is [visible], its [label] and size. */
internal class HandleSpec(val visible: Boolean, val label: String, val height: Dp, val topPadding: Dp)

/**
 * The handle of an item: a grip, shown while [HandleSpec.visible]. A mouse drags it at once and shows a hand over it;
 * a finger drags after a long press. The item follows through [drag]; letting go asks [onDrop] to move it by the
 * number of places it was taken. The label is what TalkBack reads, with whatever [semantics] adds.
 */
@Composable
internal fun ItemDragHandle(
    itemId: String,
    drag: ListDrag,
    spec: HandleSpec,
    onDrop: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semantics: SemanticsPropertyReceiver.() -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val currentDrop by rememberUpdatedState(onDrop)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(top = spec.topPadding)
            .width(ReorderHandleWidth)
            .height(spec.height)
            .pointerHoverIcon(PointerIcon.Hand)
            .testTag(ITEM_HANDLE_TAG)
            .semantics {
                contentDescription = spec.label
                semantics()
            }
            .pointerInput(itemId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = if (down.type == PointerType.Mouse) {
                        awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    } else {
                        awaitLongPressOrCancellation(down.id)
                    }
                    if (start != null) {
                        if (down.type != PointerType.Mouse) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.begin(itemId)
                        // A mouse has already moved past the slop by now; the item goes with it.
                        drag.by(start.position.y - down.position.y)
                        val finished = drag(start.id) { change ->
                            drag.by(change.positionChange().y)
                            change.consume()
                        }
                        val places = if (finished) drag.placesMoved() else 0
                        drag.end()
                        if (places != 0) currentDrop(places)
                    }
                }
            },
    ) {
        if (spec.visible) {
            Icon(
                LocalEditorTheme.current.icons.grip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GRIP_ALPHA),
                modifier = Modifier.width(GripSize).height(GripSize),
            )
        }
    }
}
