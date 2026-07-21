package com.pasich.encly.utils

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat


@Composable
fun NotificationPermissionHandler(
    requestPermissionTrigger: Int = 0, // changes on a repeated request
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
        if (!isGranted) {
            permissionDenied = true
        }
    }

    // Check the permission on first launch
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            onPermissionResult(hasPermission)
        } else {
            onPermissionResult(true)
        }
    }

    // Handle the manual permission request
    LaunchedEffect(requestPermissionTrigger) {
        if (requestPermissionTrigger > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    onPermissionResult(true)
                }

                (context as? Activity)?.shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == true -> {
                    showPermissionDialog = true
                }

                else -> {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                onPermissionResult(false)
            },
            title = {
                Text("Дозвіл на сповіщення")
            },
            text = {
                Column {
                    Text(
                        "Для роботи нагадувань потрібен дозвіл на показ сповіщень.",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "Це дозволить отримувати нагадування про важливі завдання вчасно.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Надати дозвіл")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        onPermissionResult(false)
                    }
                ) {
                    Text("Пізніше")
                }
            }
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = {
                permissionDenied = false
            },
            title = {
                Text("Сповіщення відключені")
            },
            text = {
                Text("Нагадування про завдання не працюватимуть без дозволу на сповіщення. Ви можете увімкнути їх у налаштуваннях додатка.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        // Open the app settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Налаштування")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                    }
                ) {
                    Text("Пізніше")
                }
            }
        )
    }
}
