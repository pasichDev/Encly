package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Quote
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

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

    var textFieldValue by rememberBlockTextFieldValue(text, blockActions)
    val quoteStyle = EnclyTheme.typography.quote.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontStyles.sizes.quote,
    )

    // A tonal card with the quote mark in `primary` (design spec §4.4).
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = EnclyTheme.spacing.fieldGap, vertical = EnclyTheme.spacing.m),
    ) {
        Icon(
            Lucide.Quote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
        )
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
            textStyle = quoteStyle,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .textBlockKeys(text, blockActions, enterAddsParagraph = false),
            decorationBox = { innerTextField ->
                QuoteDecoration(text.isEmpty(), quoteStyle, innerTextField)
            },
        )
    }
}

@Composable
private fun QuoteDecoration(showPlaceholder: Boolean, style: TextStyle, innerTextField: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (showPlaceholder) {
            Text(
                text = stringResource(R.string.quote),
                style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        innerTextField()
    }
}
