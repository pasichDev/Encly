package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeparatorBlock(onClick: () -> Unit, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier =
        modifier
            .padding(vertical = 20.dp)
            // Its sheet only moves or deletes it: nothing to offer in a locked editor.
            .clickable(enabled = !isLocked) { onClick() },
    )
}
