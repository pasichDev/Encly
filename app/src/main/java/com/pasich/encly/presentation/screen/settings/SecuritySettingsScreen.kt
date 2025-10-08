package com.pasich.encly.presentation.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.custombox.SettingBox
import com.pasich.encly.presentation.components.settings.AuthMethodSelector
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavHostController,
    securityViewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Безпека") }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                }
            })
        }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Lucide.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Шифрування активне",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (!securityState.isUserCreatedSeedKey) "Ваші дані повністю зашифровані. Доступ можливий лише за допомогою вашої сід-фрази." else "Ваші дані захищені. Ключ створено автоматично й зберігається лише на вашому пристрої.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // Авторизація
            item {
                Text(
                    text = "Авторизація",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                AuthMethodSelector(
                    selected = securityState.authType, onSelected = {
                        navController.navigate(NavRoutes.PinCodeConfig.name)
                    }, isDisableSeedPhase = securityState.isUserCreatedSeedKey
                )
            }
            item { Spacer(Modifier.height(15.dp)) }
            if (securityState.authType != AuthType.NONE) {
                item {
                    SettingBox(
                        title = "Біометрична авторизація",
                        subTitle = if (securityState.isBiometricAvailable) "Відбиток пальця"
                        else "Недоступно на цьому пристрої",
                        roundPosition = RoundPosition.Full,
                        endWidget = {
                            Switch(
                                checked = securityState.biometricEnable,
                                enabled = securityState.isBiometricAvailable,
                                onCheckedChange = {
                                    /* authViewModel.toggleBiometric(
                                         context,
                                         activity,
                                         it,
                                         onError = { errorMessage ->
                                             showError(errorMessage)
                                         }

                                     )  */
                                })
                        })
                }
            }



            item { Spacer(Modifier.height(20.dp)) }

            // Інформація про безпеку
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️ Інформація про безпеку",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Сід-фраза доступна тільки з біометричною автентифікацією\n" + "• Всі дані шифруються локально з використанням AES-256\n" + "• Без сід-фрази неможливо відновити дані",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(securityState.error) {
        securityState.error?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            securityViewModel.clearError()
        }
    }
}

