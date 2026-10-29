package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.theme.LocalEditorTheme

/**
 * A 1 dp rule in a 24 dp tap target that opens the block's sheet. Taller than the rule is only so a
 * finger can find it; much taller made it easy to hit by accident when placing the cursor nearby.
 */
@Composable
internal fun SeparatorBlock(onClick: () -> Unit, modifier: Modifier = Modifier, isLocked: Boolean = false) {
    val strings = LocalEditorStrings.current
    val spacing = LocalEditorTheme.current.spacing
    val label = strings.text(EditorString.BLOCK_SEPARATOR)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.l)
            // Its sheet only moves or deletes it: nothing to offer in a locked editor.
            .clickable(
                enabled = !isLocked,
                role = Role.Button,
                onClickLabel = strings.text(EditorString.MORE_OPTIONS),
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        HorizontalDivider(thickness = spacing.hairline, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
