package com.pasich.encly.presentation.components.editNote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.pasich.encly.R
import com.pasich.encly.presentation.editor.blocks.WritingKeyboard
import com.pasich.encly.presentation.editor.blocks.rememberSyncedTextFieldState
import com.pasich.encly.presentation.editor.state.textDiff
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.flow.Flow

/**
 * A title is one paragraph. Enter alone (a keyboard that commits "\n" instead of sending the key,
 * as some OEM keyboards do) moves on to the note ([onNext]) like the key does; a line break in
 * pasted text becomes a space.
 */
private class TitleInput(private val onNext: () -> Unit) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val old = originalText.toString()
        val cursor = if (selection.collapsed) selection.start else -1
        val diff = textDiff(old, asCharSequence().toString(), cursor)
        val enterOnly = diff.inserted.isNotEmpty() && diff.inserted.all { it == '\n' || it == '\r' }
        if (enterOnly && diff.removed.isEmpty()) {
            revertAllChanges()
            onNext()
            return
        }
        val text = asCharSequence()
        for (i in text.indices.reversed()) {
            if (text[i] == '\n' || text[i] == '\r') replace(i, i + 1, " ")
        }
    }
}

/**
 * The note's title (headlineLarge, placeholder "Title"). It owns what is typed and hands every
 * edit to [onTitleChange]; [title] and [titleChanges] bring a title set from elsewhere (the note
 * loading). "Next" and Enter move on to the first block ([onNext]). Read-only, not disabled, when
 * [readOnly], so a locked title can still be selected and copied.
 */
@Composable
fun TitleField(
    title: () -> String,
    titleChanges: Flow<*>,
    onTitleChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val fontStyles = rememberFontStyles()
    val state = rememberSyncedTextFieldState(model = title, modelChanges = titleChanges, onEdit = onTitleChange)
    val currentOnNext by rememberUpdatedState(onNext)
    val input = remember { TitleInput { currentOnNext() } }
    val style = MaterialTheme.typography.headlineLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontStyles.sizes.noteTitle,
        fontFamily = fontStyles.families.heading,
    )

    BasicTextField(
        state = state,
        readOnly = readOnly,
        inputTransformation = input,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = WritingKeyboard.copy(imeAction = ImeAction.Next),
        onKeyboardAction = { onNext() },
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter && event.type == KeyEventType.KeyDown
                if (enter && !readOnly) onNext()
                enter
            },
        decorator = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.xxs),
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.note_title_placeholder),
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                innerTextField()
            }
        },
    )
}
