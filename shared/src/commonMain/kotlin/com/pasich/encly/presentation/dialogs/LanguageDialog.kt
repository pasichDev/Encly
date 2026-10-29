package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.language_dialog_title
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import org.jetbrains.compose.resources.stringResource

/**
 * Picks the in-app language. Every language is listed under its own name; only
 * "System default" is shown in the current UI language.
 */
@Composable
fun LanguageDialog(
    current: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(current) }

    EnclyDialog(
        title = stringResource(Res.string.language_dialog_title),
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirm = DialogAction(stringResource(Res.string.done), { onConfirm(selected) }),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    ) {
        LazyColumn(modifier = Modifier.selectableGroup()) {
            items(AppLanguage.entries) { language ->
                EnclyRadioRow(
                    title = stringResource(language.nativeName),
                    selected = language == selected,
                    onClick = { selected = language },
                )
            }
        }
    }
}
