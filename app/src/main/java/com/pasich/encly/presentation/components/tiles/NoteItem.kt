package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.titleNoteCard
import com.pasich.encly.utils.NotesTextFormatter


@Stable
private data class FontStylesData(
    val heading: FontFamily,
    val body: FontFamily
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    modifier: Modifier = Modifier,
    item: NoteWithTag? = null,
    itemNote: Note? = null,
    onItemClick: (Note) -> Unit,
    onItemLongClick: (Note) -> Unit
) {
    val noteItem: Note = item?.note ?: itemNote ?: Note()

    val fontStyles = rememberFontStyles()
    val stableFontStyles = remember(fontStyles) {
        FontStylesData(
            heading = fontStyles.families.heading,
            body = fontStyles.families.body
        )
    }

    val preview = remember(noteItem.description, noteItem.value) {
        noteItem.description.ifEmpty {
            NotesTextFormatter.jsonToPlainText(noteItem.value)
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // Controls the animation that shifts the card to the right (under the icon)
    val cardOffsetX by animateDpAsState(
        targetValue = if (noteItem.isChecked) 50.dp else 0.dp,
        label = "cardOffset"
    )

    // Controls the icon's opacity
    val iconAlpha by animateFloatAsState(
        targetValue = if (noteItem.isChecked) 1f else 0f,
        label = "iconAlpha"
    )

    val shape = remember(noteItem.title) {
        RoundedCornerShape(if (noteItem.title.isNotEmpty()) 20.dp else 15.dp)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = cardOffsetX)
                .clip(shape)
                .combinedClickable(
                    onClick = { onItemClick(noteItem) },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onItemLongClick(noteItem)
                    }
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp)) {
                if (noteItem.title.isNotEmpty()) {
                    Text(
                        text = noteItem.title,
                        style = titleNoteCard.copy(
                            fontFamily = stableFontStyles.heading
                        )
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }
                Text(
                    text = preview,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = stableFontStyles.body
                    )
                )
                if (item != null && item.tag != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium.copy(CornerSize(12.dp))
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.tag.nameTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onItemClick(noteItem) }
                )
                .align(Alignment.CenterStart)
                .offset(x = 4.dp)
                .graphicsLayer { alpha = iconAlpha }
        )
    }
}
