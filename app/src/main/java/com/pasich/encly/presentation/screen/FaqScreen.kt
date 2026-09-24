package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyExpandableRow
import com.pasich.encly.presentation.designsystem.EnclyGroup
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.ui.theme.EnclyTheme

/** One FAQ section: its title and its question/answer pairs. */
private class FaqSection(@param:StringRes val title: Int, val items: List<Pair<Int, Int>>)

private val faqSections = listOf(
    FaqSection(
        R.string.faq_section_basics,
        listOf(
            R.string.faq_new_note_q to R.string.faq_new_note_a,
            R.string.faq_saving_q to R.string.faq_saving_a,
            R.string.faq_tags_q to R.string.faq_tags_a,
            R.string.faq_tasks_q to R.string.faq_tasks_a,
        ),
    ),
    FaqSection(
        R.string.faq_section_security,
        listOf(
            R.string.faq_storage_q to R.string.faq_storage_a,
            R.string.faq_db_key_q to R.string.faq_db_key_a,
            R.string.faq_recovery_phrase_q to R.string.faq_recovery_phrase_a,
            R.string.faq_forgot_pin_q to R.string.faq_forgot_pin_a,
            R.string.faq_screenshots_q to R.string.faq_screenshots_a,
            R.string.faq_background_q to R.string.faq_background_a,
        ),
    ),
    FaqSection(
        R.string.faq_section_settings,
        listOf(
            R.string.faq_change_pin_q to R.string.faq_change_pin_a,
            R.string.faq_biometrics_q to R.string.faq_biometrics_a,
            R.string.faq_theme_q to R.string.faq_theme_a,
            R.string.faq_language_q to R.string.faq_language_a,
        ),
    ),
    FaqSection(
        R.string.faq_section_troubleshooting,
        listOf(
            R.string.faq_vault_damaged_q to R.string.faq_vault_damaged_a,
            R.string.faq_report_bug_q to R.string.faq_report_bug_a,
        ),
    ),
)

@Composable
fun FaqScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = stringResource(R.string.about_faq_title), onBack = { navController.popBackStack() })
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
                title = stringResource(R.string.faq_screen_subtitle),
                body = stringResource(R.string.faq_screen_description),
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
