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
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.pasich.encly.BuildConfig
import com.pasich.encly.R
import com.pasich.encly.core.LINK_FEEDBACK
import com.pasich.encly.core.LINK_PRIVACY_POLICY
import com.pasich.encly.core.MAIL_DEVELOPMENT
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

@Composable
fun AboutScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(R.string.about), onBack = { navController.popBackStack() }) },
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
                title = stringResource(R.string.about_data_protection),
                text = stringResource(R.string.about_encryption_note),
                icon = EnclyIcons.Lock,
            )
            DeveloperActionsSection(context)
            EnclyGroup {
                EnclyNavigationRow(
                    title = stringResource(R.string.licenses_title),
                    supporting = stringResource(R.string.licenses_row_desc),
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
            EnclyWordmark(appName = stringResource(R.string.about_app_title), modifier = Modifier.weight(1f))
            Text(
                text = "v$versionName",
                style = EnclyTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.about_app_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeveloperActionsSection(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
        SectionOverline(stringResource(R.string.about_support_title))
        EnclyGroup {
            val rowModifier = Modifier.padding(horizontal = EnclyTheme.spacing.s)
            EnclyNavigationRow(
                title = stringResource(R.string.about_privacy_policy),
                supporting = stringResource(R.string.about_privacy_policy_desc),
                icon = EnclyIcons.Privacy,
                onClick = { openExternalLink(context, LINK_PRIVACY_POLICY) },
                modifier = rowModifier,
            )
            // Rate App: store builds only. The fdroid flavor carries no Google Play link.
            if (BuildConfig.STORE_RATING_ENABLED) {
                EnclyGroupDivider()
                EnclyNavigationRow(
                    title = stringResource(R.string.about_rate_app),
                    supporting = stringResource(R.string.about_rate_app_desc),
                    icon = EnclyIcons.Star,
                    onClick = { openStorePage(context) },
                    modifier = rowModifier,
                )
            }
            EnclyGroupDivider()
            // Feedback: public GitHub issue tracker, no third-party form service.
            EnclyNavigationRow(
                title = stringResource(R.string.about_feedback),
                supporting = stringResource(R.string.about_feedback_desc),
                icon = EnclyIcons.Send,
                onClick = { openExternalLink(context, LINK_FEEDBACK) },
                modifier = rowModifier,
            )
            EnclyGroupDivider()
            EnclyNavigationRow(
                title = stringResource(R.string.about_write_developer),
                supporting = stringResource(R.string.about_write_developer_desc),
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
        Toast.makeText(context, context.getString(R.string.about_no_mail_client), Toast.LENGTH_SHORT).show()
    }
}

/** Opens [url] in the user's browser; Encly itself never performs the request. */
internal fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.about_no_browser, url),
            Toast.LENGTH_LONG,
        ).show()
    }
}
