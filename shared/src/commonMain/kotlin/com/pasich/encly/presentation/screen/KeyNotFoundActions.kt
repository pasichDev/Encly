package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.key_not_found_last_resort
import com.pasich.encly.core.resources.key_not_found_message
import com.pasich.encly.core.resources.key_not_found_title
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.loss_hold_to_wipe
import com.pasich.encly.core.resources.vault_reset_restore_hint
import com.pasich.encly.core.resources.vault_unreadable_retry
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyHoldToConfirmButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.authCardApplies
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/** What the missing-key screen lets the user do; each leaves the screen except [onWipe], which erases the vault. */
internal class KeyNotFoundActions(
    val onTryAgain: () -> Unit,
    /** Null where the vault has no recovery phrase. */
    val onRecover: (() -> Unit)?,
    val onWipe: () -> Unit,
)

/**
 * The keyring answered but holds no key for this vault. What is known is said (the vault is intact; another keyring,
 * a reset keyring or a moved folder explain it), and the way out is offered in order: try again, the recovery phrase
 * when there is one, and only last, behind the hold-to-confirm, the wipe. The wipe is never the only action.
 */
@Composable
internal fun KeyNotFoundContent(actions: KeyNotFoundActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    val currentWipe by rememberUpdatedState(actions.onWipe)
    // In the desktop card the page is as high as its content, with a short top instead of the phone's.
    val card = authCardApplies()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier = (if (card) modifier.fillMaxWidth() else modifier.fillMaxSize())
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(
                start = spacing.gutter,
                end = spacing.gutter,
                top = if (card) spacing.xl else spacing.lockTop,
                bottom = spacing.l,
            ),
    ) {
        EnclyIconTile(icon = EnclyIcons.Key)
        Text(
            text = stringResource(Res.string.key_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs),
        )
        Text(
            text = stringResource(Res.string.key_not_found_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        EnclyButton(text = stringResource(Res.string.vault_unreadable_retry), onClick = actions.onTryAgain)
        actions.onRecover?.let { recover ->
            EnclyTextButton(text = stringResource(Res.string.lock_use_recovery_phrase), onClick = recover)
        }
        Text(
            text = stringResource(Res.string.key_not_found_last_resort),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xl),
        )
        EnclyCallout(text = stringResource(Res.string.vault_reset_restore_hint), icon = EnclyIcons.Restore)
        EnclyHoldToConfirmButton(
            text = stringResource(Res.string.loss_hold_to_wipe),
            onConfirm = { currentWipe() },
        )
    }
}
