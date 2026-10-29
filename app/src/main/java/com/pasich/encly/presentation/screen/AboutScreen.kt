package com.pasich.encly.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.pasich.encly.BuildConfig
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
import com.pasich.encly.presentation.components.showToast
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
    val context = LocalContext.current
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
            DeveloperActionsSection(context, onSupport = { navController.navigate(NavRoutes.SupportRoute.name) })
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
    val versionName = BuildConfig.VERSION_NAME

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
private fun DeveloperActionsSection(context: Context, onSupport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(Res.string.about_support_title))
        EnclyGroup {
            val rowModifier = Modifier.padding(horizontal = EnclyTheme.spacing.s)
            EnclyNavigationRow(
                title = stringResource(Res.string.about_privacy_policy),
                supporting = stringResource(Res.string.about_privacy_policy_desc),
                icon = EnclyIcons.Privacy,
                onClick = { openExternalLink(context, LINK_PRIVACY_POLICY) },
                modifier = rowModifier,
            )
            // Rate App: store builds only. The fdroid flavor carries no Google Play link.
            if (BuildConfig.STORE_RATING_ENABLED) {
                EnclyGroupDivider()
                EnclyNavigationRow(
                    title = stringResource(Res.string.about_rate_app),
                    supporting = stringResource(Res.string.about_rate_app_desc),
                    icon = EnclyIcons.Star,
                    onClick = { openStorePage(context) },
                    modifier = rowModifier,
                )
            }
            // Support the developer: fdroid only, same page as the drawer entry.
            if (BuildConfig.DONATIONS_ENABLED) {
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
                onClick = { openExternalLink(context, LINK_REPOSITORY) },
                modifier = rowModifier,
            )
            EnclyGroupDivider()
            // Feedback: public GitHub issue tracker, no third-party form service.
            EnclyNavigationRow(
                title = stringResource(Res.string.about_feedback),
                supporting = stringResource(Res.string.about_feedback_desc),
                icon = EnclyIcons.Send,
                onClick = { openExternalLink(context, LINK_FEEDBACK) },
                modifier = rowModifier,
            )
            EnclyGroupDivider()
            EnclyNavigationRow(
                title = stringResource(Res.string.about_write_developer),
                supporting = stringResource(Res.string.about_write_developer_desc),
                icon = EnclyIcons.Mail,
                onClick = { writeToDeveloper(context) },
                modifier = rowModifier,
            )
        }
    }
}

private fun openStorePage(context: Context) {
    val appPackageName = context.packageName
    val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$appPackageName".toUri())
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        openExternalLink(context, "https://play.google.com/store/apps/details?id=$appPackageName")
    }
}

private fun writeToDeveloper(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$MAIL_DEVELOPMENT".toUri()
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showToast(context, Res.string.about_no_mail_client, Toast.LENGTH_SHORT)
    }
}

/** Opens [url] in the user's browser; Encly itself never performs the request. */
internal fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        showToast(context, Res.string.about_no_browser, Toast.LENGTH_LONG, url)
    }
}
