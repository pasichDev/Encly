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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pasich.encly.R
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.utils.formatNoteDate
import java.util.Date

/**
 * The note's overline (design spec §4.4): "PERSONAL · TODAY 9:40 AM", the tag and when the note
 * was last edited; only the date without a tag. [notSaved] adds a persistent "Not saved" in
 * `error`, announced politely, after a save failed.
 */
@Composable
fun NoteOverline(tagName: String?, date: Long, notSaved: Boolean, modifier: Modifier = Modifier) {
    val edited = formatNoteDate(if (date == 0L) Date() else Date(date))
    val style = MaterialTheme.typography.labelSmall
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = listOfNotNull(tagName, edited).joinToString(OVERLINE_SEPARATOR).uppercase(),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (notSaved) {
            Text(
                text = (OVERLINE_SEPARATOR + stringResource(R.string.note_not_saved)).uppercase(),
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
 * Chooses the note's tag: "No tag" and every tag, the current one checked, then a way to
 * create or manage tags ([onManageTags]). Dismissible without choosing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTagSheet(
    tags: List<Tag>,
    selectedTagId: Long?,
    onSelect: (Long) -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = stringResource(R.string.edit_tags)) {
        // Many tags scroll; the manage row below stays in view.
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            EnclySheetRow(
                title = stringResource(R.string.no_tag),
                onClick = { onSelect(0L) },
                selected = selectedTagId == null || selectedTagId == 0L,
                muted = true,
            )
            tags.forEach { tag ->
                EnclySheetRow(
                    title = tag.nameTag,
                    icon = EnclyIcons.Tag,
                    onClick = { onSelect(tag.id) },
                    selected = selectedTagId == tag.id,
                )
            }
        }
        EnclyGroupDivider()
        EnclySheetRow(
            title = stringResource(if (tags.isEmpty()) R.string.create_new_tag else R.string.tags_manage),
            icon = if (tags.isEmpty()) EnclyIcons.Plus else EnclyIcons.Edit,
            onClick = onManageTags,
        )
    }
}
