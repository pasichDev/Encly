package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.pasich.encly.presentation.designsystem.EnclyHoldToConfirmButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.ui.theme.EnclyTheme

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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.lockTop, bottom = spacing.l),
        ) {
            EnclyIconTile(icon = Lucide.TriangleAlert)
            Text(
                text = stringResource(reason.title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Text(
                text = stringResource(reason.message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            EnclyHoldToConfirmButton(
                text = stringResource(reason.action),
                onConfirm = { currentOnRecoveryConfirm() },
                modifier = Modifier.padding(top = spacing.xxl),
            )
        }
    }
}
