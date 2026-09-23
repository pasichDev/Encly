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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.titleNote

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
        titleNote.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontStyles.sizes.noteTitle,
            fontFamily = fontStyles.families.heading,
        ),
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        decorationBox = { innerTextField ->
            TitleDecoration(
                showPlaceholder = title.isEmpty(),
                placeholderText = placeholderText,
                innerTextField = innerTextField,
            )
        },
    )
}

@Composable
private fun TitleDecoration(
    showPlaceholder: Boolean,
    placeholderText: String,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
    ) {
        if (showPlaceholder) {
            Text(
                text = placeholderText,
                style =
                titleNote.copy(
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        innerTextField()
    }
}
