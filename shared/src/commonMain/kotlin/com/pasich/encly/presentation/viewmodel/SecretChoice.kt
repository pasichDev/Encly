package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_too_long
import com.pasich.encly.core.resources.passphrase_too_short
import com.pasich.encly.core.resources.passphrase_too_simple
import com.pasich.encly.core.resources.passphrase_unusable
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.UnlockSecretPolicy
import org.jetbrains.compose.resources.StringResource

/** Why a passphrase typed to be chosen as a secret is refused; a PIN never is (the keypad only builds valid ones). */
enum class SecretRefusal(val message: StringResource) {
    /** Fewer characters than the policy sets. */
    TOO_SHORT(Res.string.passphrase_too_short),

    /** Long enough, but digits only or with too few different characters. */
    TOO_SIMPLE(Res.string.passphrase_too_simple),

    /** More characters than a passphrase may have. */
    TOO_LONG(Res.string.passphrase_too_long),

    /** The policy cannot make a secret of it at all (for instance a broken surrogate pair). */
    UNUSABLE(Res.string.passphrase_unusable),
}

/**
 * What the screens ask of the unlock secret's policy ([UnlockSecretPolicy]) while a new secret is chosen: its kind, why
 * a typed one is refused, whether two entries are the same secret, and a suggestion. A ViewModel holds one, so a screen
 * never sees the policy itself. Everything it is given stays the caller's to wipe; everything it returns too.
 */
class SecretChoice(private val policy: UnlockSecretPolicy) {
    /** What the user types: a PIN on the keypad or a passphrase in a field. */
    val kind: SecretKind get() = policy.kind

    /**
     * Why [typed] may not be chosen, or null when it may. This is the one place that asks the policy, so the core's
     * `SecurityManager.checkNewSecret` (the same canonical form and rule, in one call) is a one-line swap here.
     * A PIN is never refused here: the keypad only builds PINs of the right length, and the core checks again when it
     * is saved.
     */
    fun refusal(typed: CharArray): SecretRefusal? = if (kind == SecretKind.PIN) null else passphraseRefusal(typed)

    private fun passphraseRefusal(typed: CharArray): SecretRefusal? {
        // The form for choosing: a passphrase over what unlock takes is "too long", not "cannot be used".
        val canonical = policy.canonicalForSetting(typed) ?: return SecretRefusal.UNUSABLE
        return try {
            when (policy.mayBeSet(canonical)) {
                SecretRule.OK -> null
                SecretRule.TOO_SHORT -> SecretRefusal.TOO_SHORT
                SecretRule.TOO_SIMPLE -> SecretRefusal.TOO_SIMPLE
                SecretRule.TOO_LONG -> SecretRefusal.TOO_LONG
                SecretRule.INVALID -> SecretRefusal.UNUSABLE
            }
        } finally {
            SensitiveDataCleaner.clear(canonical)
        }
    }

    /** Whether [first] and [second] are the same secret once the policy has made each canonical form. */
    fun same(first: CharArray, second: CharArray): Boolean {
        if (kind == SecretKind.PIN) return first.contentEquals(second)
        val a = policy.canonical(first)
        val b = policy.canonical(second)
        return try {
            a != null && b != null && a.contentEquals(b)
        } finally {
            listOfNotNull(a, b).forEach { SensitiveDataCleaner.clear(it) }
        }
    }

    /**
     * The most UTF-16 units a typed secret may have, as the policy says: the field takes no more than this.
     */
    val maxTypedUnits: Int get() = policy.maxTypedUnits

    /** A passphrase to offer (five words), or null when the kind has none (a PIN). */
    fun suggest(): CharArray? = policy.suggest()

    companion object {
        /** The most units a typed secret takes until the policy says otherwise. */
        const val DEFAULT_MAX_TYPED_UNITS = 256
    }
}
