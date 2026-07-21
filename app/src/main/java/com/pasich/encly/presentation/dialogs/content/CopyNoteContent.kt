package com.pasich.encly.presentation.dialogs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.core.serialization.MarkdownConverter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.utils.NotesTextFormatter


@Composable
fun CopyNoteContent(
    blocks: List<Block>, onBackClick: () -> Unit, onCloseClick: () -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    val choices = remember {
        mutableStateListOf("Markdown", "Default")
    }
    var selectedChoiceIndex = remember {
        mutableIntStateOf(0)
    }

    // Convert blocks into text of the corresponding format
    val noteText by remember(blocks, selectedChoiceIndex) {
        derivedStateOf {
            if (selectedChoiceIndex.intValue == 0) {
                MarkdownConverter.blocksToMarkdown(blocks)
            } else {
                NotesTextFormatter.blocksToPlainText(blocks)
            }
        }
    }



    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        DrawerHeader(
            title = "Копирование текста", onBackClick = onBackClick, onCloseClick = onCloseClick
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            SingleChoiceSegmentedButtonRow {
                choices.forEachIndexed { index, choice ->

                    SegmentedButton(
                        selected = selectedChoiceIndex.intValue == index,
                        onClick = { selectedChoiceIndex.intValue = index },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index, count = choices.count()
                        ),
                        modifier = Modifier.height(32.dp)

                    ) {
                        Text(
                            choice, modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(noteText))
                }) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Копировать",
                )

            }
        }


        Spacer(modifier = Modifier.height(8.dp))


        Surface(
            modifier = Modifier.fillMaxWidth(), shadowElevation = 2.dp
        ) {
            SelectionContainer {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

    }
}
