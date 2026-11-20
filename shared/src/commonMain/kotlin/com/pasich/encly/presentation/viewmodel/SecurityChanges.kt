package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.security.AutoLockDelay

/*
 * Settings → Security: a change that weakens protection asks for the PIN (or biometric) first, a change that
 * strengthens it applies at once. The switches read their value from the stores, so until [apply] runs they keep
 * showing the old value; a cancel or a wrong PIN leaves it.
 */

/** A longer auto-lock delay keeps the vault open longer, so it asks; the same or a shorter one does not. */
internal fun BackupViewModel.changeAutoLock(current: AutoLockDelay, new: AutoLockDelay, apply: () -> Unit) =
    askUnless(new.millis <= current.millis, apply)

/** Turning strict keyboard privacy off lets the keyboard learn note text again; turning it on does not ask. */
internal fun BackupViewModel.changeStrictKeyboard(strict: Boolean, apply: () -> Unit) = askUnless(strict, apply)

/** Showing note previews puts note text in the lists; hiding them does not ask. */
internal fun BackupViewModel.changeNotePreviews(show: Boolean, apply: () -> Unit) = askUnless(!show, apply)

/**
 * Enabling biometric unlock asks for the PIN first (the re-authentication offers a fingerprint only when it is
 * already on); disabling keeps its own strong-biometric confirmation.
 */
internal fun BackupViewModel.changeBiometric(enable: Boolean, apply: () -> Unit) = askUnless(!enable, apply)

private fun BackupViewModel.askUnless(harmless: Boolean, apply: () -> Unit) {
    if (harmless) apply() else confirmSetting(apply)
}

/** The one weakening setting change waiting for its re-authentication. */
internal class PendingSetting {
    private var apply: (() -> Unit)? = null

    fun hold(apply: () -> Unit) {
        this.apply = apply
    }

    fun clear() {
        apply = null
    }

    /** Runs the held change when [allowed] and forgets it either way. */
    fun release(allowed: Boolean) {
        val change = apply
        apply = null
        if (allowed) change?.invoke()
    }
}
