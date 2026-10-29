package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.settings_swipe_left
import com.pasich.encly.core.resources.settings_swipe_right
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.presentation.components.swipe.choiceLabel
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import org.jetbrains.compose.resources.stringResource

/** Picks what swiping a note card to [side] does. */
@Composable
fun SwipeActionDialog(
    side: SwipeSide,
    current: SwipeAction,
    onDismiss: () -> Unit,
    onConfirm: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(current) }

    EnclyDialog(
        title = stringResource(
            if (side ==
                SwipeSide.RIGHT
            ) {
                Res.string.settings_swipe_right
            } else {
                Res.string.settings_swipe_left
            },
        ),
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirm = DialogAction(stringResource(Res.string.done), { onConfirm(selected) }),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            SwipeAction.entries.forEach { action ->
                EnclyRadioRow(
                    title = stringResource(action.choiceLabel()),
                    selected = action == selected,
                    onClick = { selected = action },
                )
            }
        }
    }
}
