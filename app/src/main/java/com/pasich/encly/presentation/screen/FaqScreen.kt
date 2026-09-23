package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.composables.icons.lucide.BadgeHelp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.HandHelping
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.presentation.components.HeroCard
import com.pasich.encly.presentation.components.HeroIcon
import com.pasich.encly.presentation.components.TitleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_faq_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Section
            HeroCard(
                icon = HeroIcon.Vector(Lucide.BadgeHelp),
                title = stringResource(R.string.about_faq_title),
                subtitle = stringResource(R.string.faq_screen_subtitle),
                description = stringResource(R.string.faq_screen_description),
            )

            // FAQ Content
            FaqContentSection()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FaqContentSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FaqSection(R.string.faq_section_basics)
        FaqItem(R.string.faq_new_note_q, R.string.faq_new_note_a)
        FaqItem(R.string.faq_saving_q, R.string.faq_saving_a)
        FaqItem(R.string.faq_tags_q, R.string.faq_tags_a)
        FaqItem(R.string.faq_tasks_q, R.string.faq_tasks_a)

        FaqSection(R.string.faq_section_security)
        FaqItem(R.string.faq_storage_q, R.string.faq_storage_a)
        FaqItem(R.string.faq_db_key_q, R.string.faq_db_key_a)
        FaqItem(R.string.faq_recovery_phrase_q, R.string.faq_recovery_phrase_a)
        FaqItem(R.string.faq_forgot_pin_q, R.string.faq_forgot_pin_a)
        FaqItem(R.string.faq_screenshots_q, R.string.faq_screenshots_a)
        FaqItem(R.string.faq_background_q, R.string.faq_background_a)

        FaqSection(R.string.faq_section_settings)
        FaqItem(R.string.faq_change_pin_q, R.string.faq_change_pin_a)
        FaqItem(R.string.faq_biometrics_q, R.string.faq_biometrics_a)
        FaqItem(R.string.faq_theme_q, R.string.faq_theme_a)
        FaqItem(R.string.faq_language_q, R.string.faq_language_a)

        FaqSection(R.string.faq_section_troubleshooting)
        FaqItem(R.string.faq_vault_damaged_q, R.string.faq_vault_damaged_a)
        FaqItem(R.string.faq_report_bug_q, R.string.faq_report_bug_a)
    }
}

@Composable
private fun FaqSection(@StringRes title: Int) {
    TitleCard(stringResource(title), modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun FaqIcon() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.HandHelping,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqItem(@StringRes question: Int, @StringRes answer: Int, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .padding(horizontal = 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaqIcon()

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(question),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse else R.string.expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            if (expanded) {
                Text(
                    text = stringResource(answer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 12.dp, start = 44.dp),
                )
            }
        }
    }
}
