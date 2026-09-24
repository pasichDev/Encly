package com.pasich.encly.presentation.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.editor.state.HEADING_TOOL

/** One block tool of the editor toolbar. [headingLevel] fills the "Heading %d" label. */
data class BlockTool(
    val icon: ImageVector,
    val type: BlockType,
    @param:StringRes val label: Int,
    val headingLevel: Int? = null,
)

/**
 * The toolbar's block tools (design spec §3.3), in its order. Each turns the block the user works
 * on into its type, or adds one after it (see applyTool).
 */
val mainBlockTools = listOf(
    BlockTool(EnclyIcons.Heading1, HEADING_TOOL, R.string.block_heading),
    BlockTool(EnclyIcons.Checklist, BlockType.LIST_CHECK, R.string.block_checklist),
    BlockTool(EnclyIcons.ListBullet, BlockType.LIST_BULLET, R.string.block_bulleted_list),
    BlockTool(EnclyIcons.ListNumbered, BlockType.LIST_NUMBER, R.string.block_numbered_list),
    BlockTool(EnclyIcons.Quote, BlockType.QUOTE, R.string.quote),
    BlockTool(EnclyIcons.Link, BlockType.LINK, R.string.block_link),
    BlockTool(EnclyIcons.Separator, BlockType.SEPARATOR, R.string.block_separator),
)

/** Behind "Add block": a plain paragraph and every heading level. */
val moreBlockTools = listOf(
    BlockTool(EnclyIcons.Type, BlockType.TEXT, R.string.text),
    BlockTool(EnclyIcons.Heading1, BlockType.H1, R.string.block_heading_level, headingLevel = 1),
    BlockTool(EnclyIcons.Heading2, BlockType.H2, R.string.block_heading_level, headingLevel = 2),
    BlockTool(EnclyIcons.Heading3, BlockType.H3, R.string.block_heading_level, headingLevel = 3),
    BlockTool(EnclyIcons.Heading4, BlockType.H4, R.string.block_heading_level, headingLevel = 4),
)

/** A tool button; [active] marks the tool of the block the user works on. */
@Composable
fun BlockToolButton(tool: BlockTool, active: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    EnclyToolButton(
        icon = tool.icon,
        contentDescription = tool.headingLevel?.let { stringResource(tool.label, it) } ?: stringResource(tool.label),
        onClick = onClick,
        style = if (active) ToolStyle.ACTIVE else ToolStyle.PLAIN,
        modifier = modifier,
    )
}
