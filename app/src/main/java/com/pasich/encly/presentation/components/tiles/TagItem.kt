package com.pasich.encly.presentation.components.tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.pasich.encly.R
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.designsystem.EnclyChip

/** Opacity of a hidden tag's chip. */
private const val HIDDEN_TAG_ALPHA = 0.6f

/** A tag filter chip; a hidden tag is faded. [count] is shown after the name ("All 12"). */
@Composable
fun TagItem(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    item: Tag = Tag(),
    count: Int? = null,
    onItemClick: () -> Unit = {},
) {
    val hiddenLabel = stringResource(R.string.tag_hidden)
    EnclyChip(
        label = item.nameTag,
        selected = isSelected,
        count = count,
        onClick = onItemClick,
        modifier = modifier
            .alpha(if (item.isVisible) 1f else HIDDEN_TAG_ALPHA)
            .semantics { if (!item.isVisible) stateDescription = hiddenLabel },
    )
}
