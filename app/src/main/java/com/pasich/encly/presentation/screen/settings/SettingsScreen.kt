package com.pasich.encly.presentation.screen.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.pasich.encly.MainActivity
import com.pasich.encly.R
import com.pasich.encly.presentation.components.settings.SettingsBuilder
import com.pasich.encly.presentation.components.settings.SettingsCategoryRenderer
import com.pasich.encly.presentation.components.settings.SettingsNavigation
import com.pasich.encly.presentation.dialogs.LanguageDialog
import com.pasich.encly.presentation.dialogs.ThemeColorDialog
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.SettingsEvent
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.utils.DeviceCapabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController?,
    modifier: Modifier = Modifier,
    settingViewState: SettingsViewModel = hiltViewModel(),
    securityDisable: Boolean = false,
) {
    val dialogVisibly by settingViewState.dialogVisibly.collectAsState()
    val themeSettings = settingViewState.themeSettingsFlow.collectAsState()
    val showTasks by settingViewState.showTasksFlow.collectAsState()
    val simpleEdit by settingViewState.simpleEditFlow.collectAsState()
    val themeType = themeSettings.value.type

    val context = LocalContext.current
    val languageDialogVisible by settingViewState.languageDialogVisible.collectAsState()

    // Build settings categories using the new builder
    val deviceCapabilities = remember { DeviceCapabilities(context) }
    val settingsBuilder = remember { SettingsBuilder(deviceCapabilities) }
    val settingsCategories = settingsBuilder.buildSettingsCategories(
        viewModel = settingViewState,
        themeSettings = themeSettings.value,
        showTasks = showTasks,
        simpleEdit = simpleEdit,
        navigation = SettingsNavigation(
            onSecurity = { navController?.navigate(NavRoutes.SecuritySettingsRoute.name) },
            onBackup = { navController?.navigate(NavRoutes.BackupRoute.name) },
        ),
    )

    Scaffold(modifier = modifier, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.main_drawer_settings)) },
            navigationIcon = {
                IconButton(onClick = {
                    if (navController != null) {
                        navController.popBackStack()
                    } else {
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                }) {
                    Icon(
                        if (navController != null) Icons.AutoMirrored.Filled.ArrowBack else Lucide.Menu,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
    }) { padding ->
        if (dialogVisibly) {
            ThemeColorDialog(
                themeType = themeType,
                onConfirm = {
                    settingViewState.onEvent(SettingsEvent.UpdateThemeType(it))
                    settingViewState.setDialogVisibility(false)
                },
                onDismiss = { settingViewState.setDialogVisibility(false) },
            )
        }
        if (languageDialogVisible) {
            LanguageDialog(
                current = settingViewState.currentLanguage(),
                onDismiss = { settingViewState.setLanguageDialogVisibility(false) },
                onConfirm = settingViewState::selectLanguage,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(settingsCategories) { category ->
                val securityCategory = category.titleRes == R.string.settings_privacy ||
                    category.titleRes == R.string.settings_backup
                if (securityCategory && securityDisable) {
                    return@items
                }
                SettingsCategoryRenderer(
                    category = category,
                    modifier = Modifier,
                )
            }
        }
    }
}
