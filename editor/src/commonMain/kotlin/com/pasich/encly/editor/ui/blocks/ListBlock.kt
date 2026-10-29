package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.focus.KeyboardUtils
import com.pasich.encly.editor.focus.caretLine
import com.pasich.encly.editor.focus.caretOffsetFor
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.goesUp
import com.pasich.encly.editor.theme.EDITOR_PLACEHOLDER_ALPHA
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.lineHeight
import com.pasich.encly.editor.theme.listItemGap
import com.pasich.encly.editor.theme.markerBoxSize
import com.pasich.encly.editor.theme.markerColumn
import com.pasich.encly.editor.ui.BlockActions
import com.pasich.encly.editor.ui.components.EditorCheckbox
import kotlin.math.roundToInt

/** From this many items numbers take two digits, and a wider hanging indent. */
private const val WIDE_MARKER_FROM = 10

private const val FOCUS_FRAMES = 5

/** A two-digit number's extra width, in text sizes. */
private const val WIDE_MARKER_EXTRA = 0.6f

/** Which item takes focus next, and where its cursor goes (null: where it is; [Int.MAX_VALUE]: the end). */
private data class ItemFocus(
    val itemId: String,
    val caret: Int?,
    /** A horizontal position to put the cursor at, on the item's last line when [fromBelow], else its first. */
    val caretX: Float? = null,
    val fromBelow: Boolean = false,
)

/** The list's item fields by item id, and the item to focus once it is composed. */
private class ListFocus {
    val requesters = mutableMapOf<String, FocusRequester>()
    val fieldStates = mutableMapOf<String, TextFieldState>()
    val highlights = mutableMapOf<String, FieldHighlight>()
    var pending by mutableStateOf<ItemFocus?>(null)

    /** The item whose field has focus; its cursor is reported so undo can put it back. */
    var focusedItem: String? = null
}

/**
 * The list's focus: the target the editor focuses the list through (the item a request names,
 * else its first item, or its last with the cursor at the end), and focus moves between items
 * once the item is composed.
 */
@Composable
private fun rememberListFocus(block: Block.ListBlock, blockActions: BlockActions): ListFocus {
    val focus = remember { ListFocus() }
    DisposableEffect(block, blockActions) {
        val unregister = blockActions.registerFieldsFocusTarget { request ->
            val items = block.items.value
            val item = request.itemId?.let { id -> items.firstOrNull { it.id == id } }
                // An item that is not there (undone away) is never "the first one": the nearest end is.
                ?: if (request.itemId != null || request.cursorToEnd || request.fromBelow) {
                    items.lastOrNull()
                } else {
                    items.firstOrNull()
                }
            item?.let { focus.pending = ItemFocus(it.id, request.caretOffset, request.caretX, request.fromBelow) }
        }
        onDispose { unregister() }
    }
    // A new item is composed a frame or two after the edit that added it.
    LaunchedEffect(focus.pending) {
        val request = focus.pending ?: return@LaunchedEffect
        repeat(FOCUS_FRAMES) {
            val requester = focus.requesters[request.itemId]
            if (requester != null) {
                runCatching { requester.requestFocus() }
                val stored = block.items.value.firstOrNull { it.id == request.itemId }?.value
                val state = focus.fieldStates[request.itemId]
                if (stored != null && state != null) {
                    if (request.caretX != null) {
                        val geometry = focus.highlights[request.itemId]?.geometry()
                        state.placeCaret(
                            stored,
                            caretOffsetFor(geometry, request.caretX, request.fromBelow, stored.length),
                        )
                    } else if (request.caret != null) {
                        state.placeCaret(stored, request.caret)
                    }
                }
                focus.pending = null
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
        focus.pending = null
    }
    return focus
}

/**
 * A checklist, bulleted or numbered list: one field per item. Every change goes through
 * [blockActions], so it is autosaved and can be undone. The editor focuses the list through the
 * target it registers ([BlockActions.registerFieldsFocusTarget]).
 */
@Composable
internal fun ListBlock(
    block: Block.ListBlock,
    blockActions: BlockActions,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val items by block.items.collectAsState()
    val textLocked by block.locked.collectAsState()
    val actions by rememberUpdatedState(blockActions)
    val focus = rememberListFocus(block, blockActions)
    val editor = remember(block) { ListEditor(block, { actions }) { focus.pending = it } }
    val checklist = block.blockType == BlockType.LIST_CHECK
    val theme = LocalEditorTheme.current
    val marker = rememberListMarker(block.blockType, items.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            theme.lineSpacing.listItemGap(checklist, theme.spacing.xxs),
        ),
    ) {
        items.forEachIndexed { itemIndex, item ->
            key(item.id) {
                val state = rememberSyncedTextFieldState(
                    model = { block.items.value.firstOrNull { it.id == item.id }?.value },
                    modelChanges = block.items,
                    onEdit = { text -> editor.setText(item.id, text) },
                )
                val requester = remember { FocusRequester() }
                val highlight = rememberFieldHighlight(blockActions.highlights, block.id, item.id)
                LaunchedEffect(item.id, state) {
                    snapshotFlow { state.selection.start }.collect { offset ->
                        if (focus.focusedItem == item.id) actions.onListItemCaret(item.id, offset)
                    }
                }
                DisposableEffect(item.id) {
                    focus.requesters[item.id] = requester
                    focus.fieldStates[item.id] = state
                    focus.highlights[item.id] = highlight
                    onDispose {
                        focus.highlights.remove(item.id)
                        focus.requesters.remove(item.id)
                        focus.fieldStates.remove(item.id)
                    }
                }
                ListItemRow(
                    item = item,
                    marker = marker.forItem(itemIndex + 1),
                    field = ItemField(state, highlight, marker.metrics),
                    editor = editor,
                    locks = ItemLocks(editorLocked = isLocked, textLocked = textLocked),
                    fieldModifier = fieldModifier.focusRequester(requester).onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focus.focusedItem = item.id
                            actions.onListItemCaret(item.id, state.selection.start)
                        }
                    },
                )
            }
        }
    }
}

/** The marker of a list of [count] items: the checkbox's column, grown with the text (more for two digits). */
@Composable
private fun rememberListMarker(type: BlockType, count: Int): ListMarker {
    val font = listFontDp()
    val wide = font * (if (count >= WIDE_MARKER_FROM) WIDE_MARKER_EXTRA else 0f)
    val metrics = rememberLineMetrics()
    return ListMarker(type = type, width = markerColumn(font) + wide, box = markerBoxSize(font), metrics = metrics)
}

/** What keeps a list item from being edited: the whole editor ([editorLocked]) or this checklist's text. */
private class ItemLocks(val editorLocked: Boolean, val textLocked: Boolean)

/** A list item's text field state, what Find tints in it, and its list's [metrics]. */
private class ItemField(val state: TextFieldState, val highlight: FieldHighlight, val metrics: LineMetrics)

/**
 * How a list marks its items: checkboxes, bullets or numbers in a hanging-indent column of
 * [width]; [number] is the item's own. [metrics] are measured once per list, not per item.
 */
private data class ListMarker(
    val type: BlockType,
    val width: Dp,
    val box: Dp,
    val metrics: LineMetrics,
    val number: Int = 0,
) {
    fun forItem(number: Int) = copy(number = number)
}

@Composable
private fun ListItemRow(
    item: ItemListBlock,
    marker: ListMarker,
    field: ItemField,
    editor: ListEditor,
    locks: ItemLocks,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val state = field.state
    val checklist = marker.type == BlockType.LIST_CHECK
    val placeholder = LocalEditorStrings.current.text(EditorString.LIST_ITEM_PLACEHOLDER)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = if (checklist) Alignment.Top else Alignment.CenterVertically,
    ) {
        when (marker.type) {
            BlockType.LIST_CHECK -> RowCheckMark(
                state = checkState(item, state.text.toString().ifBlank { placeholder }, locks, editor),
                marker = marker,
            )

            BlockType.LIST_NUMBER -> Marker("${marker.number}.", marker.width, LocalEditorTheme.current.dataStyle)

            // A bullet says nothing TalkBack should read out before every item.
            else -> Marker("•", marker.width, listTextStyle(), Modifier.clearAndSetSemantics { })
        }
        ListItemField(
            field = field,
            input = editor.inputFor(item.id),
            checked = checklist && item.isCheck,
            placeholder = placeholder,
            locks = locks,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (checklist) {
                        Modifier.padding(vertical = LocalEditorTheme.current.spacing.textGap)
                    } else {
                        Modifier.alignByBaseline()
                    },
                )
                .then(fieldModifier)
                .onPreviewKeyEvent { event ->
                    val selection = state.selection
                    val line = if (event.isArrow()) {
                        caretLine(field.highlight.geometry(), state.text.length, selection.start)
                    } else {
                        null
                    }
                    KeyboardUtils.handleKeyEvent(
                        event = event,
                        text = state.text,
                        cursorPosition = if (selection.collapsed) selection.start else -1,
                        onBackspaceAtStart = { editor.backspaceAtStart(item.id) },
                        onNavigateUp = { editor.moveFocus(item.id, CaretMove.Up(line?.x)) },
                        onNavigateDown = { editor.moveFocus(item.id, CaretMove.Down(line?.x)) },
                        onNavigateLeft = { editor.moveFocus(item.id, CaretMove.Left) },
                        onNavigateRight = { editor.moveFocus(item.id, CaretMove.Right) },
                        line = line,
                    )
                },
        )
    }
}

/** The list's edits, all by item id, so a pending edit can never land on the wrong item. */
private class ListEditor(
    private val block: Block.ListBlock,
    private val actions: () -> BlockActions,
    private val focusItem: (ItemFocus) -> Unit,
) {
    private val items: List<ItemListBlock> get() = block.items.value

    private fun update(mergeable: Boolean, change: MutableList<ItemListBlock>.() -> Unit) {
        actions().onListItemsChanged(items.toMutableList().apply(change), mergeable)
    }

    fun setText(itemId: String, text: String) = update(mergeable = true) {
        val at = indexOfFirst { it.id == itemId }
        if (at >= 0) this[at] = this[at].copy(value = text)
    }

    fun setChecked(itemId: String, checked: Boolean) = update(mergeable = false) {
        val at = indexOfFirst { it.id == itemId }
        if (at >= 0) this[at] = this[at].copy(isCheck = checked)
    }

    private val inputs = mutableMapOf<String, BlockInput>()

    /** The writing rules of item [itemId]'s field: Enter and pasted lines go to the editor. */
    fun inputFor(itemId: String): BlockInput =
        inputs.getOrPut(itemId) { BlockInput(BlockField.ListItem(itemId), actions) }

    /** Backspace at the start of an item (see listBackspaceAtStart). */
    fun backspaceAtStart(itemId: String): Boolean = actions().onListBackspaceAtStart(itemId)

    /**
     * A cursor key that leaves item [itemId]'s field: the item above or below takes the cursor (at the
     * same column for Up and Down), and from the first or last item the block above or below does.
     * False when there is nowhere to go; nothing is added.
     */
    fun moveFocus(itemId: String, move: CaretMove): Boolean {
        val at = items.indexOfFirst { it.id == itemId }
        val neighbour = items.getOrNull(if (move.goesUp) at - 1 else at + 1)
        if (neighbour == null) return actions().navigate(move)
        val focus = when (move) {
            is CaretMove.Up -> ItemFocus(neighbour.id, null, move.x, fromBelow = true)
            is CaretMove.Down -> ItemFocus(neighbour.id, null, move.x)
            CaretMove.Left -> ItemFocus(neighbour.id, Int.MAX_VALUE)
            CaretMove.Right -> ItemFocus(neighbour.id, 0)
        }
        focusItem(
            when {
                // Without a known column, Up lands at the end of the item above and Down at the start of the one below.
                focus.caretX == null && move is CaretMove.Up -> ItemFocus(neighbour.id, Int.MAX_VALUE)

                focus.caretX == null && move is CaretMove.Down -> ItemFocus(neighbour.id, 0)

                else -> focus
            },
        )
        return true
    }
}

@Composable
internal fun listTextStyle(): TextStyle {
    val theme = LocalEditorTheme.current
    val spacing = theme.lineSpacing
    return MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = theme.fontFamilies.body,
        fontSize = theme.fontSizes.list,
        lineHeight = spacing.lineHeight(theme.fontSizes.list, MaterialTheme.typography.bodyLarge.lineHeight),
        // The extra (or missing) line height is shared above and below the glyphs, so a line's text
        // is centred in its line box at every spacing and size: the marker centres on that box.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** A bullet or number in its hanging-indent column, on the item's first baseline. */
@Composable
private fun RowScope.Marker(text: String, width: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    val theme = LocalEditorTheme.current
    Text(
        text = text,
        style = style.copy(fontSize = theme.fontSizes.list),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .width(width)
            .alignByBaseline(),
    )
}

/**
 * The checklist box, flush with the paragraphs above, in the marker column. Its middle sits on the
 * middle of the item's first line at every line spacing and size ([LineMetrics], taken from the
 * text style, not from any item's layout, so it cannot lag or drift between rows); its side and the
 * gap after it grow with the text ([ListMarker]). The tap target is the column, widened to 48dp by
 * Compose's minimum touch size. Announced with the item's text, so TalkBack says what is checked.
 */
@Composable
private fun RowScope.RowCheckMark(state: CheckState, marker: ListMarker) {
    val metrics = marker.metrics
    val pad = with(LocalDensity.current) { LocalEditorTheme.current.spacing.textGap.toPx() }
    Layout(
        content = {
            EditorCheckbox(
                checked = state.checked,
                boxSize = marker.box,
                modifier = Modifier.testTag(CHECK_BOX_TAG),
            )
        },
        modifier = Modifier
            .toggleable(
                value = state.checked,
                enabled = state.enabled,
                role = Role.Checkbox,
                onValueChange = state.onToggle,
            )
            .semantics { contentDescription = state.label },
    ) { measurables, constraints ->
        val box = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val centre = pad + metrics.centre
        val top = (centre - box.height / 2f).roundToInt().coerceAtLeast(0)
        layout(marker.width.roundToPx(), top + box.height) { box.place(0, top) }
    }
}

private fun checkState(item: ItemListBlock, label: String, locks: ItemLocks, editor: ListEditor) =
    CheckState(item.isCheck, label, enabled = !locks.editorLocked, onToggle = { editor.setChecked(item.id, it) })

/** What a checklist box shows and does: ticked or not, the item's text for TalkBack, and its toggle. */
private class CheckState(
    val checked: Boolean,
    val label: String,
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit,
)

/** The test tag of the drawn checkbox square. */
internal const val CHECK_BOX_TAG = "checklist-box"

@Composable
private fun ListItemField(
    field: ItemField,
    input: BlockInput,
    checked: Boolean,
    placeholder: String,
    locks: ItemLocks,
    modifier: Modifier = Modifier,
) {
    val state = field.state
    val highlight = field.highlight
    val metrics = field.metrics
    val base = listTextStyle()
    val emptyShift = metrics.emptyShift.roundToInt()
    val lineHeightDp = with(LocalDensity.current) { metrics.lineHeight.toDp() }
    var focused by remember { mutableStateOf(false) }
    // Checked items stay editable; they are only muted and struck through.
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = if (checked) base.copy(color = mutedColor) else base
    val fontPx = with(LocalDensity.current) { base.fontSize.toPx() }
    BasicTextField(
        state = state,
        // A locked checklist's text cannot take focus at all, so no cursor and no keyboard.
        enabled = !locks.textLocked,
        readOnly = locks.editorLocked,
        inputTransformation = input,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = WritingKeyboard,
        onTextLayout = highlight.onTextLayout,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(highlight.modifier)
            .strikeThrough(highlight, checked, mutedColor, fontPx),
        decorator = { innerTextField ->
            // An empty field is laid out without the line height; it is lowered and given the height of
            // a typed line so a new item looks, and has its caret, where it will once typing starts.
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = lineHeightDp)) {
                if (state.text.isEmpty() && !locks.editorLocked && focused) {
                    Text(
                        text = placeholder,
                        style = base.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EDITOR_PLACEHOLDER_ALPHA),
                        ),
                    )
                }
                Box(modifier = Modifier.offset { IntOffset(0, if (state.text.isEmpty()) emptyShift else 0) }) {
                    innerTextField()
                }
            }
        },
    )
}
