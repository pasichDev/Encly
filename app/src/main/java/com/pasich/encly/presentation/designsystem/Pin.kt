package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Fingerprint
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

/** PIN length of the vault. */
const val PIN_LENGTH = 6

/** Keys above the bottom row, row by row. */
private const val KEYPAD_DIGITS = "123456789"
private const val KEYPAD_COLUMNS = 3

/** A wrong PIN shakes the dots three times, 8 dp each way, in 300 ms. */
private const val SHAKE_CYCLES = 3
private const val SHAKE_DP = 8f
private const val SHAKE_QUARTER_MS = 25

/**
 * The PIN progress dots: filled `primary` for entered digits, a 2 dp `outline` ring otherwise.
 * [error] turns every dot `error`; each new [shakeKey] above zero shakes them once.
 */
@Composable
fun PinDots(
    entered: Int,
    modifier: Modifier = Modifier,
    length: Int = PIN_LENGTH,
    error: Boolean = false,
    shakeKey: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.pin_digits_entered, entered, length)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        repeat(SHAKE_CYCLES) {
            shake.animateTo(SHAKE_DP, tween(SHAKE_QUARTER_MS))
            shake.animateTo(-SHAKE_DP, tween(SHAKE_QUARTER_MS * 2))
            shake.animateTo(0f, tween(SHAKE_QUARTER_MS))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .semantics { contentDescription = description },
    ) {
        repeat(length) { index ->
            val filled = index < entered
            val tint = if (error) colors.error else colors.primary
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(
                        2.dp,
                        if (filled) {
                            tint
                        } else if (error) {
                            colors.error
                        } else {
                            colors.outline
                        },
                        CircleShape,
                    ),
            ) {
                if (filled) Surface(shape = CircleShape, color = tint, modifier = Modifier.size(14.dp)) {}
            }
        }
    }
}

/** Keypad sizes: onboarding setup (56 dp keys, 10 dp gap) and the lock screen (64 dp, 12 dp). */
enum class KeypadSize(val keyHeight: Dp, val gap: Dp) {
    SETUP(56.dp, 10.dp),
    LOCK(64.dp, 12.dp),
}

/**
 * The PIN keypad: a 3-column grid of round `surfaceContainer` keys, bottom row
 * [biometric] [0] [backspace]. [onBiometric] null leaves the bottom-left slot empty.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    size: KeypadSize = KeypadSize.SETUP,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(size.gap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
    ) {
        KEYPAD_DIGITS.chunked(KEYPAD_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(size.gap)) {
                row.map { it.digitToInt() }.forEach { digit ->
                    DigitKey(digit, size, enabled, { onDigit(digit) }, Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(size.gap)) {
            if (onBiometric != null) {
                Key(
                    onClick = onBiometric,
                    size = size,
                    enabled = enabled,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Lucide.Fingerprint,
                        contentDescription = stringResource(R.string.pin_unlock_with_fingerprint),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            DigitKey(0, size, enabled, { onDigit(0) }, Modifier.weight(1f))
            Key(
                onClick = onBackspace,
                size = size,
                enabled = enabled,
                container = Color.Transparent,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Lucide.Delete,
                    contentDescription = stringResource(R.string.pin_delete_digit),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    size: KeypadSize,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Key(
        onClick = onClick,
        size = size,
        enabled = enabled,
        container = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Text(text = digit.toString(), style = EnclyTheme.typography.keypadDigit)
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    size: KeypadSize,
    enabled: Boolean,
    container: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) container else container.copy(alpha = container.alpha * DISABLED_CONTENT_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.disabled() },
        modifier = modifier.height(size.keyHeight),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
