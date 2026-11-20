package com.pasich.encly.presentation.screen.pincode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_lockout_hours
import com.pasich.encly.core.resources.lock_lockout_minutes
import com.pasich.encly.core.resources.lock_lockout_seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource

private const val MILLIS_PER_SECOND = 1_000L
private const val LOCKOUT_ROUNDING_MILLIS = MILLIS_PER_SECOND - 1L

/** Whole seconds left in a PIN lockout, rounded up so "0 s" is never shown while locked. */
fun lockoutSecondsLeft(remainingMillis: Long): Long =
    (remainingMillis.coerceAtLeast(0L) + LOCKOUT_ROUNDING_MILLIS) / MILLIS_PER_SECOND

/**
 * "Too many attempts. Try again in N seconds / minutes / hours", shared by every PIN entry.
 * Rounded up, so a running lockout never reads as zero.
 */
@Composable
fun pinLockoutText(seconds: Long): String {
    val (plural, count) = lockoutUnit(seconds)
    return pluralStringResource(plural, count, count)
}

/** The plural and count [pinLockoutText] shows for [seconds]. */
internal fun lockoutUnit(seconds: Long): Pair<PluralStringResource, Int> = when {
    seconds < 2 * SECONDS_PER_MINUTE -> Res.plurals.lock_lockout_seconds to seconds.toInt()

    seconds < 2 * SECONDS_PER_HOUR ->
        Res.plurals.lock_lockout_minutes to ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt()

    else -> Res.plurals.lock_lockout_hours to ((seconds + SECONDS_PER_HOUR - 1) / SECONDS_PER_HOUR).toInt()
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * Reports the remaining PIN lockout every second until it ends. Restarts whenever [key]
 * changes: pass `seconds > 0`, so a lockout that a failed attempt just started is counted
 * down too, not only one already running when the screen opened.
 */
@Composable
fun PinLockoutTicker(key: Any?, remainingMillis: () -> Long, onSecondsChange: (Long) -> Unit) {
    // The loop outlives recompositions (it restarts only on [key]); always call the latest lambdas.
    val currentRemainingMillis by rememberUpdatedState(remainingMillis)
    val currentOnSecondsChange by rememberUpdatedState(onSecondsChange)
    LaunchedEffect(key) {
        while (true) {
            val remaining = currentRemainingMillis()
            currentOnSecondsChange(lockoutSecondsLeft(remaining))
            if (remaining <= 0) break
            delay(MILLIS_PER_SECOND)
        }
    }
}
