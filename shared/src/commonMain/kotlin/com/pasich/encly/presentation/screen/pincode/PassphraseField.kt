package com.pasich.encly.presentation.screen.pincode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_caps_lock
import com.pasich.encly.core.resources.passphrase_hide
import com.pasich.encly.core.resources.passphrase_show
import com.pasich.encly.platform.capsLockOn
import com.pasich.encly.platform.vaultTextKeyboardOptions
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.designsystem.ProvideTextContextMenu
import com.pasich.encly.presentation.designsystem.providesTextContextMenu
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The masked single-line field of a passphrase over [input], with an eye that shows what is typed. Enter (and the
 * keyboard's Done) calls [onSubmit]; nothing is submitted by itself, and no length is shown. It takes focus when it
 * appears and whenever it is enabled again. [capsLock] says whether Caps Lock is on (null: the host cannot tell); a
 * hint shows while it is, since a masked field gives no sign of it. [onEdit] runs after every edit (to clear an error).
 * The keyboard is the vault's text keyboard with autocorrect off, not the ASCII-only password one of the desktop.
 * The passphrase is kept off the clipboard: the copy and cut keys are swallowed (masked or not, so a cut deletes
 * nothing either), there is no selection toolbar, and the right-click menu of a desktop lists Paste and Select all
 * only (a passphrase is pasted in from a password manager; nothing leaves). Where the host has no such menu, the
 * right button is refused. Dragging selected text out is not stopped.
 */
@Composable
internal fun PassphraseField(
    input: PassphraseInput,
    label: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onEdit: () -> Unit = {},
    capsLock: () -> Boolean? = ::capsLockOn,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) runCatching { focus.requestFocus() } }
    var focused by remember { mutableStateOf(false) }
    var capsOn by remember { mutableStateOf(false) }
    val mask = remember { PasswordVisualTransformation() }
    Column(modifier = modifier) {
        // No selection toolbar (Android, iOS) and a pointer menu without Cut and Copy (desktop): nothing here offers to
        // copy the text.
        CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
            ProvideTextContextMenu(allowCopyAndCut = false) {
                EnclyTextField(
                    value = input.text,
                    onValueChange = {
                        input.edit(it)
                        onEdit()
                    },
                    label = label,
                    enabled = enabled,
                    keyboardOptions = vaultTextKeyboardOptions(ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }, onGo = { onSubmit() }),
                    visualTransformation = if (input.revealed) VisualTransformation.None else mask,
                    trailing = { RevealToggle(input) },
                    labelInSemantics = false,
                    fieldModifier = Modifier
                        .then(if (providesTextContextMenu) Modifier else Modifier.refuseSecondaryButton())
                        .focusRequester(focus)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) capsOn = capsLock() == true
                        }
                        .onPreviewKeyEvent { event ->
                            capsOn = capsLock() == true
                            when {
                                // Copy and cut are never run here: a cut would delete what a copy could not keep.
                                event.copiesOrCutsText() -> true

                                // The keyboard action may fire for the same press: it finds nothing left to send.
                                event.type == KeyEventType.KeyDown && event.isEnter() -> {
                                    onSubmit()
                                    true
                                }

                                else -> false
                            }
                        }
                        .semantics { contentDescription = label },
                )
            }
        }
        if (focused && capsOn) {
            Text(
                text = stringResource(Res.string.passphrase_caps_lock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = EnclyTheme.spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A toolbar that never shows: the selection of the passphrase field offers no copy, cut or paste. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit
}

/** Eats a press of the secondary (right) button before the field sees it: the field's own menu never opens. */
private fun Modifier.refuseSecondaryButton(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.buttons.isSecondaryPressed) event.changes.forEach { it.consume() }
        }
    }
}

private fun KeyEvent.isEnter(): Boolean = key == Key.Enter || key == Key.NumPadEnter

/**
 * Copy and cut, by the keys of every desktop: Ctrl or Command with C, X or Insert, Shift+Delete, and the dedicated
 * Copy and Cut keys, which Compose's desktop key map runs without any modifier.
 */
private fun KeyEvent.copiesOrCutsText(): Boolean {
    val command = isCtrlPressed || isMetaPressed
    return (command && (key == Key.C || key == Key.X || key == Key.Insert)) ||
        (isShiftPressed && key == Key.Delete) ||
        key == Key.Copy ||
        key == Key.Cut
}

/** The eye at the end of the field: shows the passphrase in clear, or masks it again. */
@Composable
private fun RevealToggle(input: PassphraseInput) {
    val revealed = input.revealed
    IconWithTooltip(
        icon = if (revealed) EnclyIcons.EyeOff else EnclyIcons.Eye,
        label = stringResource(if (revealed) Res.string.passphrase_hide else Res.string.passphrase_show),
        onClick = { input.revealed = !revealed },
    )
}
