package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

@Composable
fun HBlock(block: Block.HBlock, blockActions: BlockActions, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    val textStyle = headingTextStyle(block.blockType)
    val state = rememberBlockTextFieldState(block.text, blockActions)
    val actions by rememberUpdatedState(blockActions)
    // Enter splits the heading; the text after the cursor goes on as a paragraph (BlockInput).
    val input = remember { BlockInput(BlockField.Styled) { actions } }

    BasicTextField(
        state = state,
        readOnly = isLocked,
        inputTransformation = input,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = textStyle,
        keyboardOptions = WritingKeyboard,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = EnclyTheme.spacing.labelGap)
            .textBlockKeys(state, blockActions),
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.block_heading_level, block.blockType.headingLevel()),
                        style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                innerTextField()
            }
        },
    )
}

private val HeadingTypes = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)

/** 1 for H1 up to 4 for H4. */
private fun BlockType.headingLevel(): Int = HeadingTypes.indexOf(this) + 1

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
