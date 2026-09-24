package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

/** Placeholder text is the field's text colour at this opacity. */
const val PLACEHOLDER_ALPHA = 0.72f

/** Validation state of a [EnclyTextField]. */
sealed interface FieldState {
    data object Default : FieldState

    data object Valid : FieldState

    data class Error(val message: String) : FieldState
}

/**
 * A labelled single-line field (design spec §3.3): label above, 52 dp field with radius 12.
 * Rest: 1 dp `outline`. Focused: 2 dp `onSurface`. [FieldState.Valid]: 1 dp `primary` and a
 * trailing "Correct". [FieldState.Error]: 2 dp `error` and the message below. [fieldModifier]
 * reaches the text field itself (a FocusRequester), [modifier] the whole block.
 */
@Composable
fun EnclyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String = "",
    state: FieldState = FieldState.Default,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val (borderWidth, borderColor) = when {
        state is FieldState.Error -> 2.dp to colors.error
        focused -> 2.dp to colors.onSurface
        state is FieldState.Valid -> 1.dp to colors.primary
        else -> 1.dp to colors.outline
    }
    val textColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = textColor),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            modifier = fieldModifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = EnclyTheme.spacing.buttonHeight)
                        .border(borderWidth, borderColor, MaterialTheme.shapes.small)
                        .padding(horizontal = EnclyTheme.spacing.m, vertical = EnclyTheme.spacing.xs),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.onSurface.copy(alpha = PLACEHOLDER_ALPHA),
                            )
                        }
                        inner()
                    }
                    if (state is FieldState.Valid) ValidMark()
                }
            },
        )
        if (state is FieldState.Error) {
            Text(text = state.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun ValidMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Lucide.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.field_correct),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The notes search field: 48 dp, fully round, `surfaceContainer`, no border. */
@Composable
fun EnclySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.minTouchTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(
                start = EnclyTheme.spacing.m,
                end = if (trailing ==
                    null
                ) {
                    EnclyTheme.spacing.m
                } else {
                    4.dp
                },
            ),
        ) {
            Icon(
                Lucide.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

/** A thin `Color` helper so previews and callers can dim content consistently. */
internal fun Color.disabled(): Color = copy(alpha = DISABLED_CONTENT_ALPHA)
