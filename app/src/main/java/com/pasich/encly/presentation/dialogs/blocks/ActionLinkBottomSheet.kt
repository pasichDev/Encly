package com.pasich.encly.presentation.dialogs.blocks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.blocks.LinkBlock
import com.pasich.encly.presentation.components.custombox.ModalBoxItem
import com.pasich.encly.presentation.components.custombox.RoundPosition


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLinkBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current


    if (settings.isBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = sheetState,
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.wrapContentHeight()
        ) {

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {


                when (settings.block) {
                    is Block.LinkBlock -> {

                        val urlModel by settings.block.block.collectAsState()
                        if (!urlModel.isError) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                LinkBlock(
                                    settings.block,
                                    blockActions = null,
                                    onClick = { Unit },
                                    modifier = Modifier
                                )
                            }
                            LazyColumn(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                item {
                                    ModalBoxItem(
                                        title = stringResource(R.string.copy_link),
                                        icon = painterResource(R.drawable.ic_copy),
                                        roundPosition = RoundPosition.First,
                                        enable = settings.blockMove != 1,
                                        action = {
                                            copyToClipboard(context, urlModel.url)
                                            onDismiss()
                                        })
                                }

                                item {
                                    ModalBoxItem(
                                        title = stringResource(R.string.open_browser),
                                        icon = painterResource(R.drawable.link),
                                        roundPosition = RoundPosition.Last,
                                        enable = settings.blockMove != 1,
                                        action = {
                                            uriHandler.openUri(urlModel.url)
                                            onDismiss()
                                        })
                                }
                            }
                        }

                    }

                    else -> Unit


                }

                LazyColumn(
                    modifier = Modifier.padding(16.dp)
                ) {


                    item {
                        ModalBoxItem(
                            title = "Перемістити вгору",
                            icon = painterResource(R.drawable.ic_up),
                            roundPosition = RoundPosition.First,
                            enable = settings.blockMove != 1,
                            action = { onAction(ActionBlockDialog.Move(1)) })
                    }
                    item {
                        ModalBoxItem(
                            title = "Перемістити вниз",
                            icon = painterResource(R.drawable.ic_down),
                            roundPosition = RoundPosition.Medium,
                            enable = settings.blockMove != 2,
                            action = { onAction(ActionBlockDialog.Move(2)) })
                    }
                    item {
                        ModalBoxItem(
                            title = stringResource(id = R.string.delete_block),
                            icon = painterResource(R.drawable.ic_to_trash),
                            roundPosition = RoundPosition.Last,
                            confirmationRequest = MaterialTheme.colorScheme.error,
                            action = { onAction(ActionBlockDialog.Delete) })
                    }

                }


            }

        }

    }

}


fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Url", text)
    clipboard.setPrimaryClip(clip)
}