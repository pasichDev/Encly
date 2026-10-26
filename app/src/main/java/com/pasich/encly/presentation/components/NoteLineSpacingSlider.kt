package com.pasich.encly.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.domain.model.NoteLineSpacing
import kotlin.math.roundToInt

/** The reading densities in display order; the enum's own order is fixed by what is stored. */
internal val lineSpacingOrder = listOf(
    NoteLineSpacing.COMPACT to R.string.note_line_spacing_compact,
    NoteLineSpacing.DEFAULT to R.string.note_line_spacing_default,
    NoteLineSpacing.COMFORTABLE to R.string.note_line_spacing_comfortable,
)

/** The reading density as a three-step slider, in the same row style as the text size. */
@Composable
fun NoteLineSpacingSlider(
    selected: NoteLineSpacing,
    onSelect: (NoteLineSpacing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = lineSpacingOrder.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LabeledSlider(
        modifier = modifier,
        label = stringResource(R.string.note_line_spacing_title),
        valueText = stringResource(lineSpacingOrder[index].second),
        value = index.toFloat(),
        onValueChange = { onSelect(lineSpacingOrder[it.roundToInt().coerceIn(lineSpacingOrder.indices)].first) },
        scale = SliderScale(0f..lineSpacingOrder.lastIndex.toFloat(), steps = lineSpacingOrder.size - 2),
    )
}
