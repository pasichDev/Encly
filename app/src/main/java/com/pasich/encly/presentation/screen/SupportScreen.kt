package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.FactRow
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme

private const val KOFI_URL = "https://ko-fi.com/pasichdev"

/**
 * Support the developer (F-Droid build only): laid out like the Welcome step (tile, heading,
 * three facts), with the one action pinned under the scrolling part.
 */
@Composable
fun SupportScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val spacing = EnclyTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(R.string.support_title), onBack = { navController.popBackStack() })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.gutter, vertical = spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.section),
            ) {
                EnclyIconTile(icon = EnclyIcons.Heart)
                StepHeading(
                    title = stringResource(R.string.donation_title),
                    body = stringResource(R.string.donation_description),
                    large = true,
                )
                SupportFacts()
            }
            SupportAction(onSupport = { openExternalLink(context, KOFI_URL) })
        }
    }
}

/** Why support matters, as three Welcome-style facts. */
@Composable
private fun SupportFacts() {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.factGap)) {
        FactRow(
            icon = EnclyIcons.Star,
            title = stringResource(R.string.support_fact_free_title),
            description = stringResource(R.string.support_fact_free_desc),
        )
        FactRow(
            icon = EnclyIcons.Privacy,
            title = stringResource(R.string.support_fact_private_title),
            description = stringResource(R.string.support_fact_private_desc),
        )
        FactRow(
            icon = EnclyIcons.Heart,
            title = stringResource(R.string.support_fact_uses_title),
            description = stringResource(R.string.support_fact_uses_desc),
        )
    }
}

/** The Ko-fi button and the note under it, pinned above the navigation bar. */
@Composable
private fun SupportAction(onSupport: () -> Unit) {
    val spacing = EnclyTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.s, bottom = spacing.l),
    ) {
        EnclyButton(
            text = stringResource(R.string.support_kofi_button),
            onClick = onSupport,
            trailingIcon = EnclyIcons.External,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.support_kofi_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
