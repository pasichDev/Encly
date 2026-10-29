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
import com.pasich.encly.presentation.designsystem.EnclyExpandableRow
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One FAQ section: its title and its question/answer pairs. */
private class FaqSection(val title: StringResource, val items: List<Pair<StringResource, StringResource>>)

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

@Composable
fun FaqScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(Res.string.about_faq_title), onBack = { navController.popBackStack() })
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
                        section.items.forEachIndexed { index, (question, answer) ->
                            if (index > 0) EnclyGroupDivider()
                            EnclyExpandableRow(question = stringResource(question), answer = stringResource(answer))
                        }
                    }
                }
            }
        }
    }
}
