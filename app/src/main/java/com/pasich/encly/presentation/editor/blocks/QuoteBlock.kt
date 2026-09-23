package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.bodyNote

/** [focusRequester] targets the quote's text field, not the row that draws the quote bar. */
@Composable
fun QuoteBlock(
    block: Block.QuoteBlock,
    blockActions: BlockActions,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val text by block.text.collectAsState()
    val fontStyles = rememberFontStyles()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
        )
        var textFieldValue by rememberBlockTextFieldValue(text, blockActions)

        BasicTextField(
            value = textFieldValue,
            enabled = !isLocked,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            onValueChange = { newValue ->
                textFieldValue = newValue
                // Records the edit for autosave and undo/redo
                blockActions.onTextChanged(newValue.text)
            },
            textStyle = bodyNote.copy(
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                fontSize = fontStyles.sizes.quote,
                fontFamily = fontStyles.families.body,
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f)
                .textBlockKeys(text, blockActions, enterAddsParagraph = false)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(bottomEnd = 10.dp, topEnd = 10.dp),
                )
                .padding(8.dp),
            decorationBox = { innerTextField ->
                QuoteDecoration(text.isEmpty(), innerTextField)
            },
        )
    }
}

@Composable
private fun QuoteDecoration(showPlaceholder: Boolean, innerTextField: @Composable () -> Unit) {
    val fontStyles = rememberFontStyles()
    Box(modifier = Modifier.fillMaxWidth()) {
        if (showPlaceholder) {
            Text(
                text = stringResource(R.string.quote),
                style = bodyNote.copy(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6F),
                    fontStyle = FontStyle.Italic,
                    fontSize = fontStyles.sizes.quote,
                    fontFamily = fontStyles.families.body,
                ),
            )
        }
        innerTextField()
    }
}
