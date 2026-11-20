package com.pasich.encly.presentation.components.editNote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.block_bulleted_list
import com.pasich.encly.core.resources.block_checklist
import com.pasich.encly.core.resources.block_code
import com.pasich.encly.core.resources.block_heading
import com.pasich.encly.core.resources.block_heading_level
import com.pasich.encly.core.resources.block_link
import com.pasich.encly.core.resources.block_numbered_list
import com.pasich.encly.core.resources.block_separator
import com.pasich.encly.core.resources.block_table
import com.pasich.encly.core.resources.quote
import com.pasich.encly.core.resources.text
import com.pasich.encly.domain.model.ToolbarTool
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.presentation.designsystem.Code
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.Table
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

/** The icon, block type and label of toolbar tool [this]. */
fun ToolbarTool.toBlockTool(): BlockTool = when (this) {
    ToolbarTool.HEADING -> BlockTool(EnclyIcons.Heading1, type, Res.string.block_heading)

    ToolbarTool.LIST_CHECK -> BlockTool(EnclyIcons.Checklist, type, Res.string.block_checklist)

    ToolbarTool.LIST_BULLET -> BlockTool(EnclyIcons.ListBullet, type, Res.string.block_bulleted_list)

    ToolbarTool.LIST_NUMBER -> BlockTool(EnclyIcons.ListNumbered, type, Res.string.block_numbered_list)

    ToolbarTool.QUOTE -> BlockTool(EnclyIcons.Quote, type, Res.string.quote)

    ToolbarTool.CODE -> BlockTool(EnclyIcons.Code, type, Res.string.block_code)

    ToolbarTool.TABLE -> BlockTool(EnclyIcons.Table, type, Res.string.block_table)

    ToolbarTool.TEXT -> BlockTool(EnclyIcons.Type, type, Res.string.text)

    ToolbarTool.H1, ToolbarTool.H2, ToolbarTool.H3, ToolbarTool.H4 ->
        BlockTool(headingIcon(), type, Res.string.block_heading_level, headingLevel)

    ToolbarTool.LINK -> BlockTool(EnclyIcons.Link, type, Res.string.block_link)

    ToolbarTool.SEPARATOR -> BlockTool(EnclyIcons.Separator, type, Res.string.block_separator)
}

private fun ToolbarTool.headingIcon() = when (this) {
    ToolbarTool.H1 -> EnclyIcons.Heading1
    ToolbarTool.H2 -> EnclyIcons.Heading2
    ToolbarTool.H3 -> EnclyIcons.Heading3
    else -> EnclyIcons.Heading4
}

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
