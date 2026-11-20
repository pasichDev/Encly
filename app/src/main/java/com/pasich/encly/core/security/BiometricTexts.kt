package com.pasich.encly.core.security

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.biometric_prompt_general_subtitle
import com.pasich.encly.core.resources.biometric_prompt_general_title
import com.pasich.encly.core.resources.biometric_prompt_key_subtitle
import com.pasich.encly.core.resources.biometric_prompt_key_title
import com.pasich.encly.core.resources.biometric_prompt_settings_subtitle
import com.pasich.encly.core.resources.biometric_prompt_settings_title
import com.pasich.encly.core.resources.biometric_prompt_unlock_subtitle
import com.pasich.encly.core.resources.biometric_prompt_unlock_title
import com.pasich.encly.core.resources.biometric_prompt_use_pin
import com.pasich.encly.core.resources.biometric_unavailable
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.security.BiometricManager.PromptConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** The texts of the system biometric prompts, as plain strings in the in-app language. */
class BiometricTexts(
    val unavailable: String,
    val cancel: String,
    val unlock: PromptConfig,
    val masterKey: PromptConfig,
    val settings: PromptConfig,
    val general: PromptConfig,
)

/**
 * Holds the [BiometricTexts] for [KeystoreBiometricManager], which builds the system prompt outside any
 * coroutine and so is handed finished strings instead of resolving resources itself. The activity calls
 * [refresh] while the splash screen is up (and again after every language change, which recreates it).
 */
@SingleIn(AppScope::class)
@Inject
class BiometricTextSource {
    @Volatile
    private var texts: BiometricTexts? = null

    /** Reads the texts in the current language. */
    suspend fun refresh() {
        texts = BiometricTexts(
            unavailable = getString(Res.string.biometric_unavailable),
            cancel = getString(Res.string.cancel),
            unlock = PromptConfig(
                title = getString(Res.string.biometric_prompt_unlock_title),
                subtitle = getString(Res.string.biometric_prompt_unlock_subtitle),
                negativeButtonText = getString(Res.string.biometric_prompt_use_pin),
            ),
            masterKey = PromptConfig(
                title = getString(Res.string.biometric_prompt_key_title),
                subtitle = getString(Res.string.biometric_prompt_key_subtitle),
            ),
            settings = PromptConfig(
                title = getString(Res.string.biometric_prompt_settings_title),
                subtitle = getString(Res.string.biometric_prompt_settings_subtitle),
            ),
            general = PromptConfig(
                title = getString(Res.string.biometric_prompt_general_title),
                subtitle = getString(Res.string.biometric_prompt_general_subtitle),
            ),
        )
    }

    /** Uses [texts] as they are (tests, or a caller that resolved them itself). */
    fun set(texts: BiometricTexts) {
        this.texts = texts
    }

    /**
     * The texts of the last [refresh]. A process started by another entry point than MainActivity
     * (the My Notes hand-off) may reach a prompt before any refresh: then they are read here, once,
     * so the unlock never fails over a missing title.
     */
    fun current(): BiometricTexts = texts ?: runBlocking { refresh() }.let { checkNotNull(texts) }
}
