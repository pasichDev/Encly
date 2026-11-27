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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.vault_reset_restore_hint
import com.pasich.encly.platform.hostString
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyHoldToConfirmButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.authCardApplies
import com.pasich.encly.presentation.designsystem.authSurfaceColor
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The vault can no longer be opened: explains why ([reason]), and wipes everything only after
 * the button is held for four seconds.
 */
@Composable
fun LossRecoveryScreen(
    onRecoveryConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    reason: LossReason = LossReason.DAMAGED,
) {
    val currentOnRecoveryConfirm by rememberUpdatedState(onRecoveryConfirm)
    val spacing = EnclyTheme.spacing
    // In the desktop card the page is as high as its content, with a short top instead of the phone's.
    val card = authCardApplies()
    Surface(
        modifier = if (card) modifier.fillMaxWidth() else modifier.fillMaxSize(),
        color = authSurfaceColor(card),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            modifier = (if (card) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
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
            EnclyIconTile(icon = EnclyIcons.Alert)
            Text(
                text = stringResource(reason.title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Text(
                text = hostString(reason.message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // The one way back after the wipe: a backup file and its 12 words.
            EnclyCallout(
                text = stringResource(Res.string.vault_reset_restore_hint),
                icon = EnclyIcons.Restore,
                modifier = Modifier.padding(top = spacing.s),
            )
            EnclyHoldToConfirmButton(
                text = stringResource(reason.action),
                onConfirm = { currentOnRecoveryConfirm() },
                modifier = Modifier.padding(top = spacing.xl),
            )
        }
    }
}
