package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.R
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySearchField
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel

private const val FOCUS_FRAMES = 3

/**
 * Find in this note: the query, "n of m" and previous/next, under the editor bar. Read-only, so it
 * also works in a locked or trashed note. The query lives in the ViewModel and nowhere else (not in
 * saved state), and the whole bar is gone when the vault locks.
 */
@Composable
internal fun EditorFindBar(modifier: Modifier = Modifier, viewModel: EditNoteViewModel = metroViewModel()) {
    val state by viewModel.find.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) {
        // The field is in the tree after a frame or two; asking for focus brings the keyboard up.
        repeat(FOCUS_FRAMES) { withFrameNanos { } }
        runCatching { focus.requestFocus() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = EnclyTheme.spacing.xs, vertical = EnclyTheme.spacing.xxs),
    ) {
        EnclySearchField(
            value = state.query,
            onValueChange = viewModel::onFindQueryChanged,
            placeholder = stringResource(R.string.find_placeholder),
            modifier = Modifier.weight(1f),
            fieldModifier = Modifier.focusRequester(focus),
            onSubmit = viewModel::findNext,
            trailing = { MatchCount(state) },
        )
        val hasMatches = state.matches.isNotEmpty()
        IconWithTooltip(
            EnclyIcons.ArrowUp,
            stringResource(R.string.find_previous),
            viewModel::findPrevious,
            enabled = hasMatches,
        )
        IconWithTooltip(
            EnclyIcons.ArrowDown,
            stringResource(R.string.find_next),
            viewModel::findNext,
            enabled = hasMatches,
        )
        IconWithTooltip(EnclyIcons.Close, stringResource(R.string.find_close), viewModel::closeFind)
    }
}

/** "2 of 5", "No matches" once something is typed, nothing before; announced as it changes. */
@Composable
private fun MatchCount(state: FindState, modifier: Modifier = Modifier) {
    val text = when {
        state.query.isBlank() -> return
        state.matches.isEmpty() -> stringResource(R.string.find_no_matches)
        else -> stringResource(R.string.find_count, state.current + 1, state.matches.size)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = EnclyTheme.spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
