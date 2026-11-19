package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.main_drawer_tags
import com.pasich.encly.core.resources.no_tag
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_description_none
import com.pasich.encly.core.resources.note_description_placeholder
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_tags_more
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.task_description_placeholder
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.platform.vaultTextKeyboardOptions
import com.pasich.encly.presentation.designsystem.EnclyBackButton
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteColorPicker
import com.pasich.encly.presentation.designsystem.EnclySheetGroup
import com.pasich.encly.presentation.designsystem.EnclySheetGroupDivider
import com.pasich.encly.presentation.designsystem.EnclySheetGroupRow
import com.pasich.encly.presentation.designsystem.EnclySheetTiles
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.SheetTile
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.formatNoteDate
import com.pasich.encly.utils.noteInstant
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.pluralStringResource
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

/** The test tag of the sheet's page area: the first page, or the tags or description page. */
internal const val NOTE_SHEET_PAGE_TAG = "note-sheet-page"

/** What the sheet shows under the note's title: its quick settings, or a page one of them leads to. */
private enum class NoteSheetPage { MAIN, TAGS, DESCRIPTION }

/** More tags than this fold into a "+N" chip that opens the full tags page. */
private const val TAG_CHIPS_SHOWN = 6

/**
 * Long-press actions for a note: its title and date, quick action tiles (pin, archive, duplicate, delete),
 * the colour swatches and tag chips that apply on a tap and leave the sheet open, and a description row.
 * The description, and the full list of tags behind "+N", are pages that slide in over the first page and
 * return with Back, the system's or the page's; every page is as high as the first, so the sheet never
 * changes size as they swap.
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
    // Per note and per opening: a sheet always opens on its first page.
    var page by remember(item.note.id, isVisible) { mutableStateOf(NoteSheetPage.MAIN) }

    if (isVisible) {
        AdaptiveSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            scrollable = true,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            // On a page Back returns to the first page; on it Back closes the sheet, as ever.
            BackHandler(enabled = page != NoteSheetPage.MAIN) { page = NoteSheetPage.MAIN }
            NoteActionHeader(item)
            NoteSheetPages(page = page, item = item, onAction = onAction, onPage = { page = it })
        }
    }
}

/**
 * The first page and the pages it leads to, sliding sideways as they swap. The first page sets the height:
 * each page is given the height it measured, and scrolls what does not fit in it.
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

/** The first page: quick actions, colour, tags and the description, spaced as sections. */
@Composable
private fun NoteRows(
    item: NoteWithTags,
    onAction: (NoteAction) -> Unit,
    onPage: (NoteSheetPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m)) {
        EnclySheetTiles(
            tiles = noteTiles(item.note, onAction),
            // The sheet is `surfaceContainer`: tiles on the group colour stand out from it, as its groups do.
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        )
        NoteSection(title = stringResource(Res.string.note_color_title)) {
            EnclyNoteColorPicker(
                selected = NoteColor.fromKey(item.note.color),
                onSelect = { onAction(NoteAction.ChangeColor(it)) },
                modifier = Modifier.bleed(EnclyTheme.spacing.gutter),
                contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter),
            )
        }
        NoteSection(title = stringResource(Res.string.main_drawer_tags)) {
            NoteTagChips(
                selectedTagIds = item.tags.mapTo(HashSet()) { it.id },
                onToggle = { tagId, tagged -> onAction(NoteAction.ChangeTag(tagId, tagged)) },
                onMore = { onPage(NoteSheetPage.TAGS) },
            )
        }
        EnclySheetGroup {
            val shown = item.note.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: stringResource(Res.string.note_description_none)
            // The row shows the description itself; read out, it is named first ("Description, ...").
            val readOut = "${stringResource(Res.string.task_description_placeholder)}, $shown"
            EnclySheetGroupRow(
                title = shown,
                icon = EnclyIcons.Comment,
                chevron = true,
                onClick = { onPage(NoteSheetPage.DESCRIPTION) },
                modifier = Modifier.semantics { contentDescription = readOut },
            )
        }
    }
}

/** A section of the first page: its small [title] label over its [content]. */
@Composable
private fun NoteSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.xxs).semantics { heading() },
        )
        content()
    }
}

/**
 * The quick actions: Pin (Unpin when pinned, none for an archived note), Archive (Unarchive), Duplicate and
 * Delete, which needs a second tap. Edit is not among them: a tap on the note opens it.
 */
@Composable
private fun noteTiles(note: Note, onAction: (NoteAction) -> Unit): List<SheetTile> = buildList {
    if (!note.isArchived) {
        add(
            SheetTile(
                label = stringResource(if (note.isPinned) Res.string.note_unpin else Res.string.note_pin),
                icon = if (note.isPinned) EnclyIcons.PinOff else EnclyIcons.Pin,
                active = note.isPinned,
                onClick = { onAction(NoteAction.Pin(!note.isPinned)) },
            ),
        )
    }
    add(
        SheetTile(
            label = stringResource(if (note.isArchived) Res.string.note_unarchive else Res.string.note_archive),
            icon = if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
            onClick = { onAction(NoteAction.Archive(!note.isArchived)) },
        ),
    )
    add(
        SheetTile(
            label = stringResource(Res.string.duplicate),
            icon = EnclyIcons.Duplicate,
            onClick = { onAction(NoteAction.Duplicate) },
        ),
    )
    add(
        SheetTile(
            label = stringResource(Res.string.delete),
            icon = EnclyIcons.Trash,
            destructive = true,
            confirmFirst = true,
            onClick = { onAction(NoteAction.Delete) },
        ),
    )
}

/**
 * The tags as chips, the note's ones selected; a tap puts a tag on or takes it off at once. Past
 * [TAG_CHIPS_SHOWN] tags the rest fold into a "+N" chip that opens the full list; with no tags at all a
 * muted "No tag" stands in. Until the tags are read nothing is shown: "No tag" must not flash before them.
 */
@Composable
private fun NoteTagChips(
    selectedTagIds: Set<Long>,
    onToggle: (tagId: Long, tagged: Boolean) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    tagListViewModel: TagListViewModel = metroViewModel(),
) {
    val tags by tagListViewModel.state.collectAsState()
    val all = tags.tagsLoad.valueOrNull() ?: return
    if (all.isEmpty()) {
        Text(
            text = stringResource(Res.string.no_tag),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = EnclyTheme.spacing.xxs),
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
    ) {
        // The first tags in their order, and every tag the note has: a note must never look untagged
        // because its tag sits past the fold.
        val shown = all.filterIndexed { index, tag -> index < TAG_CHIPS_SHOWN || tag.id in selectedTagIds }
        shown.forEach { tag ->
            val tagged = tag.id in selectedTagIds
            EnclyChip(label = tag.nameTag, selected = tagged, onClick = { onToggle(tag.id, !tagged) })
        }
        val hidden = all.size - shown.size
        if (hidden > 0) {
            EnclyChip(
                label = "+$hidden",
                selected = false,
                onClick = onMore,
                leadsTo = pluralStringResource(Res.plurals.note_tags_more, hidden, hidden),
            )
        }
    }
}

/** Title and last edit side by side: the sheet's heading. */
@Composable
private fun NoteActionHeader(item: NoteWithTags) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = Modifier.fillMaxWidth().padding(bottom = EnclyTheme.spacing.m),
    ) {
        Text(
            text = item.note.title.ifEmpty { stringResource(Res.string.untitled) },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatNoteDate(noteInstant(item.note.date)),
            style = EnclyTheme.typography.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(bottom = EnclyTheme.spacing.xxs),
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
            keyboardOptions = vaultTextKeyboardOptions(),
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
