package com.pasich.encly.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.donation_description
import com.pasich.encly.core.resources.donation_title
import com.pasich.encly.core.resources.support_browser_note
import com.pasich.encly.core.resources.support_fact_free_desc
import com.pasich.encly.core.resources.support_fact_free_title
import com.pasich.encly.core.resources.support_fact_private_desc
import com.pasich.encly.core.resources.support_fact_private_title
import com.pasich.encly.core.resources.support_fact_uses_desc
import com.pasich.encly.core.resources.support_fact_uses_title
import com.pasich.encly.core.resources.support_method_kofi_hint
import com.pasich.encly.core.resources.support_title
import com.pasich.encly.platform.LocalExternalLinks
import com.pasich.encly.platform.LocalToaster
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.FactRow
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

private const val KOFI_URL = "https://ko-fi.com/pasichdev"

/**
 * Support the developer (F-Droid build only): why donations matter, then one Ko-fi card that
 * opens the page in the browser; the app never handles payments.
 */
@Composable
fun SupportScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val links = LocalExternalLinks.current
    val toaster = LocalToaster.current
    val spacing = EnclyTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(Res.string.support_title), onBack = { navController.popBackStack() })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = spacing.gutter, vertical = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            EnclyIconTile(icon = EnclyIcons.Heart)
            StepHeading(
                title = stringResource(Res.string.donation_title),
                body = stringResource(Res.string.donation_description),
                large = true,
            )
            SupportFacts()
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                KofiCard(onClick = { openExternalLink(links, toaster, KOFI_URL) })
                Text(
                    text = stringResource(Res.string.support_browser_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Why support matters, as three Welcome-style facts. */
@Composable
private fun SupportFacts() {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.factGap)) {
        FactRow(
            icon = EnclyIcons.Star,
            title = stringResource(Res.string.support_fact_free_title),
            description = stringResource(Res.string.support_fact_free_desc),
        )
        FactRow(
            icon = EnclyIcons.Privacy,
            title = stringResource(Res.string.support_fact_private_title),
            description = stringResource(Res.string.support_fact_private_desc),
        )
        FactRow(
            icon = EnclyIcons.Heart,
            title = stringResource(Res.string.support_fact_uses_title),
            description = stringResource(Res.string.support_fact_uses_desc),
        )
    }
}

/** The Ko-fi card, filled with `primaryContainer` so it reads as the screen's one action. */
@Composable
private fun KofiCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = EnclyTheme.spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            modifier = Modifier.padding(spacing.m),
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = colors.primary) {
                Icon(
                    imageVector = EnclyIcons.Heart,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier
                        .padding(spacing.s)
                        .size(spacing.iconMedium),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.textGap)) {
                Text(text = "Ko-fi", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(Res.string.support_method_kofi_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = EnclyIcons.External,
                contentDescription = null,
                modifier = Modifier.size(spacing.iconSmall),
            )
        }
    }
}
