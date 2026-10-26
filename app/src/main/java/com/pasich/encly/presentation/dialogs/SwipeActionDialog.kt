package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.presentation.components.swipe.choiceLabel
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyRadioRow

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
                R.string.settings_swipe_right
            } else {
                R.string.settings_swipe_left
            },
        ),
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirm = DialogAction(stringResource(R.string.done), { onConfirm(selected) }),
        dismiss = DialogAction(stringResource(R.string.cancel), onDismiss),
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
