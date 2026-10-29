package com.pasich.encly.presentation.components.editNote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.block_bulleted_list
import com.pasich.encly.core.resources.block_checklist
import com.pasich.encly.core.resources.block_heading
import com.pasich.encly.core.resources.block_heading_level
import com.pasich.encly.core.resources.block_link
import com.pasich.encly.core.resources.block_numbered_list
import com.pasich.encly.core.resources.block_separator
import com.pasich.encly.core.resources.quote
import com.pasich.encly.core.resources.text
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.state.HEADING_TOOL
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.ToolStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One block tool of the editor toolbar. [headingLevel] fills the "Heading %d" label. */
data class BlockTool(
    val icon: ImageVector,
    val type: BlockType,
    val label: StringResource,
    val headingLevel: Int? = null,
)

/**
 * The toolbar's block tools (design spec §3.3), in its order. Each turns the block the user works
 * on into its type, or adds one after it (see applyTool).
 */
val mainBlockTools = listOf(
    BlockTool(EnclyIcons.Heading1, HEADING_TOOL, Res.string.block_heading),
    BlockTool(EnclyIcons.Checklist, BlockType.LIST_CHECK, Res.string.block_checklist),
    BlockTool(EnclyIcons.ListBullet, BlockType.LIST_BULLET, Res.string.block_bulleted_list),
    BlockTool(EnclyIcons.ListNumbered, BlockType.LIST_NUMBER, Res.string.block_numbered_list),
    BlockTool(EnclyIcons.Quote, BlockType.QUOTE, Res.string.quote),
)

/** Behind "More block tools": a plain paragraph, every heading level, a link and a separator. */
val moreBlockTools = listOf(
    BlockTool(EnclyIcons.Type, BlockType.TEXT, Res.string.text),
    BlockTool(EnclyIcons.Heading1, BlockType.H1, Res.string.block_heading_level, headingLevel = 1),
    BlockTool(EnclyIcons.Heading2, BlockType.H2, Res.string.block_heading_level, headingLevel = 2),
    BlockTool(EnclyIcons.Heading3, BlockType.H3, Res.string.block_heading_level, headingLevel = 3),
    BlockTool(EnclyIcons.Heading4, BlockType.H4, Res.string.block_heading_level, headingLevel = 4),
    BlockTool(EnclyIcons.Link, BlockType.LINK, Res.string.block_link),
    BlockTool(EnclyIcons.Separator, BlockType.SEPARATOR, Res.string.block_separator),
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
