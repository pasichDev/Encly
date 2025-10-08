package com.pasich.encly.dynamicBlocks.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeparatorBlock(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier =
            modifier
                .padding(vertical = 20.dp)
                .clickable { onClick() },
    )
}
