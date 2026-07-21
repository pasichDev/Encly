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
import androidx.compose.runtime.mutableLongStateOf
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
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.viewmodel.LockViewModel
import kotlinx.coroutines.delay

/**
 * App-unlock screen shown when a PIN-based lock is configured. Verifies the PIN
 * (with a progressive lockout) and optionally a biometric prompt, then unlocks the
 * encrypted database and navigates home. It never accesses note data before unlock.
 */
@Composable
fun LockScreen(
    navController: NavHostController,
    viewModel: LockViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? FragmentActivity

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }

    val biometricEnabled = remember {
        viewModel.strategy() == AuthStrategy.PIN_BIOMETRIC &&
                viewModel.biometricEnabled() &&
                viewModel.biometricAvailable()
    }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
        }
    }

    fun completeUnlock() {
        if (viewModel.unlock()) goHome() else error = "Не вдалося відкрити базу даних"
    }

    // Live lockout countdown
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = viewModel.lockoutRemainingMillis()
            lockoutSeconds = (remaining + 999) / 1000
            if (remaining <= 0) break
            delay(1000)
        }
    }

    // Auto-prompt biometric on first entry (if enabled and not locked out)
    LaunchedEffect(Unit) {
        if (biometricEnabled && activity != null && viewModel.lockoutRemainingMillis() <= 0) {
            viewModel.authenticateBiometric(activity) { ok -> if (ok) completeUnlock() }
        }
    }

    // Verify once 4 digits are entered
    LaunchedEffect(input) {
        if (input.length == 4) {
            when {
                viewModel.lockoutRemainingMillis() > 0 -> input = ""
                viewModel.verifyPin(input) -> completeUnlock()
                else -> {
                    error = "Невірний PIN-код"
                    input = ""
                    lockoutSeconds = (viewModel.lockoutRemainingMillis() + 999) / 1000
                }
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

                Text(
                    text = "Розблокуйте нотатки",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (lockoutSeconds > 0) {
                    Text(
                        text = "Забагато спроб. Спробуйте через ${lockoutSeconds}с",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Введіть PIN-код для доступу",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                PinCodeWidget(
                    pinInput = input,
                    onPinChange = { if (input.length < 4 && lockoutSeconds <= 0L) input += it },
                    onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
                )

                if (biometricEnabled && activity != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        if (viewModel.lockoutRemainingMillis() <= 0) {
                            viewModel.authenticateBiometric(activity) { ok -> if (ok) completeUnlock() }
                        }
                    }) {
                        Text("Використати біометрію")
                    }
                }
            }
        }
    }
}
