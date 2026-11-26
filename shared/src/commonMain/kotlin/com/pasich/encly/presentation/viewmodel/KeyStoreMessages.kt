package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.error_keystore_unavailable
import com.pasich.encly.core.resources.key_not_found_title
import com.pasich.encly.core.resources.key_store_unavailable
import com.pasich.encly.core.resources.keyring_no_default
import com.pasich.encly.core.resources.keyring_not_running
import com.pasich.encly.core.resources.keyring_prompt_dismissed
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinCheck
import org.jetbrains.compose.resources.StringResource

/** What to tell the user when the device key store is not usable, by what [problem] says is missing. */
internal fun keyStoreMessage(problem: KeyStoreProblem?): StringResource = when (problem) {
    KeyStoreProblem.NOT_RUNNING -> Res.string.keyring_not_running
    KeyStoreProblem.NO_DEFAULT -> Res.string.keyring_no_default
    KeyStoreProblem.DISMISSED -> Res.string.keyring_prompt_dismissed
    KeyStoreProblem.LOCKED, KeyStoreProblem.OTHER, null -> Res.string.key_store_unavailable
}

/**
 * What a refused check of the current secret says, or null when it passed. [PinCheck.WRONG] is the only one that says
 * the secret is wrong: the others are the device key store not answering, which says nothing about the secret.
 */
internal fun pinCheckError(check: PinCheck, problem: KeyStoreProblem?): StringResource? = when (check) {
    PinCheck.OK -> null
    PinCheck.WRONG -> Res.string.pin_current_wrong
    PinCheck.KEY_STORE_UNAVAILABLE -> keyStoreMessage(problem)
    PinCheck.KEY_NOT_FOUND -> Res.string.key_not_found_title
    PinCheck.KEY_STORE_FAILED -> Res.string.error_keystore_unavailable
    PinCheck.KEY_LOST -> Res.string.lock_pin_key_lost
}
