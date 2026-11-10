package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.theme.EDITOR_PLACEHOLDER_ALPHA
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.theme.lineHeight
import com.pasich.encly.editor.ui.BlockActions

/** What the Tab key types into code: four spaces (code never holds a tab by accident; a pasted one is kept). */
internal const val CODE_TAB = "    "

/**
 * Keyboard of a code field: no capital letters, no autocorrect. Suggestions are the keyboard's own to
 * offer; the app already asks keyboards not to learn from what is typed (SecureTextInputBoundary).
 */
internal val CodeKeyboard: KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
)

/**
 * Code, in a monospace face on a tonal card. It reads left to right whatever the app's language is, keeps
 * every space, tab and line break as typed (Enter adds a line, never a block), and Tab types [CODE_TAB].
 * Its placeholder shows only while it is [showPlaceholder] (focused) and empty.
 */
@Composable
internal fun CodeBlock(
    block: Block.CodeBlock,
    blockActions: BlockActions,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    showPlaceholder: Boolean = true,
) {
    val theme = LocalEditorTheme.current
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = theme.fontSizes.list,
        fontFamily = FontFamily.Monospace,
        lineHeight = theme.lineSpacing.lineHeight(theme.fontSizes.list, MaterialTheme.typography.bodyLarge.lineHeight),
    )
    val actions by rememberUpdatedState(blockActions)
    val defer = rememberDefer()
    val input = remember { BlockInput(BlockField.Code, defer) { actions } }
    val highlight = rememberFieldHighlight(blockActions.highlights, block.id)
    RegisterCaretAtX(blockActions, state, highlight)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            state = state,
            readOnly = isLocked,
            inputTransformation = input,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = CodeKeyboard,
            onTextLayout = highlight.onTextLayout,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = theme.spacing.xs)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = theme.spacing.fieldGap, vertical = theme.spacing.m)
                .then(highlight.modifier)
                .codeTabKey(state, enabled = !isLocked)
                .textBlockKeys(state, blockActions, highlight),
            decorator = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (showPlaceholder && state.text.isEmpty()) {
                        Text(
                            text = LocalEditorStrings.current.text(EditorString.BLOCK_CODE),
                            style = style.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = EDITOR_PLACEHOLDER_ALPHA,
                                ),
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Tab in a code field types [CODE_TAB] over the selection instead of moving the focus to the next control.
 * Tab with Shift or any other modifier is left alone.
 */
internal fun Modifier.codeTabKey(state: TextFieldState, enabled: Boolean): Modifier = onPreviewKeyEvent { event ->
    val plain = !(event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
    val tab = event.key == Key.Tab && plain
    if (tab && event.type == KeyEventType.KeyDown && enabled) {
        state.edit {
            val at = selection.min
            replace(at, selection.max, CODE_TAB)
            selection = TextRange(at + CODE_TAB.length)
        }
    }
    tab
}
