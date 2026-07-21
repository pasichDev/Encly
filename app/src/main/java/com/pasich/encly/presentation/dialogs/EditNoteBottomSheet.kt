package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.components.custombox.ModalBoxItem
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.drawer.NavigationDrawerStyleButton
import com.pasich.encly.presentation.components.drawer.StyleButton
import com.pasich.encly.presentation.dialogs.content.CopyDrawerContent
import com.pasich.encly.presentation.dialogs.content.CopyNoteContent
import com.pasich.encly.presentation.dialogs.content.DrawerContent
import com.pasich.encly.presentation.dialogs.content.MainDrawerContent
import com.pasich.encly.presentation.dialogs.content.TranslateContent
import com.pasich.encly.presentation.dialogs.content.TranslateDrawerContent
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.ibmPlex
import com.pasich.encly.ui.theme.inter
import com.pasich.encly.ui.theme.playfair
import com.pasich.encly.ui.theme.poppins
import com.pasich.encly.ui.theme.roboto
import com.pasich.encly.ui.theme.sourceSans

enum class EditNoteBottomSheetAction {
    SHARE, CLOSE_NO_SAVE, DUPLICATE, TRASH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: EditNoteViewModel = hiltViewModel<EditNoteViewModel>(),
    onAction: (EditNoteBottomSheetAction) -> Unit = {}
) {
    val noteBlocks = viewModel.blocks
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontStyle by viewModel.fontStyle.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var currentContent by remember { mutableStateOf<DrawerContent>(MainDrawerContent) }

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss, sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                when (currentContent) {
                    is MainDrawerContent -> {

                        // Font style selection panel
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            NavigationDrawerStyleButton(
                                StyleButton(
                                    name = "Сучасний",
                                    fontTitle = poppins,
                                    fontBody = roboto,
                                    isSelected = fontStyle == FontStyleType.MODERN_SIMPLE
                                ),
                                onClick = { viewModel.updateFontStyle(FontStyleType.MODERN_SIMPLE) }
                            )
                            NavigationDrawerStyleButton(
                                StyleButton(
                                    name = "Затишний",
                                    fontTitle = playfair,
                                    fontBody = sourceSans,
                                    isSelected = fontStyle == FontStyleType.COZY_EDITOR
                                ),
                                onClick = { viewModel.updateFontStyle(FontStyleType.COZY_EDITOR) }
                            )
                            NavigationDrawerStyleButton(
                                StyleButton(
                                    name = "Техно",
                                    fontTitle = ibmPlex,
                                    fontBody = inter,
                                    isSelected = fontStyle == FontStyleType.TECH_MINIMAL
                                ),
                                onClick = { viewModel.updateFontStyle(FontStyleType.TECH_MINIMAL) }
                            )
                        }

                        Spacer(modifier = Modifier.height(25.dp))

                        // Slider for font size
                        FontSizeSlider(
                            currentSize = fontSize,
                            onSizeChange = { newSize ->
                                viewModel.updateFontSize(newSize)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            item {
                                ModalBoxItem(
                                    title = "Копировать",
                                    icon = painterResource(R.drawable.ic_copy),
                                    roundPosition = RoundPosition.First,
                                    action = { currentContent = CopyDrawerContent })
                            }


                            item {
                                ModalBoxItem(
                                    title = stringResource(id = R.string.share),
                                    icon = painterResource(R.drawable.ic_share),
                                    roundPosition = RoundPosition.Medium,
                                    action = {
                                        onAction(EditNoteBottomSheetAction.SHARE)
                                    })

                            }

                            item {
                                ModalBoxItem(
                                    title = stringResource(id = R.string.duplicate),
                                    icon = painterResource(R.drawable.ic_duplicate),
                                    roundPosition = RoundPosition.Medium,
                                    action = {
                                        onAction(EditNoteBottomSheetAction.DUPLICATE)
                                        onDismiss()
                                    })
                            }
                            item {
                                ModalBoxItem(
                                    title = "Скасувати всі зміни",
                                    icon = painterResource(R.drawable.ic_cancel_save),
                                    roundPosition = RoundPosition.Medium,
                                    action = {
                                        onAction(EditNoteBottomSheetAction.CLOSE_NO_SAVE)
                                        onDismiss()
                                    })
                            }


                            item {
                                ModalBoxItem(
                                    title = stringResource(id = R.string.delete),
                                    icon = painterResource(R.drawable.ic_delete),
                                    roundPosition = RoundPosition.Last,
                                    confirmationRequest = MaterialTheme.colorScheme.error,
                                    action = {
                                        onAction(EditNoteBottomSheetAction.TRASH)
                                        onDismiss()
                                    })
                            }
                        }

                    }

                    is CopyDrawerContent -> {
                        CopyNoteContent(
                            blocks = noteBlocks,
                            onBackClick = { currentContent = MainDrawerContent },
                            onCloseClick = {
                                currentContent = MainDrawerContent
                                onDismiss()
                            })
                    }

                    is TranslateDrawerContent -> {
                        TranslateContent(
                            onBackClick = { currentContent = MainDrawerContent },
                            onCloseClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}
