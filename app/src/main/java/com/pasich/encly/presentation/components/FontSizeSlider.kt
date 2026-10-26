package com.pasich.encly.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

private const val MIN_SIZE = 10f
private const val MAX_SIZE = 32f

/** One step per size between [MIN_SIZE] and [MAX_SIZE]. */
private const val SIZE_STEPS = 21

/** The editor text size: a labelled slider with the value in the data style. */
@Composable
fun FontSizeSlider(currentSize: Int, onSizeChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    LabeledSlider(
        label = stringResource(R.string.font_size_title),
        valueText = stringResource(R.string.font_size_value, currentSize),
        value = currentSize.toFloat(),
        onValueChange = { onSizeChange(it.toInt()) },
        range = MIN_SIZE..MAX_SIZE,
        steps = SIZE_STEPS,
        modifier = modifier,
    )
}

/**
 * A labelled slider: [label] on the left, [valueText] on the right in the data style, the slider
 * below. Shared by the editor's text size and line spacing, so both rows look the same.
 */
@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = EnclyTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
