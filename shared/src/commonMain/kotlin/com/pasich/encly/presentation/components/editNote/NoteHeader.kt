package com.pasich.encly.presentation.components.editNote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.create_new_tag
import com.pasich.encly.core.resources.edit_tags
import com.pasich.encly.core.resources.no_tag
import com.pasich.encly.core.resources.note_not_saved
import com.pasich.encly.core.resources.tags_manage
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.utils.formatNoteDate
import com.pasich.encly.utils.noteInstant
import org.jetbrains.compose.resources.stringResource

/**
 * The note's overline (design spec §4.4): "PERSONAL · WORK · TODAY 9:40 AM", every tag of the note and when it
 * was last edited; only the date without a tag. [notSaved] adds a persistent "Not saved" in
 * `error`, announced politely, after a save failed.
 */
@Composable
fun NoteOverline(tagNames: List<String>, date: Long, notSaved: Boolean, modifier: Modifier = Modifier) {
    val edited = formatNoteDate(noteInstant(date))
    val style = MaterialTheme.typography.labelSmall
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = (tagNames + edited).joinToString(OVERLINE_SEPARATOR).uppercase(),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (notSaved) {
            Text(
                text = (OVERLINE_SEPARATOR + stringResource(Res.string.note_not_saved)).uppercase(),
                style = style,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val OVERLINE_SEPARATOR = " · "

/**
 * Chooses the note's tags: "No tag" and every tag, the note's ones checked, then a way to
 * create or manage tags ([onManageTags]). A tap puts a tag on or takes it off ([onToggle]) and the
 * sheet stays open for the next one; "No tag" ([onToggle] with 0) takes every tag off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTagSheet(
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = stringResource(Res.string.edit_tags)) {
        // Many tags scroll; the manage row below stays in view.
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val untagged = selectedTagIds.isEmpty()
            EnclySheetRow(
                title = stringResource(Res.string.no_tag),
                onClick = { onToggle(0L) },
                selected = untagged,
                muted = true,
                modifier = Modifier.semantics { selected = untagged },
            )
            tags.forEach { tag ->
                val checked = tag.id in selectedTagIds
                EnclySheetRow(
                    title = tag.nameTag,
                    icon = EnclyIcons.Tag,
                    onClick = { onToggle(tag.id) },
                    selected = checked,
                    modifier = Modifier.semantics { selected = checked },
                )
            }
        }
        EnclyGroupDivider()
        EnclySheetRow(
            title = stringResource(if (tags.isEmpty()) Res.string.create_new_tag else Res.string.tags_manage),
            icon = if (tags.isEmpty()) EnclyIcons.Plus else EnclyIcons.Edit,
            onClick = onManageTags,
        )
    }
}
