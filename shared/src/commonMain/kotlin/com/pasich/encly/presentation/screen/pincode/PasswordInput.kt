package com.pasich.encly.presentation.screen.pincode

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pasich.encly.core.security.MAX_PASSWORD_LENGTH
import com.pasich.encly.core.security.MIN_PASSWORD_LENGTH
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.FieldState

/**
 * A password being typed. A text field keeps its text as a String, which cannot be wiped (the recovery words
 * are no different): it is held here only while the form is up, dropped when the password is taken for the
 * check, and the check wipes the CharArray it is given.
 */
@Stable
internal class PasswordInput {
    var text by mutableStateOf("")

    /** The characters typed, as Unicode code points (a surrogate pair is one). */
    val length: Int get() = text.count { !it.isLowSurrogate() }

    /** Long enough to be a password. Nothing is submitted by itself: the user presses the button. */
    val canSubmit: Boolean get() = length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH

    /** The typed password, taken out of the form; the text is dropped. The caller wipes the copy. */
    fun take(): CharArray = text.toCharArray().also { clear() }

    fun clear() {
        text = ""
    }
}

/** A masked password field over [input]; [onDone] is the keyboard's Done action. */
@Composable
internal fun PasswordField(
    input: PasswordInput,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onEdit: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    EnclyTextField(
        value = input.text,
        onValueChange = {
            input.text = it.take(MAX_PASSWORD_LENGTH * 2)
            onEdit()
        },
        label = label,
        modifier = modifier,
        fieldModifier = fieldModifier,
        enabled = enabled,
        state = if (error != null) FieldState.Error(error) else FieldState.Default,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        visualTransformation = PasswordVisualTransformation(),
    )
}
