package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.FieldState
import com.pasich.encly.presentation.viewmodel.AnswerState
import com.pasich.encly.presentation.viewmodel.phraseAnswerState
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * "Word #n" fields for typing back words of a new phrase ([checks]: 0-based position and the
 * word). Each says at once when it is right, and that it is wrong once it is as long as the word.
 * Next moves down, Done on the last closes the keyboard. Used by onboarding and by Backup.
 */
@Composable
internal fun PhraseCheckFields(
    checks: List<Pair<Int, String>>,
    answers: Map<Int, String>,
    onAnswer: (Int, String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.fieldGap)) {
        checks.forEachIndexed { position, (index, word) ->
            val number = index + 1
            val last = position == checks.lastIndex
            val answer = answers[index].orEmpty()
            val fieldState = when (phraseAnswerState(word, answer)) {
                AnswerState.CORRECT -> FieldState.Valid
                AnswerState.WRONG -> FieldState.Error(stringResource(R.string.onboarding_verify_wrong, number))
                AnswerState.EMPTY, AnswerState.TYPING -> FieldState.Default
            }
            EnclyTextField(
                value = answer,
                onValueChange = { onAnswer(index, it) },
                label = stringResource(R.string.onboarding_verify_label, number),
                placeholder = stringResource(R.string.onboarding_verify_placeholder),
                state = fieldState,
                enabled = enabled,
                textStyle = EnclyTheme.typography.dataLarge,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus() },
                ),
            )
        }
    }
}

/** True when every word of [checks] is typed back right. */
internal fun allChecksCorrect(checks: List<Pair<Int, String>>, answers: Map<Int, String>): Boolean =
    checks.isNotEmpty() &&
        checks.all { (index, word) -> phraseAnswerState(word, answers[index].orEmpty()) == AnswerState.CORRECT }
