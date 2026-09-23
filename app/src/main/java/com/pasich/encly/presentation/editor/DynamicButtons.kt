package com.pasich.encly.presentation.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.BlockType

@Composable
private fun BottomIconButton(
    icon: Int,
    blockType: BlockType,
    contentDescription: String?,
    onPress: (BlockType) -> Unit,
    isActive: Boolean = true,
) {
    IconButton(enabled = isActive, onClick = { onPress(blockType) }) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = if (isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onBackground.copy(
                    alpha = 0.5f,
                )
            },
        )
    }
}

/** One "add block" button of the editor toolbar. [headingLevel] fills the "Heading %d" label. */
private data class AddBlockButton(
    @param:DrawableRes val icon: Int,
    val type: BlockType,
    @param:StringRes val label: Int,
    val headingLevel: Int? = null,
)

private val addBlockButtons = listOf(
    AddBlockButton(R.drawable.field, BlockType.TEXT, R.string.text),
    AddBlockButton(R.drawable.link, BlockType.LINK, R.string.block_link),
    AddBlockButton(R.drawable.quote, BlockType.QUOTE, R.string.quote),
    AddBlockButton(R.drawable.list_ordered, BlockType.LIST_NUMBER, R.string.block_numbered_list),
    AddBlockButton(R.drawable.list_checklist, BlockType.LIST_CHECK, R.string.block_checklist),
    AddBlockButton(R.drawable.h1, BlockType.H1, R.string.block_heading_level, headingLevel = 1),
    AddBlockButton(R.drawable.h2, BlockType.H2, R.string.block_heading_level, headingLevel = 2),
    AddBlockButton(R.drawable.h3, BlockType.H3, R.string.block_heading_level, headingLevel = 3),
    AddBlockButton(R.drawable.h4, BlockType.H4, R.string.block_heading_level, headingLevel = 4),
    AddBlockButton(R.drawable.separator, BlockType.SEPARATOR, R.string.block_separator),
)

@Composable
fun DynamicButtons(onAddBlock: (BlockType) -> Unit, modifier: Modifier = Modifier) = Row(modifier = modifier) {
    addBlockButtons.forEach { button ->
        BottomIconButton(
            icon = button.icon,
            blockType = button.type,
            onPress = onAddBlock,
            contentDescription = button.headingLevel
                ?.let { stringResource(button.label, it) }
                ?: stringResource(button.label),
        )
    }
}
