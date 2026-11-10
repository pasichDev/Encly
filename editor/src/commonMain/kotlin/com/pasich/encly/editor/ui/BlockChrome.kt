package com.pasich.encly.editor.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.blocks.BlockSelectAllIme
import com.pasich.encly.editor.ui.blocks.ReorderHandleWidth
import com.pasich.encly.editor.ui.components.EditorTooltip
import com.pasich.encly.editor.ui.components.editorInteractive

/** The test tag of the lock toggle at the end of a checklist. */
internal const val CHECKLIST_LOCK_TAG = "checklist-lock-toggle"

/** The width of the strip at a block's start (the page gutter) that a long press selects the block from. */
private val HandleWidth = 24.dp
private val GripSize = 18.dp
private val LockSize = 28.dp
private val HighlightInset = 8.dp
private val HighlightRadius = 8.dp
private const val HIGHLIGHT_ALPHA = 0.16f
private const val GRIP_ALPHA = 0.5f

/**
 * What goes around a block on screen, outside its own content: the highlight of a picked block, a handle in the
 * page gutter at its start (a long press picks the block; a grip marks it on the block the user works on), the
 * lock toggle at the end of a checklist the user works on, and the keyboard's Select all. While blocks are being
 * picked a touch anywhere on a block picks or lets go of it, and its text takes no touch. Nothing here changes the
 * block's layout.
 */
@Composable
internal fun BlockChrome(
    block: Block,
    controller: EditorController,
    isLocked: Boolean,
    content: @Composable () -> Unit,
) {
    val selection = controller.selectedBlocks
    val ids by selection.ids.collectAsState()
    val picked = block.id in ids
    val picking = ids.isNotEmpty()
    BlockSelectAllIme(onSelectAll = controller::selectAllText) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pickedBackground(picked, MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA))
                .semantics { selected = picked },
        ) {
            content()
            if (picking) {
                PickOverlay(picked) { selection.toggle(block.id) }
            } else {
                val worksOn = controller.interactedBlockId.collectAsState().value == block.id
                BlockHandle(worksOn) { selection.select(block.id) }
                if (worksOn && !isLocked) ChecklistLockToggle(block, controller)
            }
        }
    }
}

/** Draws [color] behind a picked block, a little inside the page edges. */
private fun Modifier.pickedBackground(picked: Boolean, color: Color): Modifier = if (!picked) {
    this
} else {
    drawBehind {
        val inset = HighlightInset.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, 0f),
            size = Size(size.width - 2 * inset, size.height),
            cornerRadius = CornerRadius(HighlightRadius.toPx()),
        )
    }
}

/** Covers the block while blocks are being picked: a tap picks it or lets it go; TalkBack reads its state. */
@Composable
private fun BoxScope.PickOverlay(picked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .toggleable(
                value = picked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
    )
}

/** The strip at the start of a block that a long press picks the block from; [marked] draws a grip in it. */
@Composable
private fun BoxScope.BlockHandle(marked: Boolean, onSelect: () -> Unit) {
    // The strip is as tall as the block: an outer box takes the block's size, which a child cannot ask for.
    Box(Modifier.matchParentSize()) {
        Box(
            Modifier
                .width(HandleWidth)
                .fillMaxHeight()
                .pointerInput(onSelect) { detectTapGestures(onLongPress = { onSelect() }) },
        ) {
            if (marked) {
                Icon(
                    LocalEditorTheme.current.icons.grip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GRIP_ALPHA),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(GripSize),
                )
            }
        }
    }
}

/**
 * The lock of a checklist, at the end of its first item while the user works in it: the same lock as the block's
 * menu entry. It shows the state the checklist is in, and a press changes it. Nothing for any other block.
 */
@Composable
private fun BoxScope.ChecklistLockToggle(block: Block, controller: EditorController) {
    val checklist = (block as? Block.ListBlock)?.takeIf { it.blockType == BlockType.LIST_CHECK } ?: return
    val locked by checklist.locked.collectAsState()
    val items by checklist.items.collectAsState()
    val strings = LocalEditorStrings.current
    val icons = LocalEditorTheme.current.icons
    val colors = MaterialTheme.colorScheme
    EditorTooltip(
        label = strings.text(if (locked) EditorString.CHECKLIST_UNLOCK else EditorString.CHECKLIST_LOCK),
        // Beside the drag handle of the first item, not over it.
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = if (items.size > 1 && !locked) ReorderHandleWidth else 0.dp),
    ) {
        Surface(
            onClick = { controller.toggleChecklistLock(checklist) },
            shape = CircleShape,
            color = if (locked) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (locked) colors.onPrimaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.editorInteractive(CircleShape).size(LockSize).testTag(CHECKLIST_LOCK_TAG),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    if (locked) icons.lock else icons.lockOpen,
                    contentDescription = strings.text(
                        if (locked) EditorString.CHECKLIST_UNLOCK else EditorString.CHECKLIST_LOCK,
                    ),
                    modifier = Modifier.size(LocalEditorTheme.current.spacing.iconSmall - 4.dp),
                )
            }
        }
    }
}
