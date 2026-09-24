package com.pasich.encly.presentation.components.editNote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

/** The title placeholder follows the title's size and family. */
private class PlaceholderStyle(val size: TextUnit, val family: FontFamily)

@Composable
fun TitleField(
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    placeholderText: String = stringResource(R.string.new_page),
    title: String = "",
    onTitleChange: (String) -> Unit = {},
) {
    val fontStyles = rememberFontStyles()

    BasicTextField(
        value = title,
        enabled = !readOnly,
        onValueChange = { onTitleChange(it) },
        textStyle =
        MaterialTheme.typography.headlineLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontStyles.sizes.noteTitle,
            fontFamily = fontStyles.families.heading,
        ),
        modifier =
        modifier.fillMaxWidth(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        decorationBox = { innerTextField ->
            TitleDecoration(
                showPlaceholder = title.isEmpty(),
                placeholderText = placeholderText,
                placeholderStyle = PlaceholderStyle(fontStyles.sizes.noteTitle, fontStyles.families.heading),
                innerTextField = innerTextField,
            )
        },
    )
}

@Composable
private fun TitleDecoration(
    showPlaceholder: Boolean,
    placeholderText: String,
    placeholderStyle: PlaceholderStyle,
    innerTextField: @Composable () -> Unit,
) {
    val fontSize = placeholderStyle.size
    val fontFamily = placeholderStyle.family
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.xxs),
    ) {
        if (showPlaceholder) {
            Text(
                text = placeholderText,
                style =
                MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                ),
            )
        }
        innerTextField()
    }
}
