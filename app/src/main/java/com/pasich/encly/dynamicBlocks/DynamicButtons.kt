package com.pasich.encly.dynamicBlocks

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
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

@Composable
fun BottomIconButton(
    icon: Int,
    blockType: BlockType,
    contentDescription: String = "bottomBarIcon",
    onPressed: (BlockType) -> Unit,
    isActive: Boolean = true
) {
    IconButton(enabled = isActive, onClick = { onPressed(blockType) }) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onBackground.copy(
                alpha = 0.5f
            )
        )
    }
}


@Composable
fun DynamicButtons(
    viewModel: EditNoteViewModel,
    toMain: () -> Unit,
) {
    return Row {
        BottomIconButton(
            icon = R.drawable.field, blockType = BlockType.TEXT, onPressed = {
                viewModel.addBlock(BlockType.TEXT)
                toMain()
            }, contentDescription = stringResource(R.string.text)
        )

        BottomIconButton(
            icon = R.drawable.link, blockType = BlockType.LINK, onPressed = {
                viewModel.addBlock(BlockType.LINK)
                toMain()
            })
        BottomIconButton(
            icon = R.drawable.quote, blockType = BlockType.QUOTE, onPressed = {
                viewModel.addBlock(BlockType.QUOTE)
                toMain()
            }, contentDescription = stringResource(R.string.quote)
        )
        BottomIconButton(
            icon = R.drawable.list_ordered, blockType = BlockType.LIST_NUMBER, onPressed = {
                viewModel.addBlock(BlockType.LIST_NUMBER)
                toMain()
            })
        BottomIconButton(
            icon = R.drawable.list_checklist, blockType = BlockType.LIST_CHECK, onPressed = {
                viewModel.addBlock(BlockType.LIST_CHECK)
                toMain()
            })
        BottomIconButton(
            icon = R.drawable.h1, blockType = BlockType.H1, onPressed = {
                viewModel.addBlock(BlockType.H1)
                toMain()
            }, contentDescription = "H1"
        )
        BottomIconButton(
            icon = R.drawable.h2, blockType = BlockType.H2, onPressed = {
                viewModel.addBlock(BlockType.H2)
                toMain()
            }, contentDescription = "H2"
        )
        BottomIconButton(
            icon = R.drawable.h3, blockType = BlockType.H3, onPressed = {
                viewModel.addBlock(BlockType.H3)
                toMain()
            }, contentDescription = "H3"
        )
        BottomIconButton(
            icon = R.drawable.h4, blockType = BlockType.H4, onPressed = {
                viewModel.addBlock(BlockType.H4)
                toMain()
            }, contentDescription = "H4"
        )
        BottomIconButton(
            icon = R.drawable.separator, blockType = BlockType.SEPARATOR, onPressed = {
                viewModel.addBlock(BlockType.SEPARATOR)
                toMain()
            }, contentDescription = "Separator"
        )
    }
}



