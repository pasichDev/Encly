package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.pasich.encly.core.LINK_FEEDBACK
import com.pasich.encly.core.LINK_PRIVACY_POLICY
import com.pasich.encly.core.LINK_REPOSITORY
import com.pasich.encly.core.MAIL_DEVELOPMENT
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about
import com.pasich.encly.core.resources.about_app_description
import com.pasich.encly.core.resources.about_app_title
import com.pasich.encly.core.resources.about_data_protection
import com.pasich.encly.core.resources.about_encryption_note
import com.pasich.encly.core.resources.about_feedback
import com.pasich.encly.core.resources.about_feedback_desc
import com.pasich.encly.core.resources.about_no_browser
import com.pasich.encly.core.resources.about_no_mail_client
import com.pasich.encly.core.resources.about_privacy_policy
import com.pasich.encly.core.resources.about_privacy_policy_desc
import com.pasich.encly.core.resources.about_rate_app
import com.pasich.encly.core.resources.about_rate_app_desc
import com.pasich.encly.core.resources.about_source_code
import com.pasich.encly.core.resources.about_source_code_desc
import com.pasich.encly.core.resources.about_support_dev_desc
import com.pasich.encly.core.resources.about_support_title
import com.pasich.encly.core.resources.about_write_developer
import com.pasich.encly.core.resources.about_write_developer_desc
import com.pasich.encly.core.resources.licenses_row_desc
import com.pasich.encly.core.resources.licenses_title
import com.pasich.encly.core.resources.main_drawer_support
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.platform.LocalExternalLinks
import com.pasich.encly.platform.LocalToaster
import com.pasich.encly.platform.ToastDuration
import com.pasich.encly.platform.Toaster
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNavigationRow
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.EnclyWordmark
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val links = LocalExternalLinks.current
    val toaster = LocalToaster.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(Res.string.about), onBack = { navController.popBackStack() }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
        ) {
            HeroSection()
            EnclyCallout(
                title = stringResource(Res.string.about_data_protection),
                text = stringResource(Res.string.about_encryption_note),
                icon = EnclyIcons.Lock,
            )
            DeveloperActionsSection(links, toaster, onSupport = { navController.navigate(NavRoutes.SupportRoute.name) })
            EnclyGroup {
                EnclyNavigationRow(
                    title = stringResource(Res.string.licenses_title),
                    supporting = stringResource(Res.string.licenses_row_desc),
                    icon = EnclyIcons.File,
                    onClick = { navController.navigate(NavRoutes.LicensesRoute.name) },
                    modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
                )
            }
        }
    }
}

@Composable
private fun HeroSection() {
    val versionName = LocalAppInfo.current.versionName

    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EnclyWordmark(appName = stringResource(Res.string.about_app_title), modifier = Modifier.weight(1f))
            Text(
                text = "v$versionName",
                style = EnclyTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.about_app_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeveloperActionsSection(links: ExternalLinks, toaster: Toaster, onSupport: () -> Unit) {
    val info = LocalAppInfo.current
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(Res.string.about_support_title))
        EnclyGroup {
            val rowModifier = Modifier.padding(horizontal = EnclyTheme.spacing.s)
            EnclyNavigationRow(
                title = stringResource(Res.string.about_privacy_policy),
                supporting = stringResource(Res.string.about_privacy_policy_desc),
                icon = EnclyIcons.Privacy,
                onClick = { openExternalLink(links, toaster, LINK_PRIVACY_POLICY) },
                modifier = rowModifier,
            )
            // Rate App: store builds only. The fdroid flavor carries no Google Play link.
            if (info.storeRatingEnabled) {
                EnclyGroupDivider()
                EnclyNavigationRow(
                    title = stringResource(Res.string.about_rate_app),
                    supporting = stringResource(Res.string.about_rate_app_desc),
                    icon = EnclyIcons.Star,
                    onClick = { openStorePage(links, toaster, info) },
                    modifier = rowModifier,
                )
            }
            // Support the developer: fdroid only, same page as the drawer entry.
            if (info.donationsEnabled) {
                EnclyGroupDivider()
                EnclyNavigationRow(
                    title = stringResource(Res.string.main_drawer_support),
                    supporting = stringResource(Res.string.about_support_dev_desc),
                    icon = EnclyIcons.Coffee,
                    onClick = onSupport,
                    modifier = rowModifier,
                )
            }
            EnclyGroupDivider()
            // The code is public: a plain link in every flavor, not a donation or store link.
            EnclyNavigationRow(
                title = stringResource(Res.string.about_source_code),
                supporting = stringResource(Res.string.about_source_code_desc),
                icon = EnclyIcons.External,
                onClick = { openExternalLink(links, toaster, LINK_REPOSITORY) },
                modifier = rowModifier,
            )
            EnclyGroupDivider()
            // Feedback: public GitHub issue tracker, no third-party form service.
            EnclyNavigationRow(
                title = stringResource(Res.string.about_feedback),
                supporting = stringResource(Res.string.about_feedback_desc),
                icon = EnclyIcons.Send,
                onClick = { openExternalLink(links, toaster, LINK_FEEDBACK) },
                modifier = rowModifier,
            )
            EnclyGroupDivider()
            EnclyNavigationRow(
                title = stringResource(Res.string.about_write_developer),
                supporting = stringResource(Res.string.about_write_developer_desc),
                icon = EnclyIcons.Mail,
                onClick = { writeToDeveloper(links, toaster) },
                modifier = rowModifier,
            )
        }
    }
}

private fun openStorePage(links: ExternalLinks, toaster: Toaster, info: AppInfo) {
    // The store app's own link first; a device without one gets the store's web page in the browser.
    val opened = info.storePageLinks.any { links.openUrl(it) }
    if (!opened) {
        toaster.show(Res.string.about_no_browser, ToastDuration.LONG, info.storePageLinks.lastOrNull().orEmpty())
    }
}

private fun writeToDeveloper(links: ExternalLinks, toaster: Toaster) {
    if (!links.writeMail(MAIL_DEVELOPMENT)) toaster.show(Res.string.about_no_mail_client, ToastDuration.SHORT)
}

/** Opens [url] in the user's browser; Encly itself never performs the request. */
internal fun openExternalLink(links: ExternalLinks, toaster: Toaster, url: String) {
    if (!links.openUrl(url)) toaster.show(Res.string.about_no_browser, ToastDuration.LONG, url)
}
