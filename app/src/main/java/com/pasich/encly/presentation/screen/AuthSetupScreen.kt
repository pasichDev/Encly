package com.pasich.encly.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel

private enum class SetupStep { CREATE_PIN, CONFIRM_PIN, BIOMETRIC }

/**
 * Mandatory security setup after onboarding: create a PIN and, when available, enable
 * biometric unlock. On completion the database is unlocked and the app opens.
 */
@Composable
fun AuthSetupScreen(
    navController: NavHostController,
    viewModel: AuthSetupViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? FragmentActivity

    var step by remember { mutableStateOf(SetupStep.CREATE_PIN) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.AuthSetupRoute.name) { inclusive = true }
        }
    }

    fun finish() {
        if (viewModel.finishSetup()) goHome() else error = "Не вдалося відкрити базу даних"
    }

    fun afterPinSet() {
        if (viewModel.biometricAvailable() && activity != null) {
            step = SetupStep.BIOMETRIC
        } else {
            finish()
        }
    }

    // PIN entry handling
    LaunchedEffect(input, step) {
        if (input.length == 4 && step != SetupStep.BIOMETRIC) {
            when (step) {
                SetupStep.CREATE_PIN -> {
                    firstPin = input
                    input = ""
                    error = null
                    step = SetupStep.CONFIRM_PIN
                }

                SetupStep.CONFIRM_PIN -> {
                    if (input == firstPin) {
                        if (viewModel.setPin(input)) {
                            input = ""
                            afterPinSet()
                        } else {
                            error = "Не вдалося зберегти PIN"
                            input = ""
                            firstPin = ""
                            step = SetupStep.CREATE_PIN
                        }
                    } else {
                        error = "PIN-коди не збігаються"
                        input = ""
                        firstPin = ""
                        step = SetupStep.CREATE_PIN
                    }
                }

                SetupStep.BIOMETRIC -> Unit
            }
        }
    }

    // Auto-launch the biometric prompt when entering that step
    LaunchedEffect(step) {
        if (step == SetupStep.BIOMETRIC && activity != null) {
            viewModel.enableBiometric(activity) { ok ->
                if (ok) finish()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val title = when (step) {
                    SetupStep.CREATE_PIN -> "Створіть PIN-код"
                    SetupStep.CONFIRM_PIN -> "Підтвердіть PIN-код"
                    SetupStep.BIOMETRIC -> "Увімкніть біометрію"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val subtitle = when (step) {
                    SetupStep.CREATE_PIN -> "PIN-код обов'язковий для захисту нотаток"
                    SetupStep.CONFIRM_PIN -> "Повторіть PIN-код для підтвердження"
                    SetupStep.BIOMETRIC -> "Підтвердіть біометрію для швидкого входу"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (step == SetupStep.BIOMETRIC) {
                    TextButton(onClick = {
                        if (activity != null) viewModel.enableBiometric(activity) { ok -> if (ok) finish() }
                    }) {
                        Text("Повторити біометрію")
                    }
                    TextButton(onClick = { finish() }) {
                        Text("Продовжити лише з PIN")
                    }
                } else {
                    PinCodeWidget(
                        pinInput = input,
                        onPinChange = { if (input.length < 4) input += it },
                        onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
                    )
                }
            }
        }
    }
}
