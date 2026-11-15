package com.pasich.encly.platform

import androidx.compose.runtime.Composable
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auto_lock_desc
import com.pasich.encly.core.resources.auto_lock_desc_device
import com.pasich.encly.core.resources.duress_pin_real_vault
import com.pasich.encly.core.resources.duress_pin_real_vault_named
import com.pasich.encly.core.resources.duress_pin_real_vault_nobio
import com.pasich.encly.core.resources.faq_db_key_a
import com.pasich.encly.core.resources.faq_db_key_a_desktop
import com.pasich.encly.core.resources.faq_db_key_a_ios
import com.pasich.encly.core.resources.faq_language_a
import com.pasich.encly.core.resources.faq_language_a_ios
import com.pasich.encly.core.resources.faq_swipe_a
import com.pasich.encly.core.resources.faq_swipe_a_other
import com.pasich.encly.core.resources.faq_theme_a
import com.pasich.encly.core.resources.faq_theme_a_other
import com.pasich.encly.core.resources.import_error_no_picker
import com.pasich.encly.core.resources.import_error_no_picker_device
import com.pasich.encly.core.resources.import_intro
import com.pasich.encly.core.resources.import_intro_device
import com.pasich.encly.core.resources.lock_biometric_invalidated
import com.pasich.encly.core.resources.lock_biometric_invalidated_named
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_pin_key_lost_desktop
import com.pasich.encly.core.resources.lock_pin_key_lost_named
import com.pasich.encly.core.resources.onboarding_ready_biometric_retry
import com.pasich.encly.core.resources.onboarding_ready_biometric_retry_named
import com.pasich.encly.core.resources.onboarding_ready_biometric_title
import com.pasich.encly.core.resources.onboarding_ready_biometric_title_named
import com.pasich.encly.core.resources.pin_unlock_with_biometric_named
import com.pasich.encly.core.resources.pin_unlock_with_fingerprint
import com.pasich.encly.core.resources.security_biometric_not_enrolled
import com.pasich.encly.core.resources.security_biometric_not_enrolled_named
import com.pasich.encly.core.resources.security_encryption_pin_plain
import com.pasich.encly.core.resources.security_encryption_pin_plain_named
import com.pasich.encly.core.resources.security_encryption_pin_plain_nobio
import com.pasich.encly.core.resources.security_encryption_seed_plain
import com.pasich.encly.core.resources.security_encryption_seed_plain_named
import com.pasich.encly.core.resources.security_encryption_seed_plain_nobio
import com.pasich.encly.core.resources.security_protection_pin_desc
import com.pasich.encly.core.resources.security_protection_pin_desc_desktop
import com.pasich.encly.core.resources.security_protection_pin_desc_ios
import com.pasich.encly.core.resources.security_protection_pin_title
import com.pasich.encly.core.resources.security_protection_pin_title_device
import com.pasich.encly.core.resources.security_protection_slots_desc
import com.pasich.encly.core.resources.security_protection_slots_desc_named
import com.pasich.encly.core.resources.security_protection_slots_desc_nobio
import com.pasich.encly.core.resources.wipe_pin_biometric_warning
import com.pasich.encly.core.resources.wipe_pin_biometric_warning_named
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The text of [res] as the host words it. Most strings are the same everywhere; the ones that name a fingerprint, a
 * phone, the Android keystore, TalkBack or Android 12 have a sibling for the host that works differently, and this
 * picks it. Android always gets [res] itself, so its texts do not change.
 */
@Composable
fun hostString(res: StringResource): String {
    val platform = LocalHostPlatform.current
    val wording = LocalBiometricWording.current
    val variant = hostVariant(res, platform, wording)
    return if (wording is BiometricWording.Named) {
        // The name is the only argument; a text without a placeholder ignores it.
        stringResource(variant, wording.name)
    } else {
        stringResource(variant)
    }
}

private val str = Res.string

/** The string that stands in for [res] on [platform] with [wording] (itself when none does). */
internal fun hostVariant(res: StringResource, platform: HostPlatform, wording: BiometricWording): StringResource {
    val named = wording is BiometricWording.Named
    return when (res) {
        str.pin_unlock_with_fingerprint -> pick(named, str.pin_unlock_with_biometric_named, res)

        str.lock_biometric_invalidated -> pick(named, str.lock_biometric_invalidated_named, res)

        str.security_biometric_not_enrolled -> pick(named, str.security_biometric_not_enrolled_named, res)

        str.onboarding_ready_biometric_title -> pick(named, str.onboarding_ready_biometric_title_named, res)

        str.onboarding_ready_biometric_retry -> pick(named, str.onboarding_ready_biometric_retry_named, res)

        str.wipe_pin_biometric_warning -> pick(named, str.wipe_pin_biometric_warning_named, res)

        str.security_encryption_pin_plain ->
            byWording(wording, res, str.security_encryption_pin_plain_named, str.security_encryption_pin_plain_nobio)

        str.security_encryption_seed_plain ->
            byWording(wording, res, str.security_encryption_seed_plain_named, str.security_encryption_seed_plain_nobio)

        str.security_protection_slots_desc ->
            byWording(wording, res, str.security_protection_slots_desc_named, str.security_protection_slots_desc_nobio)

        str.duress_pin_real_vault ->
            byWording(wording, res, str.duress_pin_real_vault_named, str.duress_pin_real_vault_nobio)

        str.lock_pin_key_lost ->
            byWording(wording, res, str.lock_pin_key_lost_named, str.lock_pin_key_lost_desktop)

        else -> platformVariant(res, platform)
    }
}

/** The texts that name the system, its key store or its device rather than a biometric method. */
private fun platformVariant(res: StringResource, platform: HostPlatform): StringResource = when (platform) {
    HostPlatform.ANDROID -> res
    HostPlatform.IOS -> iosVariant(res)
    HostPlatform.DESKTOP -> desktopVariant(res)
}

private fun iosVariant(res: StringResource): StringResource = when (res) {
    str.security_protection_pin_desc -> str.security_protection_pin_desc_ios

    str.faq_db_key_a -> str.faq_db_key_a_ios

    // iOS applies a language at the next start; the desktop switches at once, like Android.
    str.faq_language_a -> str.faq_language_a_ios

    else -> neutralVariant(res)
}

private fun desktopVariant(res: StringResource): StringResource = when (res) {
    str.security_protection_pin_desc -> str.security_protection_pin_desc_desktop
    str.faq_db_key_a -> str.faq_db_key_a_desktop
    else -> neutralVariant(res)
}

/** Texts that say "phone", "Android 12" or "TalkBack" for Android: the same neutral sibling on iOS and the desktop. */
private fun neutralVariant(res: StringResource): StringResource = when (res) {
    str.security_protection_pin_title -> str.security_protection_pin_title_device
    str.faq_theme_a -> str.faq_theme_a_other
    str.faq_swipe_a -> str.faq_swipe_a_other
    str.auto_lock_desc -> str.auto_lock_desc_device
    str.import_intro -> str.import_intro_device
    str.import_error_no_picker -> str.import_error_no_picker_device
    else -> res
}

private fun pick(named: Boolean, variant: StringResource, default: StringResource) = if (named) variant else default

/** [named] for a named method, [none] for a host without biometrics, [default] (the fingerprint text) otherwise. */
private fun byWording(
    wording: BiometricWording,
    default: StringResource,
    named: StringResource,
    none: StringResource,
): StringResource = when (wording) {
    is BiometricWording.Named -> named
    BiometricWording.None -> none
    BiometricWording.Fingerprint -> default
}
