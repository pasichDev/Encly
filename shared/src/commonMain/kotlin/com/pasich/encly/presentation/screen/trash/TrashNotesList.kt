package com.pasich.encly.presentation.screen.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asString
import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.empty_trash_desc
import com.pasich.encly.core.resources.empty_trash_title
import com.pasich.encly.core.resources.trash_days_left
import com.pasich.encly.core.resources.trash_days_unit
import com.pasich.encly.core.resources.trash_deleted_on
import com.pasich.encly.core.resources.trash_first_in_days
import com.pasich.encly.core.resources.trash_first_today
import com.pasich.encly.core.resources.trash_last_day
import com.pasich.encly.core.resources.trash_notes_count
import com.pasich.encly.core.resources.trash_section_later
import com.pasich.encly.core.resources.trash_section_soon
import com.pasich.encly.core.resources.trash_summary
import com.pasich.encly.core.resources.trash_summary_same
import com.pasich.encly.core.resources.trash_today_unit
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.designsystem.Countdown
import com.pasich.encly.presentation.designsystem.EnclyCountdownRow
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.NoteSkeleton
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.TrashListEvent
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.RelativeDay
import com.pasich.encly.utils.formatCardDate
import com.pasich.encly.utils.formatNoteDate
import com.pasich.encly.utils.noteInstant
import com.pasich.encly.utils.relativeDay
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_CARDS = 3

/**
 * The trashed notes as a countdown: a skeleton while they load, "Trash is empty" or the read error, else a
 * line that says how many there are and when the first and the last go, then the notes that go within a
 * week and the rest, each soonest first, with the days it has left in front. Tapping a note opens it
 * read-only, or toggles it while a selection is open; a long press starts one.
 */
@Composable
fun TrashNotesList(
    onItemClick: (Int, Note) -> Unit,
    modifier: Modifier = Modifier,
    trashViewModel: TrashViewModel = metroViewModel(),
) {
    val listScrollState = rememberLazyListState()
    val state by trashViewModel.state.collectAsStateWithLifecycle()
    // Counted from the moment the list arrived: the days only change with it, or on the next visit.
    val countdown = remember(state.notes) { trashCountdown(state.notes, currentTimeMillis()) }
    val spacing = EnclyTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listScrollState,
        contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.l),
    ) {
        val load = state.notesLoad
        when {
            load is LoadState.Loading -> items(SKELETON_CARDS) {
                NoteSkeleton(modifier = Modifier.padding(horizontal = spacing.listGutter, vertical = spacing.xxs))
            }

            // A failed read is never shown as an empty trash.
            load is LoadState.Failed -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Trash,
                    title = load.error.title.asString(),
                    body = load.error.message.asString(),
                    error = true,
                )
            }

            countdown.count == 0 -> item {
                EnclyEmptyState(
                    icon = EnclyIcons.Trash,
                    title = stringResource(Res.string.empty_trash_title),
                    body = stringResource(Res.string.empty_trash_desc),
                )
            }

            else -> trashCountdownRows(
                countdown = countdown,
                rows = TrashRows(
                    selecting = state.canCheck,
                    onOpen = onItemClick,
                    onToggle = { index -> trashViewModel.onEvent(TrashListEvent.ToggleCheckItem(index)) },
                ),
            )
        }
    }
}

/** What the rows do: [onToggle] selects or unselects the note at an index, [onOpen] opens it to read. */
private class TrashRows(val selecting: Boolean, val onOpen: (Int, Note) -> Unit, val onToggle: (Int) -> Unit)

/** The summary line and the two sections. */
private fun LazyListScope.trashCountdownRows(countdown: TrashCountdown, rows: TrashRows) {
    item(key = "summary") {
        Text(
            text = trashSummary(countdown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter).animateItem(),
        )
    }
    trashSection("soon", countdown.soon, rows) { count ->
        stringResource(Res.string.trash_section_soon, count)
    }
    trashSection("later", countdown.later, rows) { count ->
        stringResource(Res.string.trash_section_later, count)
    }
}

private fun LazyListScope.trashSection(
    key: String,
    entries: List<TrashEntry>,
    rows: TrashRows,
    title: @Composable (Int) -> String,
) {
    if (entries.isEmpty()) return
    item(key = key) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EnclyTheme.spacing.textButtonHeight)
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(bottom = EnclyTheme.spacing.xs)
                .animateItem(),
        ) {
            SectionOverline(text = title(entries.size), modifier = Modifier.semantics { heading() })
        }
    }
    items(items = entries, key = { it.note.id }) { entry ->
        val note = entry.note
        EnclyCountdownRow(
            countdown = countdownOf(entry.daysLeft),
            title = note.title.ifBlank { stringResource(Res.string.untitled) },
            supporting = note.trashedAt?.let { stringResource(Res.string.trash_deleted_on, trashedDate(it)) },
            selected = note.isChecked.takeIf { rows.selecting },
            onClick = { if (rows.selecting) rows.onToggle(entry.index) else rows.onOpen(entry.index, note) },
            onLongClick = { if (!rows.selecting) rows.onToggle(entry.index) },
            modifier = Modifier.animateItem(),
        )
    }
}

/** When a note went to the trash: "Today 14:20", "Yesterday 09:05", then the day and month ("17 Oct"). */
@Composable
private fun trashedDate(millis: Long): String {
    val instant = noteInstant(millis)
    val day = relativeDay(instant, zone = TimeZone.currentSystemDefault())
    return if (day == RelativeDay.OTHER) formatCardDate(instant) else formatNoteDate(instant)
}

/** "8 notes. The first disappears in 3 days, the last in 28." */
@Composable
private fun trashSummary(countdown: TrashCountdown): String {
    val notes = pluralStringResource(Res.plurals.trash_notes_count, countdown.count, countdown.count)
    val first = countdown.firstDays ?: 0
    val last = countdown.lastDays ?: 0
    return if (first == last) {
        stringResource(Res.string.trash_summary_same, notes, daysLeftText(first))
    } else {
        val firstGoes = if (first == 0) {
            stringResource(Res.string.trash_first_today)
        } else {
            pluralStringResource(Res.plurals.trash_first_in_days, first, first)
        }
        stringResource(Res.string.trash_summary, notes, firstGoes, last)
    }
}

/** "3 days left", or "Last day" for a note on its last one. */
@Composable
private fun daysLeftText(days: Int): String = if (days == 0) {
    stringResource(Res.string.trash_last_day)
} else {
    pluralStringResource(Res.plurals.trash_days_left, days, days)
}

@Composable
private fun countdownOf(days: Int): Countdown = Countdown(
    number = days.toString(),
    unit = if (days == 0) {
        stringResource(Res.string.trash_today_unit)
    } else {
        pluralStringResource(Res.plurals.trash_days_unit, days)
    },
    description = daysLeftText(days),
    urgent = days <= TRASH_SOON_DAYS,
)
