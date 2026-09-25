package com.pasich.encly.core.security

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How long the vault may stay open after the user leaves the app (Settings → Security). */
@Suppress("MagicNumber") // The offered delays themselves.
enum class AutoLockDelay(val millis: Long) {
    IMMEDIATELY(0L),
    SECONDS_15(15_000L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_2(120_000L),
    ;

    companion object {
        val DEFAULT = SECONDS_15
    }
}

/** What [SessionLockManager] reads when the app goes to the background. */
interface AutoLockPolicy {
    val delayMillis: Long

    /** Tests and previews: lock the moment the app leaves. */
    object Immediately : AutoLockPolicy {
        override val delayMillis: Long = 0L
    }
}

/**
 * The stored auto-lock delay. The screen turning off or the device locking still closes the
 * vault at once, whatever the delay (see [SessionLockManager]).
 */
@Singleton
class AutoLock @Inject constructor(private val flags: SharedPreferences) : AutoLockPolicy {
    private val _delay = MutableStateFlow(read())
    val delay: StateFlow<AutoLockDelay> = _delay.asStateFlow()

    override val delayMillis: Long get() = _delay.value.millis

    fun setDelay(delay: AutoLockDelay) {
        flags.edit().putString(DELAY_KEY, delay.name).apply()
        _delay.value = delay
    }

    private fun read(): AutoLockDelay = flags.getString(DELAY_KEY, null)
        ?.let { name -> AutoLockDelay.entries.firstOrNull { it.name == name } }
        ?: AutoLockDelay.DEFAULT

    private companion object {
        const val DELAY_KEY = "auto_lock_delay"
    }
}
