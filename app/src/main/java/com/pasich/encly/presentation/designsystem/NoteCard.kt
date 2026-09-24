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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

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
 * meta line. [highlight] marks search matches. [selected] draws a 2 dp `primary` ring and a check;
 * [dimmed] fades the title (trash), keeping every text at 4.5:1 or more.
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
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        border = if (selected) BorderStroke(EnclyTheme.spacing.stroke, colors.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
            modifier = Modifier.padding(EnclyTheme.spacing.m),
        ) {
            if (!title.isNullOrEmpty() || selected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedText(
                        text = title.orEmpty(),
                        searchQuery = highlight,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dimmed) colors.onSurface.copy(alpha = DIMMED_CARD_ALPHA) else colors.onSurface,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            EnclyIcons.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
                        )
                    }
                }
            }
            if (excerpt.isNotEmpty()) {
                HighlightedText(
                    text = excerpt,
                    searchQuery = highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = excerptLines,
                )
            }
            if (meta != NoteCardMeta()) NoteMetaLine(meta)
        }
    }
}

@Composable
private fun NoteMetaLine(meta: NoteCardMeta) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun MetaItem(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    weight: FontWeight? = null,
) {
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
