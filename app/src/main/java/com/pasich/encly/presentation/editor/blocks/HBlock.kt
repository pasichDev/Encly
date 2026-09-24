package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

@Composable
fun HBlock(block: Block.HBlock, blockActions: BlockActions, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    val text by block.text.collectAsState()
    val textStyle = headingTextStyle(block.blockType)

    var textFieldValue by rememberBlockTextFieldValue(text, blockActions)

    BasicTextField(
        value = textFieldValue,
        enabled = !isLocked,
        onValueChange = { newValue ->
            textFieldValue = newValue
            val newText = newValue.text
            blockActions.onTextChanged(newText)
        },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        keyboardActions =
        KeyboardActions(onNext = {
            if (text.isEmpty()) {
                // An empty heading turns into a regular text block
                blockActions.onReplaceBlock(Block.TextBlock())
            } else if (!blockActions.navigateToNext()) {
                // The last block: add a paragraph after it
                blockActions.onAddParagraph()
            }
        }),
        modifier =
        modifier
            .fillMaxWidth()
            .padding(top = EnclyTheme.spacing.labelGap)
            // Enter is handled by KeyboardActions.onNext.
            .textBlockKeys(text, blockActions, enterAddsParagraph = false),
        decorationBox = {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = block.blockType.toString(),
                        style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                it()
            }
        },
    )
}

/** The text style of a heading of [blockType] (H1-H4). */
@Composable
private fun headingTextStyle(blockType: BlockType): TextStyle {
    val fontStyles = rememberFontStyles()
    val (base, size) = when (blockType) {
        BlockType.H1 -> MaterialTheme.typography.headlineSmall to fontStyles.sizes.h1
        BlockType.H2 -> MaterialTheme.typography.titleLarge to fontStyles.sizes.h2
        BlockType.H3 -> MaterialTheme.typography.titleMedium to fontStyles.sizes.h3
        BlockType.H4 -> MaterialTheme.typography.titleSmall to fontStyles.sizes.h4
        else -> return MaterialTheme.typography.titleMedium
    }
    return base.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = size,
        fontFamily = fontStyles.families.heading,
    )
}
