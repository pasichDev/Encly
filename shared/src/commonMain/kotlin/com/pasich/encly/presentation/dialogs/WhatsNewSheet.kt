package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.whats_new_dismiss
import com.pasich.encly.core.resources.whats_new_title
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.whatsnew.ReleaseNotes
import org.jetbrains.compose.resources.stringResource

/**
 * The notes of a release ("What's new"): the version as the heading, the changes under it, and a button that
 * closes the sheet. Shown once after an update, and again from Settings → About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(notes: ReleaseNotes, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AdaptiveSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = stringResource(Res.string.whats_new_title),
        scrollable = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            Text(
                text = notes.heading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (notes.body.isNotEmpty()) {
                Text(
                    text = notes.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EnclyButton(
                text = stringResource(Res.string.whats_new_dismiss),
                onClick = onDismiss,
                modifier = Modifier.padding(top = EnclyTheme.spacing.s),
            )
        }
    }
}
