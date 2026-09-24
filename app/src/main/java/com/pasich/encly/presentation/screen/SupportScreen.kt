package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme

private const val KOFI_URL = "https://ko-fi.com/pasichdev"
private const val KOFI_NAME = "Ko-fi"

@Composable
fun SupportScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
                StepHeading(
                    title = stringResource(R.string.donation_title),
                    body = stringResource(R.string.donation_description),
                )
                Text(
                    text = stringResource(R.string.donation_subtitle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Support options — external services only (no in-app purchases).
            Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
                SectionOverline(stringResource(R.string.support_options_title))
                Text(
                    text = stringResource(R.string.support_external_services),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnclyGroup {
                    EnclyNavigationRow(
                        title = KOFI_NAME,
                        supporting = stringResource(R.string.support_kofi_desc),
                        icon = Lucide.Coffee,
                        onClick = { uriHandler.openUri(KOFI_URL) },
                        modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
                    )
                }
            }
            EnclyCallout(
                title = stringResource(R.string.support_why_title),
                text = stringResource(R.string.support_why_desc),
                icon = Lucide.Heart,
            )
        }
    }
}
