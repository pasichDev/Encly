package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyListRow
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme

/** A third-party work Encly ships: its name, its licence and where to read both. */
private class OpenSourceWork(val name: String, val license: String, val url: String)

private const val APACHE_2 = "Apache License 2.0"
private const val OFL = "SIL Open Font License 1.1"

private class LicenseSection(@param:StringRes val title: Int, val works: List<OpenSourceWork>)

private val bundledFonts = listOf(
    OpenSourceWork("Playfair Display", OFL, "https://github.com/clauseggers/Playfair"),
    OpenSourceWork("Source Sans 3", OFL, "https://github.com/adobe-fonts/source-sans"),
    OpenSourceWork("IBM Plex Sans", OFL, "https://github.com/IBM/plex"),
    OpenSourceWork("Poppins", OFL, "https://github.com/itfoundry/Poppins"),
)

private val bundledLibraries = listOf(
    OpenSourceWork("AndroidX and Jetpack Compose", APACHE_2, "https://github.com/androidx/androidx"),
    OpenSourceWork("Kotlin and kotlinx", APACHE_2, "https://github.com/JetBrains/kotlin"),
    OpenSourceWork("Dagger Hilt", APACHE_2, "https://github.com/google/dagger"),
    OpenSourceWork("SQLCipher for Android", "BSD 3-Clause", "https://github.com/sqlcipher/sqlcipher-android"),
    OpenSourceWork("kotlin-bip39", "MIT License", "https://github.com/Electric-Coin-Company/kotlin-bip39"),
    OpenSourceWork("Gson", APACHE_2, "https://github.com/google/gson"),
    OpenSourceWork("Reorderable", APACHE_2, "https://github.com/Calvin-LL/Reorderable"),
    OpenSourceWork("Lucide", "ISC License", "https://github.com/lucide-icons/lucide"),
)

/** The fonts and libraries Encly is built with, each linking to its licence. */
@Composable
fun LicensesScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sections = listOf(
        LicenseSection(R.string.licenses_libraries, bundledLibraries),
        LicenseSection(R.string.licenses_fonts, bundledFonts),
    )
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(R.string.licenses_title), onBack = { navController.popBackStack() })
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
            StepHeading(
                title = stringResource(R.string.licenses_heading),
                body = stringResource(R.string.licenses_intro),
            )
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
                    SectionOverline(stringResource(section.title))
                    EnclyGroup {
                        section.works.forEachIndexed { index, work ->
                            if (index > 0) EnclyGroupDivider()
                            EnclyListRow(
                                title = work.name,
                                supporting = work.license,
                                onClick = { openExternalLink(context, work.url) },
                                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s),
                            ) {
                                Icon(
                                    EnclyIcons.External,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
