package com.pasich.encly.presentation.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.pasich.encly.R
import com.pasich.encly.core.utils.rememberSeedPhraseActions
import com.pasich.encly.presentation.screen.onboarding.AnimatedButton
import com.pasich.encly.presentation.screen.onboarding.AnimatedText
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseBottomSheet(
    seedPhrase: String,
    isPhraseVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onPhraseSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showSuccessMessage by remember { mutableStateOf(false) }

    // Використовуємо новий спільний клас для дій з сід-фразою
    val seedPhraseActions = rememberSeedPhraseActions(
        context = context,
        onFileSaveSuccess = {
            onPhraseSaved()
            showSuccessMessage = true
        },
        onFileSaveError = { /* Handle error */ },
        onGoogleDriveSuccess = {
            onPhraseSaved()
            showSuccessMessage = true
        },
        onGoogleDriveError = { /* Handle error */ },
        onCopySuccess = {
            showSuccessMessage = true
        }
    )

    // Auto-hide success message
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            delay(2000)
            showSuccessMessage = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Анімована іконка ключа з світінням
            val infiniteTransition = rememberInfiniteTransition(label = "key_glow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow"
            )

            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Світіння
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .alpha(glowAlpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Іконка ключа
                Icon(
                    imageVector = Lucide.Key,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedText(
                text = "Сід-фраза",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                delay = 300
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedText(
                text = "Ця фраза захищає всі ваші дані. Зберігайте її в безпечному місці.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                delay = 500
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Key display card з анімацією
            var keyCardVisible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(700)
                keyCardVisible = true
            }

            AnimatedVisibility(
                visible = keyCardVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ваша Сід-фраза",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row {
                                IconButton(onClick = {
                                    seedPhraseActions.copyToClipboard(seedPhrase)
                                }) {
                                    Icon(
                                        imageVector = Lucide.Copy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = onToggleVisibility) {
                                    Icon(
                                        painter = if (isPhraseVisible) painterResource(R.drawable.ic_visible) else painterResource(
                                            R.drawable.ic_unvisible
                                        ),
                                        contentDescription = if (isPhraseVisible) "Приховати" else "Показати",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                            }

                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SelectionContainer {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(
                                    text = if (isPhraseVisible) seedPhrase else "•".repeat(seedPhrase.length),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons з анімацією
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Save to file button
                AnimatedButton(
                    text = "Зберегти локально",
                    onClick = {
                        seedPhraseActions.saveToFile(seedPhrase)
                    },
                    delay = 1150,
                    icon = Lucide.Save,
                    isSecondary = true,
                )

                // Save to Google Drive button
                AnimatedButton(
                    text = "Зберегти в Google Drive",
                    onClick = {
                        seedPhraseActions.saveToGoogleDrive(seedPhrase)
                    },
                    delay = 1300,
                    icon = Icons.Default.Share,
                    isSecondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

        }
    }
}
