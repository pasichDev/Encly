package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.components.settings.SettingsBuilder
import com.pasich.encly.presentation.components.settings.SettingsCategory
import com.pasich.encly.presentation.components.settings.SettingsCategoryRenderer
import com.pasich.encly.presentation.components.settings.SettingsNavigation
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.dialogs.LanguageDialog
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme

@Composable
fun SettingsScreen(
    navController: NavHostController?,
    modifier: Modifier = Modifier,
    settingViewState: SettingsViewModel = hiltViewModel(),
    securityDisable: Boolean = false,
) {
    val showTasks by settingViewState.showTasksFlow.collectAsState()
    val simpleEdit by settingViewState.simpleEditFlow.collectAsState()

    val languageDialogVisible by settingViewState.languageDialogVisible.collectAsState()

    val settingsBuilder = remember { SettingsBuilder() }
    val settingsCategories = settingsBuilder.buildSettingsCategories(
        viewModel = settingViewState,
        showTasks = showTasks,
        simpleEdit = simpleEdit,
        navigation = SettingsNavigation(
            onAppearance = { navController?.navigate(NavRoutes.AppearanceRoute.name) },
            onSecurity = { navController?.navigate(NavRoutes.SecuritySettingsRoute.name) },
            onBackup = { navController?.navigate(NavRoutes.BackupRoute.name) },
            onAbout = { navController?.navigate(NavRoutes.AboutRoute.name) },
            onFaq = { navController?.navigate(NavRoutes.FaqRoute.name) },
        ),
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(R.string.main_drawer_settings),
                onBack = { navController?.popBackStack() },
            )
        },
    ) { padding ->
        if (languageDialogVisible) {
            LanguageDialog(
                current = settingViewState.currentLanguage(),
                onDismiss = { settingViewState.setLanguageDialogVisibility(false) },
                onConfirm = settingViewState::selectLanguage,
            )
        }

        SettingsList(
            categories = settingsCategories,
            securityDisable = securityDisable,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SettingsList(categories: List<SettingsCategory>, securityDisable: Boolean, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = EnclyTheme.spacing.gutter,
            end = EnclyTheme.spacing.gutter,
            top = EnclyTheme.spacing.s,
            bottom = EnclyTheme.spacing.l,
        ),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
    ) {
        items(categories) { category ->
            val securityCategory = category.titleRes == R.string.settings_privacy ||
                category.titleRes == R.string.settings_backup
            if (!(securityCategory && securityDisable)) SettingsCategoryRenderer(category = category)
        }
    }
}
