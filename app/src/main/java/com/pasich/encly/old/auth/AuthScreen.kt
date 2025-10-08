package com.pasich.encly.old.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    // Используем стабильную ссылку на state для предотвращения перерисовок
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Мемоизация значения PIN для предотвращения лишних обновлений
    var pinInput by remember { mutableStateOf("") }

    // Мемоизация для предотвращения повторных обработок успешной аутентификации
    val isAuthenticatedProcessed = remember { mutableStateOf(false) }
    
    // Состояния для анимации успешной аутентификации
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var animationPinInput by remember { mutableStateOf("") }
    val successAlpha = animateFloatAsState(
        targetValue = if (showSuccessAnimation) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "successAlpha"
    )

    // Максимальная длина PIN-кода
    val maxPinLength = 6

    // Оптимизированный LaunchedEffect для предотвращения мигания и анимации успешной аутентификации
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated && !isAuthenticatedProcessed.value) {
            // Показываем анимацию успешной аутентификации
            showSuccessAnimation = true
            animationPinInput = "123456".take(maxPinLength) // Заполняем все точки
            
            // Задержка перед переходом на следующий экран
            delay(800)
            
            isAuthenticatedProcessed.value = true
            pinInput = "" // Очищаем PIN после успешной авторизации
            authViewModel.resetAuthState() // Сбрасываем состояние авторизации
            showSuccessAnimation = false
            animationPinInput = ""
            onAuthSuccess()
        } else if (!state.isAuthenticated) {
            isAuthenticatedProcessed.value = false
            showSuccessAnimation = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Иконка в верхней части экрана
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (state.showPinInput || state.attemptCount >= 5 || !state.isBiometricEnabled)
                            painterResource(R.drawable.ic_lock) //Icons.Default.Lock
                        else
                            painterResource(R.drawable.ic_fingerprint),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Заголовок
                Text(
                    text = if (state.showPinInput || state.attemptCount >= 5 || !state.isBiometricEnabled) {
                        "Введіть PIN-код"
                    } else {
                        "Розблокувати My Notes"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Подзаголовок
                Text(
                    text = if (state.showPinInput || state.attemptCount >= 5 || !state.isBiometricEnabled) {
                        "Введіть ваш PIN-код для розблокування"
                    } else {
                        "Використайте відбиток пальця для доступу"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN индикаторы (точки)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(maxPinLength) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        showSuccessAnimation && index < animationPinInput.length -> 
                                            MaterialTheme.colorScheme.primary
                                        !showSuccessAnimation && index < pinInput.length -> 
                                            MaterialTheme.colorScheme.primary
                                        else -> 
                                            MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Сообщение об ошибке с мемоизацией для предотвращения мигания
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    state.errorMessage?.let { errorMsg ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = errorMsg,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))



                // Цифровая клавиатура
                AnimatedVisibility(
                    visible = (state.showPinInput || state.attemptCount >= 5 || !state.isBiometricEnabled) && !showSuccessAnimation,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Ряд 1: 1 2 3
                        NumpadRow(
                            numbers = listOf("1", "2", "3"),
                            onClick = { digit ->
                                if (pinInput.length < maxPinLength) {
                                    pinInput += digit

                                    // Автоматическая проверка PIN, когда длина достигает 4-6 символов
                                    if (pinInput.length >= 4) {
                                        authViewModel.verifyPin(pinInput)
                                    }
                                }
                            }
                        )

                        // Ряд 2: 4 5 6
                        NumpadRow(
                            numbers = listOf("4", "5", "6"),
                            onClick = { digit ->
                                if (pinInput.length < maxPinLength) {
                                    pinInput += digit

                                    // Автоматическая проверка PIN, когда длина достигает 4-6 символов
                                    if (pinInput.length >= 4) {
                                        authViewModel.verifyPin(pinInput)
                                    }
                                }
                            }
                        )

                        // Ряд 3: 7 8 9
                        NumpadRow(
                            numbers = listOf("7", "8", "9"),
                            onClick = { digit ->
                                if (pinInput.length < maxPinLength) {
                                    pinInput += digit

                                    // Автоматическая проверка PIN, когда длина достигает 4-6 символов
                                    if (pinInput.length >= 4) {
                                        authViewModel.verifyPin(pinInput)
                                    }
                                }
                            }
                        )

                        // Ряд 4: биометрия, 0, стереть
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Кнопка биометрии (под цифрой 7)
                            if (state.isBiometricEnabled && state.attemptCount < 5) {
                                IconButton(
                                    onClick = { authViewModel.authenticateWithBiometric(context) },
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_fingerprint),
                                        contentDescription = "Використати біометрію",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                // Пустая кнопка для симметрии, если биометрия недоступна
                                Box(modifier = Modifier.size(64.dp))
                            }

                            // Кнопка с цифрой 0
                            NumpadButton(
                                text = "0",
                                onClick = {
                                    if (pinInput.length < maxPinLength) {
                                        pinInput += "0"

                                        // Автоматическая проверка PIN, когда длина достигает 4-6 символов
                                        if (pinInput.length >= 4) {
                                            authViewModel.verifyPin(pinInput)
                                        }
                                    }
                                }
                            )

                            // Кнопка стереть (backspace)
                            IconButton(
                                onClick = {
                                    if (pinInput.isNotEmpty()) {
                                        pinInput = pinInput.dropLast(1)
                                    }
                                },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_backspace),
                                    contentDescription = "Стерти",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }


                // Индикатор загрузки
                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun NumpadRow(
    numbers: List<String>,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        numbers.forEach { number ->
            NumpadButton(
                text = number,
                onClick = { onClick(number) }
            )
        }
    }
}

@Composable
fun NumpadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
