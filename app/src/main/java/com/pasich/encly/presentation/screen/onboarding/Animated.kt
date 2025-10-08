package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay


@Composable
fun AnimatedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    delay: Int = 0
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible, enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
            initialOffsetY = { it / 3 }, animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    ) {
        Text(
            text = text, style = style, modifier = modifier, textAlign = textAlign, color = color
        )
    }
}

@Composable
fun AnimatedButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    delay: Int = 0,
    isSecondary: Boolean = false,
    icon: ImageVector? = null,
    smallStyle: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    val shape = if (smallStyle) RoundedCornerShape(8.dp) else RoundedCornerShape(12.dp)
    val sizeHeight = if (smallStyle) 33.dp else 46.dp
    val styleText =
        if (smallStyle) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )

    AnimatedVisibility(
        visible = visible, enter = scaleIn(
            animationSpec = tween(400, easing = FastOutSlowInEasing), initialScale = 0.8f
        ) + fadeIn()
    ) {
        if (isSecondary) {
            OutlinedButton(
                onClick = onClick, modifier = modifier.height(sizeHeight), shape = shape
            ) {
                Text(text, style = styleText)
            }
        } else {
            Button(
                onClick = onClick, modifier = modifier.height(sizeHeight), shape = shape
            ) {
                Text(
                    text, style = styleText
                )
                icon?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


