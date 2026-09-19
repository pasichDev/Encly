package com.pasich.encly.presentation.components.editNote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.titleNote
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleField(
    enabled: Boolean = true,
    placeholderText: String = "stringResource(R.string.new_page)",
    title: String = "",
    onTitleChange: (String) -> Unit = {},
    useNewFocusSystem: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Auto-scroll on focus (only for the new system)
    if (useNewFocusSystem) {
        LaunchedEffect(isFocused) {
            if (isFocused) {
                delay(100L) // Delay for the keyboard
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    val fontStyles = rememberFontStyles()
    
    BasicTextField(
        value = title,
        enabled = !enabled,
        onValueChange = { onTitleChange(it) },
        textStyle =
            titleNote.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = fontStyles.sizes.noteTitle,
                fontFamily = fontStyles.families.heading
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp)
                .let { modifier ->
                    if (useNewFocusSystem) {
                        modifier
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            }
                    } else {
                        modifier
                    }
                },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp),
            ) {
                if (title.isEmpty()) {
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
        },
    )
}
