package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.vault_unreadable_message
import com.pasich.encly.core.resources.vault_unreadable_retry
import com.pasich.encly.core.resources.vault_unreadable_still
import com.pasich.encly.core.resources.vault_unreadable_title
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The vault's state file could not be read at start (another program holds it, or the device is still locked).
 * Nothing is damaged and nothing is offered to wipe: the only action is to try again.
 */
@Composable
fun StorageUnreadableScreen(onRetry: () -> Unit, stillUnreadable: Boolean, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.lockTop, bottom = spacing.l),
        ) {
            EnclyIconTile(icon = EnclyIcons.Alert)
            Text(
                text = stringResource(Res.string.vault_unreadable_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Text(
                text = stringResource(Res.string.vault_unreadable_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (stillUnreadable) {
                Text(
                    text = stringResource(Res.string.vault_unreadable_still),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            EnclyButton(
                text = stringResource(Res.string.vault_unreadable_retry),
                onClick = onRetry,
                modifier = Modifier.padding(top = spacing.xl),
            )
        }
    }
}
