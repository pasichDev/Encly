package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.components.tiles.TagItem
import com.pasich.encly.presentation.viewmodel.TagListEvent.SelectTag
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * The tag filter chips of the notes list: "All", then one chip per tag, in one horizontally
 * scrolling row that runs to the screen edges.
 */
@Composable
fun TagsList(modifier: Modifier = Modifier, tagListViewModel: TagListViewModel = hiltViewModel()) {
    val state by tagListViewModel.state.collectAsStateWithLifecycle()
    val gutter = EnclyTheme.spacing.listGutter

    // A failed read says so instead of looking like "no tags".
    val failure = (state.tagsLoad as? LoadState.Failed)?.error
    if (failure != null) {
        Text(
            text = failure.title.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(horizontal = gutter),
        )
        return
    }

    // Show nothing while loading or if the list is empty
    if (state.listTags.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TagItem(
                item = Tag(nameTag = stringResource(R.string.all), id = 0),
                isSelected = state.selectedTagId.toInt() == 0,
                onItemClick = { tagListViewModel.onEvent(SelectTag(Tag(nameTag = "All", id = 0))) },
            )
        }
        items(state.listTags, key = { it.id }) { tag ->
            TagItem(
                item = tag,
                isSelected = tag.id == state.selectedTagId,
                onItemClick = { tagListViewModel.onEvent(SelectTag(tag)) },
            )
        }
    }
}
