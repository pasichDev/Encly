package com.pasich.encly.editor.ui.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.theme.LocalEditorTheme

private val LabelGap = 6.dp
private val RestBorder = 1.dp
private val ActiveBorder = 2.dp

/**
 * A labelled single-line field with an outline: 1 dp `outline` at rest, 2 dp `onSurface` while
 * focused, 2 dp `error` with [errorMessage] below it when there is one. [modifier] lays out the whole
 * block, [fieldModifier] reaches the text field itself (a FocusRequester).
 */
@Composable
fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val spacing = LocalEditorTheme.current.spacing
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val (borderWidth, borderColor) = when {
        errorMessage != null -> ActiveBorder to colors.error
        focused -> ActiveBorder to colors.onSurface
        else -> RestBorder to colors.outline
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            modifier = fieldModifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = spacing.buttonHeight)
                        .border(borderWidth, borderColor, MaterialTheme.shapes.small)
                        .padding(horizontal = spacing.m, vertical = spacing.xs),
                ) {
                    Box(modifier = Modifier.weight(1f)) { inner() }
                }
            },
        )
        if (errorMessage != null) {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}
