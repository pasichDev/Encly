package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.pin_delete_digit
import com.pasich.encly.core.resources.pin_digits_count
import com.pasich.encly.core.resources.pin_unlock_with_fingerprint
import com.pasich.encly.core.security.MAX_PIN_LENGTH
import com.pasich.encly.platform.hostString
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/** Keys above the bottom row, row by row. */
private const val KEYPAD_DIGITS = "123456789"
private const val KEYPAD_COLUMNS = 3

/** A wrong PIN shakes the dots three times, 8 dp each way, in 300 ms. */
private const val SHAKE_CYCLES = 3
private const val SHAKE_DP = 8f
private const val SHAKE_QUARTER_MS = 25

/** The diameter of a PIN dot; also the height of the row, so it does not jump when the first digit is typed. */
private val PIN_DOT_SIZE = 14.dp

/** Test tags of one PIN dot, normal and in the error colour. */
const val PIN_DOT_TAG = "pin_dot"
const val PIN_DOT_ERROR_TAG = "pin_dot_error"

/**
 * The PIN progress dots: one filled `primary` dot per digit typed, nothing else. A PIN is 6 to [MAX_PIN_LENGTH]
 * digits long and the lock screen never says which: no empty rings are drawn ahead of the digits, so the row is
 * empty before the first digit and the dots show only what has been typed. The row keeps the
 * dot's height while empty, so the layout does not move. [error] turns every dot `error`; each new
 * [shakeKey] above zero shakes them once. A failed attempt clears the digits, so while [error] is on and nothing is
 * typed yet the row shows the dots of the attempt that just failed (the last non-empty count it was given, kept
 * here and not in a view model) in the error colour; they go with the first new digit. That repeats only what
 * was visibly typed a moment ago.
 */
@Composable
fun PinDots(entered: Int, modifier: Modifier = Modifier, error: Boolean = false, shakeKey: Int = 0) {
    val colors = MaterialTheme.colorScheme
    // Not saveable on purpose: how many digits were typed must not go into the saved instance state.
    var lastTyped by remember { mutableIntStateOf(0) }
    if (entered > 0) lastTyped = entered
    val length = (if (error && entered == 0) lastTyped else entered).coerceIn(0, MAX_PIN_LENGTH)
    val description = stringResource(Res.string.pin_digits_count, entered)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        repeat(SHAKE_CYCLES) {
            shake.animateTo(SHAKE_DP, tween(SHAKE_QUARTER_MS))
            shake.animateTo(-SHAKE_DP, tween(SHAKE_QUARTER_MS * 2))
            shake.animateTo(0f, tween(SHAKE_QUARTER_MS))
        }
    }
    LtrContent {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m, Alignment.CenterHorizontally),
            modifier = modifier
                .fillMaxWidth()
                .height(PIN_DOT_SIZE)
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                .semantics { contentDescription = description },
        ) {
            val tint = if (error) colors.error else colors.primary
            repeat(length) {
                Box(
                    modifier = Modifier
                        .size(PIN_DOT_SIZE)
                        .testTag(if (error) PIN_DOT_ERROR_TAG else PIN_DOT_TAG)
                        .background(tint, CircleShape),
                )
            }
        }
    }
}

/**
 * How much of its full size a PIN screen keeps in the room it has: [fraction] 1 is the normal layout, 0 the
 * tightest (small header and gaps, short keys); [showHeaderIcon] false drops the logo tile when even that is too
 * tall. The scaffold works it out from its height and hands it to its body, which passes it to the entry and the
 * keypad; the default is the normal layout.
 */
@Immutable
class PinFit(val fraction: Float, val showHeaderIcon: Boolean) {
    companion object {
        val Full = PinFit(1f, true)
    }
}

/** Under this height the PIN screen starts to shrink; above it nothing changes (a normal phone). */
private val PIN_FIT_FULL_HEIGHT = 780.dp

/** At this height the shrinking is done and the logo tile goes; below it the screen scrolls. */
private val PIN_FIT_TIGHT_HEIGHT = 480.dp

/** The [PinFit] for a PIN screen whose body area is [height] tall (status bar, keyboard and footer excluded). */
fun pinFitFor(height: Dp): PinFit {
    if (height >= PIN_FIT_FULL_HEIGHT) return PinFit.Full
    val fraction = ((height - PIN_FIT_TIGHT_HEIGHT) / (PIN_FIT_FULL_HEIGHT - PIN_FIT_TIGHT_HEIGHT)).coerceIn(0f, 1f)
    return PinFit(fraction, showHeaderIcon = height >= PIN_FIT_TIGHT_HEIGHT)
}

/**
 * Keypad sizes: onboarding setup (56 dp keys, 10 dp gap) and the lock screen (64 dp, 12 dp). On a short window the
 * lock keypad shrinks towards [minKeyHeight] and [minGap] (see [PinFit]); setup is never shrunk.
 */
enum class KeypadSize(val keyHeight: Dp, val gap: Dp, val minKeyHeight: Dp = keyHeight, val minGap: Dp = gap) {
    SETUP(56.dp, 10.dp),
    LOCK(64.dp, 12.dp, minKeyHeight = 48.dp, minGap = 4.dp),
}

/**
 * The PIN keypad: a 3-column grid of round `surfaceContainer` keys, bottom row
 * [biometric] [0] [backspace]. [onBiometric] null leaves the bottom-left slot empty. A long press on
 * backspace clears every digit ([onClear]; by default delete repeated for a whole PIN) with a haptic tick.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    size: KeypadSize = KeypadSize.SETUP,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    fit: PinFit = PinFit.Full,
) {
    val keyHeight = lerp(size.minKeyHeight, size.keyHeight, fit.fraction)
    val gap = lerp(size.minGap, size.gap, fit.fraction)
    LtrContent {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
        ) {
            KEYPAD_DIGITS.chunked(KEYPAD_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.map { it.digitToInt() }.forEach { digit ->
                        DigitKey(digit, keyHeight, enabled, { onDigit(digit) }, Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (onBiometric != null) {
                    BiometricKey(onBiometric, keyHeight, enabled, Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                DigitKey(0, keyHeight, enabled, { onDigit(0) }, Modifier.weight(1f))
                BackspaceKey(onBackspace, onClear, keyHeight, enabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BiometricKey(onClick: () -> Unit, keyHeight: Dp, enabled: Boolean, modifier: Modifier = Modifier) {
    Key(
        onClick = onClick,
        keyHeight = keyHeight,
        enabled = enabled,
        container = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Icon(
            imageVector = EnclyIcons.Fingerprint,
            contentDescription = hostString(Res.string.pin_unlock_with_fingerprint),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Delete one digit; a long press clears them all ([onClear], else delete repeated for a whole PIN). */
@Composable
private fun BackspaceKey(
    onBackspace: () -> Unit,
    onClear: (() -> Unit)?,
    keyHeight: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Key(
        onClick = onBackspace,
        keyHeight = keyHeight,
        enabled = enabled,
        container = Color.Transparent,
        modifier = modifier,
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // No clear handler: delete once per digit a PIN can hold.
            onClear?.invoke() ?: repeat(MAX_PIN_LENGTH) { onBackspace() }
        },
    ) {
        Icon(
            imageVector = EnclyIcons.Backspace,
            contentDescription = stringResource(Res.string.pin_delete_digit),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun DigitKey(digit: Int, keyHeight: Dp, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Key(
        onClick = onClick,
        keyHeight = keyHeight,
        enabled = enabled,
        container = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Text(text = digit.toString(), style = EnclyTheme.typography.keypadDigit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    onClick: () -> Unit,
    keyHeight: Dp,
    enabled: Boolean,
    container: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val color = if (enabled) container else container.copy(alpha = container.alpha * DISABLED_CONTENT_ALPHA)
    val contentColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.disabled() }
    Surface(
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
        modifier = modifier.height(keyHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.enclyInteractive(CircleShape, enabled).combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        ) { content() }
    }
}
