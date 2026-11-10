package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.noteColorStyle
import org.jetbrains.compose.resources.stringResource

private val SwatchSize = 36.dp
private const val SLASH_INSET = 0.28f

/**
 * A row of round colour swatches, one per [NoteColor]: the hue of each as it looks on the current
 * theme, a ring and a check on the [selected] one. "No color" is an empty circle with a slash.
 * Every swatch is a radio button named after its colour, and 48 dp to touch.
 */
@Composable
fun EnclyNoteColorPicker(
    selected: NoteColor,
    onSelect: (NoteColor) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
    ) {
        items(NoteColor.entries, key = { it.key }) { color ->
            ColorSwatch(color = color, selected = color == selected, onClick = { onSelect(color) })
        }
    }
}

@Composable
private fun ColorSwatch(color: NoteColor, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = remember(color, scheme) { noteColorStyle(color, scheme) }
    val label = stringResource(color.labelRes)
    val fill = style?.accent ?: scheme.surfaceContainerHigh
    val mark = style?.onAccent ?: scheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(EnclyTheme.spacing.minTouchTarget)
            .enclyInteractiveRound()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Surface(
            shape = CircleShape,
            color = fill,
            border = BorderStroke(
                width = if (selected) EnclyTheme.spacing.stroke else EnclyTheme.spacing.hairline,
                color = if (selected) scheme.primary else scheme.outlineVariant,
            ),
            modifier = Modifier.size(SwatchSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    selected -> Icon(
                        EnclyIcons.CheckBold,
                        contentDescription = null,
                        tint = mark,
                        modifier = Modifier.size(EnclyTheme.spacing.iconXSmall),
                    )

                    style == null -> NoColorSlash(scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The diagonal of "no color". */
@Composable
private fun NoColorSlash(tint: Color) {
    val width = EnclyTheme.spacing.stroke
    Canvas(modifier = Modifier.size(SwatchSize)) {
        val inset = size.width * SLASH_INSET
        drawLine(
            color = tint,
            start = Offset(inset, size.height - inset),
            end = Offset(size.width - inset, inset),
            strokeWidth = width.toPx(),
        )
    }
}
