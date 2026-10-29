package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.notes_section_pinned
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.noteColorStyle
import org.jetbrains.compose.resources.stringResource

/**
 * Title opacity of a card in the trash (design spec §4.6). Only the title fades: the excerpt and
 * meta line are already muted (`onSurfaceVariant`) and faded too would drop below 4.5:1.
 */
const val DIMMED_CARD_ALPHA = 0.7f

private val MetaIcon = 14.dp

/** The meta line of a note card: tag, relative date and checklist progress; each is optional. */
@Immutable
data class NoteCardMeta(val tag: String? = null, val date: String? = null, val progress: String? = null)

/**
 * A note card (design spec §3.3): `surfaceContainer`, radius 16, padding 16, no border or shadow.
 * Title in titleMedium, excerpt in bodyMedium `onSurfaceVariant` cut at [excerptLines], then the
 * meta line. [highlight] marks search matches. A [noteColor] tints the card and adds an accent edge.
 * [selected] draws a 2 dp `primary` ring and a check; [dimmed] fades the title (trash), keeping every
 * text at 4.5:1 or more. A [pinned] note shows a small pin after its title. [current] marks the note that is
 * open beside the list (the shown tab of the desktop layout): a raised container and an `outline` edge, so it
 * reads apart from a [selected] (checked) card, which it can also be. With [onCommandClick] a click with the
 * command key held calls it instead of [onClick] (see [enclyCommandClick]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnclyNoteCard(
    title: String?,
    excerpt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    meta: NoteCardMeta = NoteCardMeta(),
    excerptLines: Int = 2,
    highlight: String = "",
    selected: Boolean = false,
    dimmed: Boolean = false,
    pinned: Boolean = false,
    noteColor: NoteColor = NoteColor.NONE,
    current: Boolean = false,
    onCommandClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tint = remember(noteColor, colors) { noteColorStyle(noteColor, colors) }
    val muted = tint?.onCardVariant ?: colors.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tint?.card ?: untintedCardColor(current),
        contentColor = colors.onSurface,
        border = cardBorder(selected, current),
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected || current }
            .enclyInteractive(MaterialTheme.shapes.medium)
            .then(if (onCommandClick != null) Modifier.enclyCommandClick(onCommandClick) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
            modifier = Modifier
                .then(if (tint != null) Modifier.accentEdge(tint.accent, EnclyTheme.spacing.xxs) else Modifier)
                .padding(EnclyTheme.spacing.m),
        ) {
            if (!title.isNullOrEmpty() || selected || pinned) {
                NoteTitleRow(
                    title = title.orEmpty(),
                    highlight = highlight,
                    state = TitleState(dimmed = dimmed, pinned = pinned, selected = selected, muted = muted),
                )
            }
            if (excerpt.isNotEmpty()) {
                HighlightedText(
                    text = excerpt,
                    searchQuery = highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = excerptLines,
                )
            }
            if (meta != NoteCardMeta()) NoteMetaLine(meta, muted)
        }
    }
}

/** The container of a card without a note colour: raised when it is the note open beside the list. */
@Composable
private fun untintedCardColor(current: Boolean): Color =
    if (current) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer

/** The edge of a card: the `primary` ring of a checked one, else the `outline` edge of the open one. */
@Composable
private fun cardBorder(selected: Boolean, current: Boolean): BorderStroke? {
    val colors = MaterialTheme.colorScheme
    return when {
        selected -> BorderStroke(EnclyTheme.spacing.stroke, colors.primary)
        current -> BorderStroke(EnclyTheme.spacing.stroke, colors.outline)
        else -> null
    }
}

/** What a card's title row marks besides the title. */
private class TitleState(val dimmed: Boolean, val pinned: Boolean, val selected: Boolean, val muted: Color)

/** The title, then the pin of a pinned note and the check of a selected one. */
@Composable
private fun NoteTitleRow(title: String, highlight: String, state: TitleState) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        HighlightedText(
            text = title,
            searchQuery = highlight,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.dimmed) colors.onSurface.copy(alpha = DIMMED_CARD_ALPHA) else colors.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        if (state.pinned) {
            Icon(
                EnclyIcons.Pin,
                contentDescription = stringResource(Res.string.notes_section_pinned),
                tint = state.muted,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
        if (state.selected) {
            Icon(
                EnclyIcons.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
    }
}

@Composable
private fun NoteMetaLine(meta: NoteCardMeta, muted: Color) {
    val style = EnclyTheme.typography.meta
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = EnclyTheme.spacing.xxs),
    ) {
        meta.tag?.let { MetaItem(icon = EnclyIcons.Tag, text = it, color = muted) }
        meta.date?.let { Text(text = it, style = style, color = muted, maxLines = 1) }
        Spacer(modifier = Modifier.weight(1f))
        meta.progress?.let {
            MetaItem(
                icon = EnclyIcons.Checklist,
                text = it,
                color = MaterialTheme.colorScheme.primary,
                weight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, color: Color, weight: FontWeight? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(MetaIcon))
        Text(
            text = text,
            style = EnclyTheme.typography.meta.copy(fontWeight = weight ?: EnclyTheme.typography.meta.fontWeight),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The left edge of a coloured note card: a [width]-wide strip of [color] the full height of the
 * card. The card's own shape clips it, so its ends follow the rounded corners.
 */
private fun Modifier.accentEdge(color: Color, width: Dp): Modifier = drawBehind {
    drawRect(color = color, size = Size(width.toPx(), size.height))
}

/** A small tag label (e.g. on the Appearance preview): `primaryContainer`, radius 12. */
@Composable
fun EnclyTagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = EnclyTheme.typography.meta.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = EnclyTheme.spacing.textGap),
        )
    }
}
