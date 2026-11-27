package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about_faq_title
import com.pasich.encly.core.resources.faq_background_a
import com.pasich.encly.core.resources.faq_background_q
import com.pasich.encly.core.resources.faq_backup_a
import com.pasich.encly.core.resources.faq_backup_q
import com.pasich.encly.core.resources.faq_biometrics_a
import com.pasich.encly.core.resources.faq_biometrics_q
import com.pasich.encly.core.resources.faq_change_pin_a
import com.pasich.encly.core.resources.faq_change_pin_q
import com.pasich.encly.core.resources.faq_db_key_a
import com.pasich.encly.core.resources.faq_db_key_q
import com.pasich.encly.core.resources.faq_forgot_pin_a
import com.pasich.encly.core.resources.faq_forgot_pin_q
import com.pasich.encly.core.resources.faq_language_a
import com.pasich.encly.core.resources.faq_language_q
import com.pasich.encly.core.resources.faq_lock_note_a
import com.pasich.encly.core.resources.faq_lock_note_q
import com.pasich.encly.core.resources.faq_markdown_a
import com.pasich.encly.core.resources.faq_markdown_q
import com.pasich.encly.core.resources.faq_new_note_a
import com.pasich.encly.core.resources.faq_new_note_q
import com.pasich.encly.core.resources.faq_note_actions_a
import com.pasich.encly.core.resources.faq_note_actions_q
import com.pasich.encly.core.resources.faq_pin_length_a
import com.pasich.encly.core.resources.faq_pin_length_q
import com.pasich.encly.core.resources.faq_recovery_phrase_a
import com.pasich.encly.core.resources.faq_recovery_phrase_q
import com.pasich.encly.core.resources.faq_report_bug_a
import com.pasich.encly.core.resources.faq_report_bug_q
import com.pasich.encly.core.resources.faq_saving_a
import com.pasich.encly.core.resources.faq_saving_q
import com.pasich.encly.core.resources.faq_screen_description
import com.pasich.encly.core.resources.faq_screen_subtitle
import com.pasich.encly.core.resources.faq_screenshots_a
import com.pasich.encly.core.resources.faq_screenshots_q
import com.pasich.encly.core.resources.faq_section_basics
import com.pasich.encly.core.resources.faq_section_security
import com.pasich.encly.core.resources.faq_section_settings
import com.pasich.encly.core.resources.faq_section_troubleshooting
import com.pasich.encly.core.resources.faq_storage_a
import com.pasich.encly.core.resources.faq_storage_q
import com.pasich.encly.core.resources.faq_swipe_a
import com.pasich.encly.core.resources.faq_swipe_q
import com.pasich.encly.core.resources.faq_tags_a
import com.pasich.encly.core.resources.faq_tags_q
import com.pasich.encly.core.resources.faq_tasks_a
import com.pasich.encly.core.resources.faq_tasks_q
import com.pasich.encly.core.resources.faq_theme_a
import com.pasich.encly.core.resources.faq_theme_q
import com.pasich.encly.core.resources.faq_trash_a
import com.pasich.encly.core.resources.faq_trash_q
import com.pasich.encly.core.resources.faq_vault_damaged_a
import com.pasich.encly.core.resources.faq_vault_damaged_q
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.platform.BiometricWording
import com.pasich.encly.platform.HostPlatform
import com.pasich.encly.platform.LocalBiometricWording
import com.pasich.encly.platform.LocalHostPlatform
import com.pasich.encly.platform.LocalScreenshotAllowance
import com.pasich.encly.platform.secretString
import com.pasich.encly.platform.secretVariant
import com.pasich.encly.presentation.designsystem.EnclyExpandableRow
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.navigation.popBack
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One FAQ section: its title and its question/answer pairs. */
private class FaqSection(val title: StringResource, val items: List<Pair<StringResource, StringResource>>)

/** Answers about screenshots and biometrics, shown only where the host has what they talk about. */
private val screenshotItem = Res.string.faq_screenshots_q
private val biometricsItem = Res.string.faq_biometrics_q

private val faqSections = listOf(
    FaqSection(
        Res.string.faq_section_basics,
        listOf(
            Res.string.faq_new_note_q to Res.string.faq_new_note_a,
            Res.string.faq_saving_q to Res.string.faq_saving_a,
            Res.string.faq_note_actions_q to Res.string.faq_note_actions_a,
            Res.string.faq_lock_note_q to Res.string.faq_lock_note_a,
            Res.string.faq_trash_q to Res.string.faq_trash_a,
            Res.string.faq_tags_q to Res.string.faq_tags_a,
            Res.string.faq_tasks_q to Res.string.faq_tasks_a,
        ),
    ),
    FaqSection(
        Res.string.faq_section_security,
        listOf(
            Res.string.faq_storage_q to Res.string.faq_storage_a,
            Res.string.faq_backup_q to Res.string.faq_backup_a,
            Res.string.faq_markdown_q to Res.string.faq_markdown_a,
            Res.string.faq_swipe_q to Res.string.faq_swipe_a,
            Res.string.faq_db_key_q to Res.string.faq_db_key_a,
            Res.string.faq_recovery_phrase_q to Res.string.faq_recovery_phrase_a,
            Res.string.faq_forgot_pin_q to Res.string.faq_forgot_pin_a,
            Res.string.faq_pin_length_q to Res.string.faq_pin_length_a,
            Res.string.faq_screenshots_q to Res.string.faq_screenshots_a,
            Res.string.faq_background_q to Res.string.faq_background_a,
        ),
    ),
    FaqSection(
        Res.string.faq_section_settings,
        listOf(
            Res.string.faq_change_pin_q to Res.string.faq_change_pin_a,
            Res.string.faq_biometrics_q to Res.string.faq_biometrics_a,
            Res.string.faq_theme_q to Res.string.faq_theme_a,
            Res.string.faq_language_q to Res.string.faq_language_a,
        ),
    ),
    FaqSection(
        Res.string.faq_section_troubleshooting,
        listOf(
            Res.string.faq_vault_damaged_q to Res.string.faq_vault_damaged_a,
            Res.string.faq_report_bug_q to Res.string.faq_report_bug_a,
        ),
    ),
)

/**
 * The answers about the PIN read "passphrase" where the unlock secret is one ([secretKind]). This page has no
 * ViewModel to ask, so by default a desktop host gets the passphrase wording and the others the PIN's.
 */
@Composable
fun FaqScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    secretKind: SecretKind = if (LocalHostPlatform.current == HostPlatform.DESKTOP) {
        SecretKind.PASSWORD
    } else {
        SecretKind.PIN
    },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(Res.string.about_faq_title), onBack = { navController.popBack() })
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
                title = stringResource(Res.string.faq_screen_subtitle),
                body = stringResource(Res.string.faq_screen_description),
            )
            faqSections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
                    SectionOverline(stringResource(section.title))
                    EnclyGroup {
                        section.items.filter { (question, _) -> offered(question) }
                            .forEachIndexed { index, (question, answer) ->
                                if (index > 0) EnclyGroupDivider()
                                EnclyExpandableRow(
                                    question = stringResource(secretVariant(question, secretKind)),
                                    answer = secretString(secretKind, answer),
                                )
                            }
                    }
                }
            }
        }
    }
}

/** False for a question about something this host does not have: screenshots to allow, biometrics to turn on. */
@Composable
private fun offered(question: StringResource): Boolean = when (question) {
    screenshotItem -> LocalScreenshotAllowance.current != null
    biometricsItem -> LocalBiometricWording.current != BiometricWording.None
    else -> true
}
