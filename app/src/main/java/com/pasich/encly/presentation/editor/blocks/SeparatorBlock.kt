package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

/** A 1 dp rule in a full-height (48 dp) tap target that opens the block's sheet. */
@Composable
fun SeparatorBlock(onClick: () -> Unit, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    val label = stringResource(R.string.block_separator)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.minTouchTarget)
            // Its sheet only moves or deletes it: nothing to offer in a locked editor.
            .clickable(
                enabled = !isLocked,
                role = Role.Button,
                onClickLabel = stringResource(R.string.more_options),
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        HorizontalDivider(thickness = EnclyTheme.spacing.hairline, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
