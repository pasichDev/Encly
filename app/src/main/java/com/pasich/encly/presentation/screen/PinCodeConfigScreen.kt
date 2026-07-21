package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import kotlinx.coroutines.delay


enum class PinAnimationState {
    Entering, SuccessAnimation
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PinCodeConfigScreen(
    navController: NavHostController, securityViewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(1) }
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var animationState by remember { mutableStateOf(PinAnimationState.Entering) }
    LocalContext.current

    LaunchedEffect(currentInput) {
        if (currentInput.length == 4 && animationState == PinAnimationState.Entering) {
            when (step) {
                1 -> {
                    firstPin = currentInput
                    currentInput = ""
                    step = 2
                }

                2 -> {
                    if (currentInput == firstPin) {
                        animationState = PinAnimationState.SuccessAnimation
                        delay(1200)
                        securityViewModel.activationPinAuth(firstPin)
                        navController.popBackStack()
                    } else {
                        errorText = "PIN-коди не збігаються. Спробуйте ще раз."
                        firstPin = ""
                        currentInput = ""
                        step = 1
                    }
                }
            }
        }
    }


    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (animationState == PinAnimationState.Entering) {
                // 🔒 Main UI
                AnimatedContent(
                    targetState = step, transitionSpec = {
                        // You can change the animation as you like
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    }, label = "StepAnimation"
                ) { currentStep ->
                    MainPinContent(
                        step = currentStep,
                        errorText = errorText,
                        currentInput = currentInput,
                        onInput = { if (currentInput.length < 4) currentInput += it },
                        onDelete = {
                            if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
                        })
                }

            } else {
                // ✅ Success animation
                SuccessAnimation()
            }
        }
    }
}

@Composable
fun MainPinContent(
    step: Int,
    errorText: String?,
    currentInput: String,
    onInput: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🔒 Icon
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

        // Title
        Text(
            text = if (step == 1) "Створіть PIN-код" else "Підтвердіть PIN-код",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = if (step == 1) "Введіть новий PIN-код для захисту нотаток"
            else "Повторно введіть PIN-код для підтвердження",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        errorText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        PinCodeWidget(
            pinInput = currentInput, onPinChange = onInput, onDelete = onDelete
        )
    }
}

@Composable
fun SuccessAnimation() {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_check), // add your own checkmark
            contentDescription = "Успіх",
            modifier = Modifier
                .size(120.dp)
                .scale(scale.value),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
