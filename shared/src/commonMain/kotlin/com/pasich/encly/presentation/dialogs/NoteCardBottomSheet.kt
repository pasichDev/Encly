package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.edit
import com.pasich.encly.core.resources.main_drawer_tags
import com.pasich.encly.core.resources.no_tag
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_description_placeholder
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.task_description_placeholder
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.presentation.designsystem.EnclyBackButton
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteColorGrid
import com.pasich.encly.presentation.designsystem.EnclySheetGroup
import com.pasich.encly.presentation.designsystem.EnclySheetGroupDivider
import com.pasich.encly.presentation.designsystem.EnclySheetGroupRow
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.SheetGroupGap
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.noteColorStyle
import com.pasich.encly.utils.formatNoteDate
import com.pasich.encly.utils.noteInstant
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

sealed class NoteAction {
    object Edit : NoteAction()
    object Duplicate : NoteAction()
    object Delete : NoteAction()
    data class Pin(val pinned: Boolean) : NoteAction()
    data class Archive(val archived: Boolean) : NoteAction()
    data class ChangeColor(val color: NoteColor) : NoteAction()

    /** Puts the tag [tagId] on the note or takes it off ([tagged]); 0 takes every tag off. */
    data class ChangeTag(val tagId: Long, val tagged: Boolean = true) : NoteAction()
    data class ChangeDescription(val description: String) : NoteAction()
}

/** The test tag of the sheet's page area: the first page, or the tags, colour or description page. */
internal const val NOTE_SHEET_PAGE_TAG = "note-sheet-page"

/** What the sheet shows under the note's title: its rows, or one of the pages a row leads to. */
private enum class NoteSheetPage { MAIN, TAGS, COLOR, DESCRIPTION }

/**
 * Long-press actions for a note, as a grouped list: the note's tags, colour and description (each a row
 * that shows its value and leads to a page of its own inside the sheet), then its actions, then Delete.
 * A page slides in over the rows and Back, the system's or the page's, returns to them; every page is as
 * high as the rows, so the sheet never changes size as they swap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCardBottomSheet(
    isVisible: Boolean,
    item: NoteWithTags,
    onAction: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    // Per note and per opening: a sheet always opens on its rows.
    var page by remember(item.note.id, isVisible) { mutableStateOf(NoteSheetPage.MAIN) }

    if (isVisible) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            scrollable = true,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            // On a page Back returns to the rows; on the rows it closes the sheet, as ever.
            BackHandler(enabled = page != NoteSheetPage.MAIN) { page = NoteSheetPage.MAIN }
            NoteActionHeader(item)
            NoteSheetPages(page = page, item = item, onAction = onAction, onPage = { page = it })
        }
    }
}

/**
 * The rows and the pages they lead to, sliding sideways as they swap. The rows set the height: each page
 * is given the height the rows measured, and scrolls what does not fit in it.
 */
@Composable
private fun NoteSheetPages(
    page: NoteSheetPage,
    item: NoteWithTags,
    onAction: (NoteAction) -> Unit,
    onPage: (NoteSheetPage) -> Unit,
) {
    var rowsHeight by remember { mutableIntStateOf(0) }
    val asHighAsRows = if (rowsHeight > 0) {
        Modifier.height(with(LocalDensity.current) { rowsHeight.toDp() })
    } else {
        Modifier
    }
    val gutter = EnclyTheme.spacing.gutter
    val back = { onPage(NoteSheetPage.MAIN) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val towards = if (targetState == NoteSheetPage.MAIN) SlideDirection.End else SlideDirection.Start
            slideIntoContainer(towards, EnclyMotion.long()) togetherWith
                slideOutOfContainer(towards, EnclyMotion.long()) using
                SizeTransform(clip = true) { _, _ -> snap() }
        },
        // Across the sheet's whole width, so a page slides in from its edge, not from the gutter.
        modifier = Modifier.bleed(gutter).testTag(NOTE_SHEET_PAGE_TAG),
        label = "noteSheetPage",
    ) { shown ->
        val inGutter = Modifier.padding(horizontal = gutter)
        val asPage = inGutter.then(asHighAsRows)
        when (shown) {
            NoteSheetPage.MAIN -> NoteRows(item, onAction, onPage, inGutter.onSizeChanged { rowsHeight = it.height })

            NoteSheetPage.TAGS -> TagsPage(
                selectedTagIds = item.tags.mapTo(HashSet()) { it.id },
                onToggle = { tagId, tagged -> onAction(NoteAction.ChangeTag(tagId, tagged)) },
                onBack = back,
                modifier = asPage,
            )

            NoteSheetPage.COLOR -> ColorPage(
                selected = NoteColor.fromKey(item.note.color),
                onSelect = { onAction(NoteAction.ChangeColor(it)) },
                onBack = back,
                modifier = asPage,
            )

            NoteSheetPage.DESCRIPTION -> DescriptionPage(
                initial = item.note.description,
                // The keyboard is asked for once the page stands still, not under its slide.
                arrived = { transition.currentState == transition.targetState },
                onSave = {
                    onAction(NoteAction.ChangeDescription(it))
                    back()
                },
                onBack = back,
                modifier = asPage,
            )
        }
    }
}

/** The first page: what the note is set to, what can be done with it, and Delete on its own. */
@Composable
private fun NoteRows(
    item: NoteWithTags,
    onAction: (NoteAction) -> Unit,
    onPage: (NoteSheetPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SheetGroupGap)) {
        NoteSettingRows(item, onPage)
        NoteActionRows(item.note, onAction)
        EnclySheetGroup {
            EnclySheetGroupRow(
                title = stringResource(Res.string.delete),
                icon = EnclyIcons.Trash,
                destructive = true,
                confirmFirst = true,
                onClick = { onAction(NoteAction.Delete) },
            )
        }
    }
}

/** Tags, colour and description: each shows its value and leads to its page. */
@Composable
private fun NoteSettingRows(item: NoteWithTags, onPage: (NoteSheetPage) -> Unit) {
    val color = NoteColor.fromKey(item.note.color)
    EnclySheetGroup {
        EnclySheetGroupRow(
            title = stringResource(Res.string.main_drawer_tags),
            icon = EnclyIcons.Tag,
            value = item.tags.joinToString { it.nameTag }.ifEmpty { stringResource(Res.string.no_tag) },
            chevron = true,
            onClick = { onPage(NoteSheetPage.TAGS) },
        )
        EnclySheetGroupDivider()
        EnclySheetGroupRow(
            title = stringResource(Res.string.note_color_title),
            icon = EnclyIcons.Palette,
            value = stringResource(color.labelRes),
            valueDot = noteColorStyle(color, MaterialTheme.colorScheme)?.accent,
            chevron = true,
            onClick = { onPage(NoteSheetPage.COLOR) },
        )
        EnclySheetGroupDivider()
        EnclySheetGroupRow(
            title = stringResource(Res.string.task_description_placeholder),
            icon = EnclyIcons.Comment,
            value = item.note.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: stringResource(Res.string.note_description_placeholder),
            chevron = true,
            onClick = { onPage(NoteSheetPage.DESCRIPTION) },
        )
    }
}

/** Edit, pin, archive and duplicate; a pinned note offers Unpin, an archived one Unarchive and no pin. */
@Composable
private fun NoteActionRows(note: Note, onAction: (NoteAction) -> Unit) {
    EnclySheetGroup {
        EnclySheetGroupRow(
            title = stringResource(Res.string.edit),
            icon = EnclyIcons.Edit,
            onClick = { onAction(NoteAction.Edit) },
        )
        EnclySheetGroupDivider()
        if (!note.isArchived) {
            EnclySheetGroupRow(
                title = stringResource(if (note.isPinned) Res.string.note_unpin else Res.string.note_pin),
                icon = if (note.isPinned) EnclyIcons.PinOff else EnclyIcons.Pin,
                onClick = { onAction(NoteAction.Pin(!note.isPinned)) },
            )
            EnclySheetGroupDivider()
        }
        EnclySheetGroupRow(
            title = stringResource(if (note.isArchived) Res.string.note_unarchive else Res.string.note_archive),
            icon = if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
            onClick = { onAction(NoteAction.Archive(!note.isArchived)) },
        )
        EnclySheetGroupDivider()
        EnclySheetGroupRow(
            title = stringResource(Res.string.duplicate),
            icon = EnclyIcons.Duplicate,
            onClick = { onAction(NoteAction.Duplicate) },
        )
    }
}

/** Title and last edit: the sheet's heading. */
@Composable
private fun NoteActionHeader(item: NoteWithTags) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = Modifier.fillMaxWidth().padding(bottom = EnclyTheme.spacing.xs),
    ) {
        Text(
            text = item.note.title.ifEmpty { stringResource(Res.string.untitled) },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatNoteDate(noteInstant(item.note.date)),
            style = EnclyTheme.typography.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A page a row leads to: a back button and its [title], then its [content]. */
@Composable
private fun NotePage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnclyBackButton(onClick = onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

/**
 * "No tag" and every tag, the note's ones checked: a tap puts a tag on or takes it off at once, and
 * "No tag" takes every tag off. Many tags scroll inside the page.
 */
@Composable
private fun TagsPage(
    selectedTagIds: Set<Long>,
    onToggle: (tagId: Long, tagged: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tagListViewModel: TagListViewModel = metroViewModel(),
) {
    val state by tagListViewModel.state.collectAsState()
    NotePage(title = stringResource(Res.string.main_drawer_tags), onBack = onBack, modifier = modifier) {
        EnclySheetGroup(modifier = Modifier.weight(1f, fill = false)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val untagged = selectedTagIds.isEmpty()
                EnclySheetGroupRow(
                    title = stringResource(Res.string.no_tag),
                    selected = untagged,
                    onClick = { if (!untagged) onToggle(0L, false) },
                    modifier = Modifier.semantics { selected = untagged },
                )
                state.listTags.forEach { tag ->
                    val tagged = tag.id in selectedTagIds
                    EnclySheetGroupDivider()
                    EnclySheetGroupRow(
                        title = tag.nameTag,
                        icon = EnclyIcons.Tag,
                        selected = tagged,
                        onClick = { onToggle(tag.id, !tagged) },
                        modifier = Modifier.semantics { selected = tagged },
                    )
                }
            }
        }
    }
}

/** The note's colour: every swatch at once, applied on a tap. */
@Composable
private fun ColorPage(
    selected: NoteColor,
    onSelect: (NoteColor) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NotePage(title = stringResource(Res.string.note_color_title), onBack = onBack, modifier = modifier) {
        EnclySheetGroup {
            EnclyNoteColorGrid(
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.padding(EnclyTheme.spacing.s),
            )
        }
    }
}

/**
 * The description as a field with Save; going back leaves it as it was. The field takes focus (and the
 * keyboard comes up) once the page has [arrived]: asked for under the slide, the keyboard would move the
 * sheet while its page is still moving in it.
 */
@Composable
private fun DescriptionPage(
    initial: String,
    arrived: () -> Boolean,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val currentArrived by rememberUpdatedState(arrived)
    LaunchedEffect(focus) {
        snapshotFlow { currentArrived() }.first { it }
        runCatching { focus.requestFocus() }
    }
    NotePage(title = stringResource(Res.string.task_description_placeholder), onBack = onBack, modifier = modifier) {
        EnclyTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(Res.string.note_description_placeholder),
            fieldModifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = false,
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        )
        EnclyButton(
            text = stringResource(Res.string.save),
            onClick = { onSave(text.trim()) },
            modifier = Modifier.align(Alignment.End),
        )
    }
}
