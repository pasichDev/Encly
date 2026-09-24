package com.pasich.encly.presentation.components.editNote

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.formatNoteDate
import java.util.Date
import com.composables.icons.lucide.Tag as TagIcon

@Composable
fun NoteSubTitle(
    statusSaveNote: SaveStatusNote,
    note: Note,
    modifier: Modifier = Modifier,
    tagButtonPadding: PaddingValues = PaddingValues(),
    editTagListViewModel: TagListViewModel = hiltViewModel(),
    tagsViewListen: ((Boolean) -> Unit)? = null,
    changeTag: ((Long) -> Unit)? = null,
    isDuplicate: Boolean = false,
) {
    val state by editTagListViewModel.state.collectAsState()
    var isTagListVisible by remember { mutableStateOf(false) }
    val targetTag = state.listTags.find { it.id == note.tagId }

    fun showTags(visible: Boolean) {
        isTagListVisible = visible
        tagsViewListen?.invoke(visible)
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = !isTagListVisible) { visible ->
            if (visible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
                ) {
                    if (statusSaveNote != SaveStatusNote.LOADING) {
                        MetaLabel(
                            icon = Lucide.TagIcon,
                            text = targetTag?.nameTag ?: stringResource(R.string.no_tag),
                            modifier = Modifier
                                .padding(tagButtonPadding)
                                .clickable { if (state.listTags.isNotEmpty()) showTags(true) },
                        )
                    }
                    SaveStatusLabel(statusSaveNote, note)
                    if (isDuplicate) MetaLabel(icon = Lucide.CopyPlus, text = stringResource(R.string.duplicate))
                }
            } else {
                TagChooser(
                    tags = state.listTags,
                    selected = targetTag,
                    onSelect = { tagId ->
                        changeTag?.invoke(tagId)
                        showTags(false)
                    },
                )
            }
        }
    }
}

/** When the note was saved, or that it is saving or loading. */
@Composable
private fun SaveStatusLabel(status: SaveStatusNote, note: Note) {
    when (status) {
        SaveStatusNote.OLD -> MetaLabel(
            icon = Lucide.CalendarDays,
            text = formatNoteDate(if (note.date == 0L) Date() else Date(note.date)),
        )

        SaveStatusNote.SAVING -> MetaLabel(icon = null, text = stringResource(R.string.saving), busy = true)

        SaveStatusNote.SAVED -> MetaLabel(icon = Lucide.Check, text = stringResource(R.string.saved))

        SaveStatusNote.LOADING -> MetaLabel(icon = null, text = stringResource(R.string.loading), busy = true)
    }
}

/** An icon (or a small spinner when [busy]) and a meta-style label, both `onSurfaceVariant`. */
@Composable
private fun MetaLabel(icon: ImageVector?, text: String, modifier: Modifier = Modifier, busy: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(EnclyTheme.spacing.iconXSmall),
                color = muted,
                strokeWidth = EnclyTheme.spacing.stroke,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(EnclyTheme.spacing.iconXSmall), tint = muted)
        }
        Text(text = text, style = EnclyTheme.typography.meta, color = muted)
    }
}

/** "No tag" and every tag as chips, the note's own tag first. */
@Composable
private fun TagChooser(tags: List<Tag>, selected: Tag?, onSelect: (Long) -> Unit) {
    val reorderedTags = remember(tags, selected) {
        if (selected == null) tags else listOf(selected) + tags.filter { it.id != selected.id }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
    ) {
        item {
            TagAssistChip(
                text = stringResource(R.string.no_tag),
                isSelected = selected == null,
                onClick = { onSelect(0) },
            )
        }
        items(reorderedTags) { tag ->
            TagAssistChip(
                text = tag.nameTag,
                isSelected = (selected?.id ?: 0) == tag.id,
                onClick = { onSelect(tag.id) },
            )
        }
    }
}

@Composable
fun TagAssistChip(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EnclyChip(label = text, selected = isSelected, onClick = onClick, modifier = modifier)
}
