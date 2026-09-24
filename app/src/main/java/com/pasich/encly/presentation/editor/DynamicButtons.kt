package com.pasich.encly.presentation.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Heading3
import com.composables.icons.lucide.Heading4
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.ListOrdered
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Quote
import com.composables.icons.lucide.SeparatorHorizontal
import com.composables.icons.lucide.Type
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.ui.theme.EnclyTheme

/** One "add block" button of the editor toolbar. [headingLevel] fills the "Heading %d" label. */
private data class AddBlockButton(
    val icon: ImageVector,
    val type: BlockType,
    @param:StringRes val label: Int,
    val headingLevel: Int? = null,
)

private val addBlockButtons = listOf(
    AddBlockButton(Lucide.Type, BlockType.TEXT, R.string.text),
    AddBlockButton(Lucide.Link, BlockType.LINK, R.string.block_link),
    AddBlockButton(Lucide.Quote, BlockType.QUOTE, R.string.quote),
    AddBlockButton(Lucide.ListOrdered, BlockType.LIST_NUMBER, R.string.block_numbered_list),
    AddBlockButton(Lucide.List, BlockType.LIST_BULLET, R.string.block_bulleted_list),
    AddBlockButton(Lucide.ListChecks, BlockType.LIST_CHECK, R.string.block_checklist),
    AddBlockButton(Lucide.Heading1, BlockType.H1, R.string.block_heading_level, headingLevel = 1),
    AddBlockButton(Lucide.Heading2, BlockType.H2, R.string.block_heading_level, headingLevel = 2),
    AddBlockButton(Lucide.Heading3, BlockType.H3, R.string.block_heading_level, headingLevel = 3),
    AddBlockButton(Lucide.Heading4, BlockType.H4, R.string.block_heading_level, headingLevel = 4),
    AddBlockButton(Lucide.SeparatorHorizontal, BlockType.SEPARATOR, R.string.block_separator),
)

/** The block types the toolbar can insert, one tool button each. */
@Composable
fun DynamicButtons(onAddBlock: (BlockType) -> Unit, modifier: Modifier = Modifier) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
) {
    addBlockButtons.forEach { button ->
        EnclyToolButton(
            icon = button.icon,
            contentDescription = button.headingLevel
                ?.let { stringResource(button.label, it) }
                ?: stringResource(button.label),
            onClick = { onAddBlock(button.type) },
        )
    }
}
