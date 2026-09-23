package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.components.tiles.TagItem
import com.pasich.encly.presentation.viewmodel.TagListEvent.SelectTag
import com.pasich.encly.presentation.viewmodel.TagListViewModel

@Composable
fun TagsList(modifier: Modifier = Modifier, tagListViewModel: TagListViewModel = hiltViewModel()) {
    val space = 6.dp
    val state by tagListViewModel.state.collectAsStateWithLifecycle()

    // A failed read says so instead of looking like "no tags".
    val failure = (state.tagsLoad as? LoadState.Failed)?.error
    if (failure != null) {
        Text(
            text = failure.title.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        return
    }

    // Show nothing while loading or if the list is empty
    if (state.listTags.isEmpty()) return

    // Animated appearance of the list after loading
    AnimatedVisibility(
        modifier = modifier,
        visible = state.listTags.isNotEmpty(),
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { -it / 2 }, // Appears from the top
        ),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth().padding(bottom = 10.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                TagItem(
                    item = Tag(nameTag = stringResource(R.string.all), id = 0),
                    isSelected = state.selectedTagId.toInt() == 0,
                    modifier = Modifier.padding(start = 20.dp, end = space),
                    onItemClick = {
                        tagListViewModel.onEvent(SelectTag(Tag(nameTag = "All", id = 0)))
                    },
                )
            }

            items(state.listTags) { tag ->
                TagItem(
                    item = tag,
                    isSelected = tag.id == state.selectedTagId,
                    modifier = Modifier.padding(horizontal = space),
                    onItemClick = {
                        tagListViewModel.onEvent(SelectTag(tag))
                    },
                )
            }
        }
    }
}
