package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.focus.KeyboardUtils
import com.pasich.encly.editor.focus.caretLine
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.TableCell
import com.pasich.encly.editor.model.TableData
import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.CellStep
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.TableEdit
import com.pasich.encly.editor.state.addColumnAfter
import com.pasich.encly.editor.state.addRowBelow
import com.pasich.encly.editor.state.cellText
import com.pasich.encly.editor.state.enterFrom
import com.pasich.encly.editor.state.neighborOf
import com.pasich.encly.editor.state.removeColumnOf
import com.pasich.encly.editor.state.removeRowOf
import com.pasich.encly.editor.state.tabFrom
import com.pasich.encly.editor.state.textDiff
import com.pasich.encly.editor.state.toggleHeader
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.lineHeight
import com.pasich.encly.editor.ui.BlockActions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Every character a text field may hold as a line break. */
private val LINE_BREAK_CHARS = setOf('\n', '\r', '\u2028', '\u2029')

/** The width of a column; a table wider than the screen scrolls sideways. */
private val CellWidth = 148.dp
private val CellMinHeight = 44.dp
private val CellBorder = 0.75.dp
private val MenuButtonPadding = 4.dp

/** Test tag of the button that opens a table's cell menu. */
internal const val TABLE_MENU_TAG = "table-menu"

/** Which cell takes focus next, and where its cursor goes (null: where it is; [Int.MAX_VALUE]: the end). */
private data class CellFocus(val cellId: String, val caret: Int?)

/** The table's cell fields by cell id, the cell that has focus, and the cell to focus once it is composed. */
private class TableFocus {
    val requesters = mutableMapOf<String, FocusRequester>()
    val states = mutableMapOf<String, TextFieldState>()
    var pending by mutableStateOf<CellFocus?>(null)

    /** The cell whose field has focus or had it last: where the cell menu acts. */
    var current by mutableStateOf<String?>(null)
}

/** The cell a request for the whole table means: the one it names, else by where the cursor comes from. */
internal fun TableData.cellFor(itemId: String?, cursorToEnd: Boolean, fromBelow: Boolean): TableCell {
    val named = itemId?.let(::cell)
    return when {
        named != null -> named
        fromBelow -> rows.last().cells.first()
        itemId != null || cursorToEnd -> rows.last().cells.last()
        else -> rows.first().cells.first()
    }
}

/** The focus target of the table (what the editor asks for) and the carrying out of its pending focus. */
@Composable
private fun rememberTableFocus(block: Block.TableBlock, blockActions: BlockActions): TableFocus {
    val focus = remember { TableFocus() }
    DisposableEffect(block, blockActions) {
        val unregister = blockActions.registerFieldsFocusTarget { request ->
            val cell = block.table.value.cellFor(request.itemId, request.cursorToEnd, request.fromBelow)
            focus.pending = CellFocus(cell.id, request.caretOffset)
        }
        onDispose { unregister() }
    }
    // A new cell is composed a frame or two after the edit that added it.
    LaunchedEffect(focus.pending) {
        val request = focus.pending ?: return@LaunchedEffect
        focus.pending = null
        val requester = focus.requesters[request.cellId] ?: return@LaunchedEffect
        runCatching { requester.requestFocus() }
        val stored = block.table.value.cell(request.cellId)?.value
        val state = focus.states[request.cellId]
        if (stored != null && state != null && request.caret != null) state.placeCaret(stored, request.caret)
    }
    return focus
}

/**
 * A table: a grid of plain-text cells that scrolls sideways when it is wider than the screen (it starts at the
 * reading side, so a right-to-left language mirrors the columns). Tab and Shift+Tab move between the cells, Enter
 * goes down a row (adding one at the end), the arrow keys leave a cell at its edges, and a small menu adds or
 * deletes rows and columns and switches the header row. Every change goes through [onTableChange], so it is
 * saved and can be undone; the editor focuses the table through the target it registers
 * ([BlockActions.registerFieldsFocusTarget]).
 */
@Composable
internal fun TableBlock(
    block: Block.TableBlock,
    blockActions: BlockActions,
    onTableChange: (TableData, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val table by block.table.collectAsState()
    val change by rememberUpdatedState(onTableChange)
    val focus = rememberTableFocus(block, blockActions)
    val defer = rememberDefer()
    val commands = remember(block) {
        TableCommands(block, focus) { edit ->
            change(edit.table, false)
            edit.focusCellId?.let { focus.pending = CellFocus(it, Int.MAX_VALUE) }
        }
    }
    val context = CellContext(block, blockActions, focus, commands, defer)
    Column(modifier = modifier.fillMaxWidth()) {
        if (!isLocked) CellMenu(commands, table.header, modifier = Modifier.align(Alignment.End))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.border(CellBorder, MaterialTheme.colorScheme.outlineVariant)) {
                table.rows.forEachIndexed { rowIndex, row ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.cells.forEach { cell ->
                            key(cell.id) {
                                TableCellField(
                                    cell = cell,
                                    isHeader = table.header && rowIndex == 0,
                                    context = context,
                                    onValue = { text -> change(block.table.value.withCell(cell.id, text), true) },
                                    isLocked = isLocked,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the commands of a table act on: the cell the cursor is in (or last was), else the first one. */
private class TableCommands(
    private val block: Block.TableBlock,
    private val focus: TableFocus,
    private val apply: (TableEdit) -> Unit,
) {
    private fun cellId(): String = focus.current?.takeIf { block.table.value.cell(it) != null }
        ?: block.table.value.rows.first().cells.first().id

    fun addRow() = block.table.value.addRowBelow(cellId())?.let(apply)

    fun addColumn() = block.table.value.addColumnAfter(cellId())?.let(apply)

    fun removeRow() = block.table.value.removeRowOf(cellId())?.let(apply)

    fun removeColumn() = block.table.value.removeColumnOf(cellId())?.let(apply)

    fun toggleHeader() = apply(block.table.value.toggleHeader())

    /** Enter in [cellId]: down a row, adding one at the end. */
    fun enter(cellId: String) = block.table.value.enterFrom(cellId)?.let(apply)

    /** Tab in [cellId]: the next cell, adding a row after the last. */
    fun tab(cellId: String) = block.table.value.tabFrom(cellId)?.let(apply)

    /** Moves the cursor [step] cells away from [cellId]; false at the edge of the table. */
    fun step(cellId: String, step: CellStep, caret: Int): Boolean {
        val target = block.table.value.neighborOf(cellId, step) ?: return false
        focus.pending = CellFocus(target, caret)
        return true
    }
}

/** What a cell needs of its table. */
private class CellContext(
    val block: Block.TableBlock,
    val blockActions: BlockActions,
    val focus: TableFocus,
    val commands: TableCommands,
    val defer: (() -> Unit) -> Unit,
)

/** The small button at the table's end that opens the menu of row, column and header commands. */
@Composable
private fun CellMenu(commands: TableCommands, header: Boolean, modifier: Modifier = Modifier) {
    val strings = LocalEditorStrings.current
    val theme = LocalEditorTheme.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Icon(
            theme.icons.grip,
            contentDescription = strings.text(EditorString.TABLE_CELL_OPTIONS),
            modifier = Modifier
                .testTag(TABLE_MENU_TAG)
                .clickable { open = true }
                .padding(MenuButtonPadding)
                .size(theme.spacing.iconMedium),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(strings.text(EditorString.TABLE_ADD_ROW)) {
                open = false
                commands.addRow()
            }
            MenuRow(strings.text(EditorString.TABLE_ADD_COLUMN)) {
                open = false
                commands.addColumn()
            }
            MenuRow(strings.text(EditorString.TABLE_REMOVE_ROW)) {
                open = false
                commands.removeRow()
            }
            MenuRow(strings.text(EditorString.TABLE_REMOVE_COLUMN)) {
                open = false
                commands.removeColumn()
            }
            MenuRow(strings.text(EditorString.TABLE_HEADER_ROW), checked = header) {
                open = false
                commands.toggleHeader()
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, checked: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
        onClick = onClick,
        trailingIcon = { if (checked) Icon(LocalEditorTheme.current.icons.check, contentDescription = null) },
    )
}

/** One cell: its text field, kept in step with the stored text, with the keys that move between cells. */
@Composable
private fun TableCellField(
    cell: TableCell,
    isHeader: Boolean,
    context: CellContext,
    onValue: (String) -> Unit,
    isLocked: Boolean,
) {
    val theme = LocalEditorTheme.current
    val block = context.block
    val actions by rememberUpdatedState(context.blockActions)
    val state = rememberSyncedTextFieldState(
        model = { block.table.value.cell(cell.id)?.value },
        modelChanges = remember(block, cell.id) { block.table.map { it.cell(cell.id)?.value }.distinctUntilChanged() },
        onEdit = onValue,
    )
    val requester = remember { FocusRequester() }
    val highlight = rememberFieldHighlight(context.blockActions.highlights, block.id, cell.id)
    val focus = context.focus
    RegisterCell(cell.id, CellField(state, requester), context)
    val input = remember(context.commands) { CellInput(context.defer) { context.commands.enter(cell.id) } }
    val style = cellTextStyle(isHeader)
    val background = if (isHeader) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    ReadingDirection(text = { state.text }) {
        Box(
            modifier = Modifier
                .width(CellWidth)
                .heightIn(min = CellMinHeight)
                .fillMaxHeight()
                .border(CellBorder, MaterialTheme.colorScheme.outlineVariant)
                .background(background)
                .padding(theme.spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                state = state,
                readOnly = isLocked,
                inputTransformation = input,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = WritingKeyboard,
                onTextLayout = highlight.onTextLayout,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focus.current = cell.id
                            actions.onListItemCaret(cell.id, state.selection.start)
                        }
                    }
                    .then(highlight.modifier)
                    .cellKeys(cell.id, state, context, highlight),
            )
        }
    }
}

/** The cell's field, as the table finds it to focus it and to place its cursor. */
private class CellField(val state: TextFieldState, val requester: FocusRequester)

/** Lets the table find the field of cell [id] while it is composed, and tells the editor where its cursor is. */
@Composable
private fun RegisterCell(id: String, field: CellField, context: CellContext) {
    val focus = context.focus
    val actions by rememberUpdatedState(context.blockActions)
    DisposableEffect(id) {
        focus.requesters[id] = field.requester
        focus.states[id] = field.state
        onDispose {
            focus.requesters.remove(id)
            focus.states.remove(id)
        }
    }
    LaunchedEffect(id, field.state) {
        snapshotFlow { field.state.selection.start }.collect { offset ->
            if (focus.current == id) actions.onListItemCaret(id, offset)
        }
    }
}

/** A cell's text: the list's size, the body font, heavier in the header row. */
@Composable
private fun cellTextStyle(isHeader: Boolean): TextStyle {
    val theme = LocalEditorTheme.current
    return MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = theme.fontSizes.list,
        fontFamily = theme.fontFamilies.body,
        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        lineHeight = theme.lineSpacing.lineHeight(theme.fontSizes.list, MaterialTheme.typography.bodyLarge.lineHeight),
    )
}

/**
 * The cell's keys: Tab and Shift+Tab move to the next and previous cell (Tab in the last cell adds a row), and the
 * arrow keys leave the cell only from its edges, to the cell beside or above or below it, or past the table to the
 * block before or after it.
 */
private fun Modifier.cellKeys(
    cellId: String,
    state: TextFieldState,
    context: CellContext,
    highlight: FieldHighlight,
): Modifier = onPreviewKeyEvent { event ->
    val selection = state.selection
    val line = if (event.isArrow()) caretLine(highlight.geometry(), state.text.length, selection.end) else null
    val commands = context.commands
    val actions = context.blockActions
    when {
        event.key == Key.Tab && event.type == KeyEventType.KeyDown ->
            if (event.isShiftPressed) {
                commands.step(cellId, CellStep.PREVIOUS, caret = Int.MAX_VALUE)
            } else {
                true.also { commands.tab(cellId) }
            }

        event.key == Key.Tab -> true

        else -> KeyboardUtils.handleKeyEvent(
            event = event,
            text = state.text,
            cursorPosition = if (selection.collapsed) selection.start else -1,
            onNavigateUp = {
                commands.step(cellId, CellStep.UP, caret = Int.MAX_VALUE) ||
                    actions.navigate(CaretMove.Up(line?.x))
            },
            onNavigateDown = {
                commands.step(cellId, CellStep.DOWN, caret = 0) ||
                    actions.navigate(CaretMove.Down(line?.x))
            },
            onNavigateLeft = {
                commands.step(cellId, CellStep.PREVIOUS, caret = Int.MAX_VALUE) || actions.navigate(CaretMove.Left)
            },
            onNavigateRight = { commands.step(cellId, CellStep.NEXT, caret = 0) || actions.navigate(CaretMove.Right) },
            line = line,
        )
    }
}

/**
 * The writing rules of a cell: a cell holds one line. A typed Enter goes to the cell below (a new row after the
 * last one) and leaves the text as it was; line breaks in pasted text become spaces.
 */
private class CellInput(private val defer: (() -> Unit) -> Unit, private val onEnter: () -> Unit) :
    InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val new = asCharSequence().toString()
        if (new.none { it in LINE_BREAK_CHARS }) return
        val old = originalText.toString()
        val diff = textDiff(old, new, if (selection.collapsed) selection.start else -1)
        if (LineBreak.of(old, diff)?.isEnter == true) {
            revertAllChanges()
            defer(onEnter)
        } else {
            replace(0, length, cellText(new))
            placeCursorAtEnd()
        }
    }
}
