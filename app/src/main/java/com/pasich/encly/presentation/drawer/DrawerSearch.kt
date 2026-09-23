package com.pasich.encly.presentation.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.components.HighlightedText
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel

private const val FADE_MS = 300
private const val PREVIEW_MAX_LINES = 5

/**
 * Note search in the navigation drawer. It covers every note (not only the tag chip the list
 * shows) and lists every match; [onNoteClick] opens one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerSearch(
    onNoteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    searchViewModel: NoteSearchViewModel = hiltViewModel(),
) {
    // The field reads local state (a flow round trip can drop fast keystrokes); the ViewModel
    // gets every change.
    var query by remember { mutableStateOf(searchViewModel.query.value) }
    val results by searchViewModel.results.collectAsStateWithLifecycle()
    var isActive by remember { mutableStateOf(false) }

    fun reset() {
        query = ""
        searchViewModel.clear()
        isActive = false
    }

    SearchBar(
        query = query,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onQueryChange = {
            query = it
            searchViewModel.onQueryChange(it)
            isActive = it.isNotBlank()
        },
        onSearch = {},
        placeholder = {
            Text(
                text = stringResource(R.string.search_notes),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.search_notes),
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = { SearchClearButton(visible = query.isNotBlank(), onClear = ::reset) },
        active = isActive,
        onActiveChange = { isActive = it },
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
        content = {
            SearchContent(
                query = query,
                results = results,
                onOpen = { id ->
                    reset()
                    onNoteClick(id)
                },
            )
        },
    )
}

@Composable
private fun SearchClearButton(visible: Boolean, onClear: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS))) {
        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Clear,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.search_clear),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SearchContent(query: String, results: NoteSearchViewModel.SearchResults, onOpen: (Long) -> Unit) {
    // Results that belong to what is typed now (input is debounced).
    val resultsAreCurrent = results.query == query.trim()
    AnimatedVisibility(
        visible = results.notes.isNotEmpty(),
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
    ) {
        SearchResultsList(results, onOpen)
    }
    AnimatedVisibility(
        visible = query.isNotBlank() && resultsAreCurrent && results.notes.isEmpty(),
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
    ) {
        SearchNoResults()
    }
    AnimatedVisibility(
        visible = query.isEmpty(),
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SearchResultsList(results: NoteSearchViewModel.SearchResults, onOpen: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(results.notes, key = { it.note.id }) { item: NoteListItem ->
            SearchResultItem(
                title = item.note.title.ifBlank { stringResource(R.string.untitled) },
                content = item.preview ?: stringResource(R.string.note_preview_unreadable),
                searchQuery = results.query,
                onClick = { onOpen(item.note.id) },
            )
        }
    }
}

@Composable
private fun SearchNoResults() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.search_try_other_keywords),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SearchResultItem(title: String, content: String, searchQuery: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = title,
                    searchQuery = searchQuery,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HighlightedText(
                        text = content,
                        searchQuery = searchQuery,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = PREVIEW_MAX_LINES,
                    )
                }
            }
        }
    }
}
