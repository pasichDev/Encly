package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * What a countdown row counts: the [number] and its [unit] ("3" over "days"), read out as [description]
 * ("3 days left"). [urgent] tints it `error`.
 */
class Countdown(val number: String, val unit: String, val description: String, val urgent: Boolean = false)

/**
 * A row that leads with a countdown (a note in the trash): the number large at the start, then the [title]
 * on one line over a [supporting] line, and a hairline under it. [selected] is null outside a selection;
 * in one, a checkbox at the end shows whether this row is part of it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnclyCountdownRow(
    countdown: Countdown,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .enclyInteractive()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics { if (selected != null) this.selected = selected }
                .heightIn(min = RowMinHeight)
                .padding(start = RowStart, end = RowEnd),
        ) {
            CountdownNumber(countdown)
            Column(
                verticalArrangement = Arrangement.spacedBy(TextGap),
                modifier = Modifier.weight(1f).padding(vertical = EnclyTheme.spacing.s),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = EnclyTheme.typography.dataSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected != null) {
                EnclyCheckbox(checked = selected, onCheckedChange = null, size = CheckboxSize.COMPACT)
            }
        }
        HorizontalDivider(
            thickness = EnclyTheme.spacing.hairline,
            color = colors.outlineVariant,
            modifier = Modifier.padding(start = RowStart + NumberWidth, end = RowEnd),
        )
    }
}

/** The number over its unit, one thing to a screen reader: what it counts. */
@Composable
private fun CountdownNumber(countdown: Countdown) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(NumberWidth)
            .clearAndSetSemantics { contentDescription = countdown.description },
    ) {
        Text(
            text = countdown.number,
            style = EnclyTheme.typography.dataLarge.copy(fontSize = 22.sp, lineHeight = 24.sp),
            color = if (countdown.urgent) colors.error else colors.onSurface,
            maxLines = 1,
        )
        Text(
            text = countdown.unit,
            style = EnclyTheme.typography.dataSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = if (countdown.urgent) colors.error else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private val RowMinHeight = 66.dp
private val RowStart = 12.dp
private val RowEnd = 20.dp
private val NumberWidth = 60.dp
private val TextGap = 2.dp
