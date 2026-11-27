package com.pasich.encly.platform

import androidx.compose.runtime.Composable
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auth_method_passphrase_desc
import com.pasich.encly.core.resources.auth_method_passphrase_title
import com.pasich.encly.core.resources.auth_method_pin_desc
import com.pasich.encly.core.resources.auth_method_pin_title
import com.pasich.encly.core.resources.backup_needs_phrase_body
import com.pasich.encly.core.resources.backup_needs_phrase_body_passphrase
import com.pasich.encly.core.resources.backup_reauth_passphrase_subtitle
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.duress_passphrase_change
import com.pasich.encly.core.resources.duress_passphrase_confirm_subtitle
import com.pasich.encly.core.resources.duress_passphrase_confirm_title
import com.pasich.encly.core.resources.duress_passphrase_current_subtitle
import com.pasich.encly.core.resources.duress_passphrase_failed
import com.pasich.encly.core.resources.duress_passphrase_intro
import com.pasich.encly.core.resources.duress_passphrase_limits
import com.pasich.encly.core.resources.duress_passphrase_new_subtitle
import com.pasich.encly.core.resources.duress_passphrase_new_title
import com.pasich.encly.core.resources.duress_passphrase_real_vault
import com.pasich.encly.core.resources.duress_passphrase_remove
import com.pasich.encly.core.resources.duress_passphrase_remove_confirm_title
import com.pasich.encly.core.resources.duress_passphrase_removed
import com.pasich.encly.core.resources.duress_passphrase_row_desc
import com.pasich.encly.core.resources.duress_passphrase_row_turned_off
import com.pasich.encly.core.resources.duress_passphrase_same_as_passphrase
import com.pasich.encly.core.resources.duress_passphrase_saved
import com.pasich.encly.core.resources.duress_passphrase_saved_kept
import com.pasich.encly.core.resources.duress_passphrase_set
import com.pasich.encly.core.resources.duress_passphrase_status_off
import com.pasich.encly.core.resources.duress_passphrase_status_on
import com.pasich.encly.core.resources.duress_passphrase_title
import com.pasich.encly.core.resources.duress_passphrase_turned_off
import com.pasich.encly.core.resources.duress_pin_change
import com.pasich.encly.core.resources.duress_pin_confirm_subtitle
import com.pasich.encly.core.resources.duress_pin_confirm_title
import com.pasich.encly.core.resources.duress_pin_current_subtitle
import com.pasich.encly.core.resources.duress_pin_failed
import com.pasich.encly.core.resources.duress_pin_intro
import com.pasich.encly.core.resources.duress_pin_limits
import com.pasich.encly.core.resources.duress_pin_new_subtitle
import com.pasich.encly.core.resources.duress_pin_new_title
import com.pasich.encly.core.resources.duress_pin_real_vault
import com.pasich.encly.core.resources.duress_pin_remove
import com.pasich.encly.core.resources.duress_pin_remove_confirm_title
import com.pasich.encly.core.resources.duress_pin_removed
import com.pasich.encly.core.resources.duress_pin_row_desc
import com.pasich.encly.core.resources.duress_pin_row_turned_off
import com.pasich.encly.core.resources.duress_pin_same_as_pin
import com.pasich.encly.core.resources.duress_pin_saved
import com.pasich.encly.core.resources.duress_pin_saved_kept
import com.pasich.encly.core.resources.duress_pin_set
import com.pasich.encly.core.resources.duress_pin_status_off
import com.pasich.encly.core.resources.duress_pin_status_on
import com.pasich.encly.core.resources.duress_pin_title
import com.pasich.encly.core.resources.duress_pin_turned_off
import com.pasich.encly.core.resources.faq_change_passphrase_a
import com.pasich.encly.core.resources.faq_change_passphrase_q
import com.pasich.encly.core.resources.faq_change_pin_a
import com.pasich.encly.core.resources.faq_change_pin_q
import com.pasich.encly.core.resources.faq_db_key_a
import com.pasich.encly.core.resources.faq_db_key_a_passphrase
import com.pasich.encly.core.resources.faq_forgot_passphrase_a
import com.pasich.encly.core.resources.faq_forgot_passphrase_q
import com.pasich.encly.core.resources.faq_forgot_pin_a
import com.pasich.encly.core.resources.faq_forgot_pin_q
import com.pasich.encly.core.resources.faq_markdown_a
import com.pasich.encly.core.resources.faq_markdown_a_passphrase
import com.pasich.encly.core.resources.faq_passphrase_length_a
import com.pasich.encly.core.resources.faq_passphrase_length_q
import com.pasich.encly.core.resources.faq_pin_length_a
import com.pasich.encly.core.resources.faq_pin_length_q
import com.pasich.encly.core.resources.faq_recovery_phrase_a
import com.pasich.encly.core.resources.faq_recovery_phrase_a_passphrase
import com.pasich.encly.core.resources.faq_report_bug_a
import com.pasich.encly.core.resources.faq_report_bug_a_passphrase
import com.pasich.encly.core.resources.lock_back_to_passphrase
import com.pasich.encly.core.resources.lock_back_to_pin
import com.pasich.encly.core.resources.lock_passphrase_key_lost
import com.pasich.encly.core.resources.lock_passphrase_subtitle
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_wrong_passphrase
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.resources.markdown_export_warning_body
import com.pasich.encly.core.resources.markdown_export_warning_body_passphrase
import com.pasich.encly.core.resources.onboarding_passphrase_again_body
import com.pasich.encly.core.resources.onboarding_passphrase_again_title
import com.pasich.encly.core.resources.onboarding_passphrase_body
import com.pasich.encly.core.resources.onboarding_passphrase_title
import com.pasich.encly.core.resources.onboarding_pin_again_body
import com.pasich.encly.core.resources.onboarding_pin_again_title
import com.pasich.encly.core.resources.onboarding_pin_body
import com.pasich.encly.core.resources.onboarding_pin_title
import com.pasich.encly.core.resources.onboarding_ready_passphrase_desc
import com.pasich.encly.core.resources.onboarding_ready_passphrase_title
import com.pasich.encly.core.resources.onboarding_ready_pin_desc
import com.pasich.encly.core.resources.onboarding_ready_pin_title
import com.pasich.encly.core.resources.onboarding_recovery_passphrase_desc
import com.pasich.encly.core.resources.onboarding_recovery_passphrase_title
import com.pasich.encly.core.resources.onboarding_recovery_pin_desc
import com.pasich.encly.core.resources.onboarding_recovery_pin_title
import com.pasich.encly.core.resources.onboarding_recovery_skip
import com.pasich.encly.core.resources.onboarding_recovery_skip_passphrase
import com.pasich.encly.core.resources.onboarding_recovery_warning
import com.pasich.encly.core.resources.onboarding_recovery_warning_passphrase
import com.pasich.encly.core.resources.onboarding_restore_label
import com.pasich.encly.core.resources.onboarding_restore_label_passphrase
import com.pasich.encly.core.resources.passphrase_change_bar_title
import com.pasich.encly.core.resources.passphrase_change_confirm_subtitle
import com.pasich.encly.core.resources.passphrase_change_confirm_title
import com.pasich.encly.core.resources.passphrase_change_current_subtitle
import com.pasich.encly.core.resources.passphrase_change_current_title
import com.pasich.encly.core.resources.passphrase_change_new_subtitle
import com.pasich.encly.core.resources.passphrase_changed
import com.pasich.encly.core.resources.passphrase_create_title
import com.pasich.encly.core.resources.passphrase_current_wrong
import com.pasich.encly.core.resources.passphrase_in_use
import com.pasich.encly.core.resources.passphrase_mismatch_retry
import com.pasich.encly.core.resources.passphrase_reset_subtitle
import com.pasich.encly.core.resources.passphrase_save_failed
import com.pasich.encly.core.resources.passphrase_update_failed
import com.pasich.encly.core.resources.pdf_export_warning_body
import com.pasich.encly.core.resources.pdf_export_warning_body_passphrase
import com.pasich.encly.core.resources.pin_change_bar_title
import com.pasich.encly.core.resources.pin_change_confirm_subtitle
import com.pasich.encly.core.resources.pin_change_confirm_title
import com.pasich.encly.core.resources.pin_change_current_subtitle
import com.pasich.encly.core.resources.pin_change_current_title
import com.pasich.encly.core.resources.pin_change_new_subtitle
import com.pasich.encly.core.resources.pin_changed
import com.pasich.encly.core.resources.pin_create_title
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.resources.pin_in_use
import com.pasich.encly.core.resources.pin_mismatch_retry
import com.pasich.encly.core.resources.pin_reset_subtitle
import com.pasich.encly.core.resources.pin_save_failed
import com.pasich.encly.core.resources.pin_update_failed
import com.pasich.encly.core.resources.print_warning_body
import com.pasich.encly.core.resources.print_warning_body_passphrase
import com.pasich.encly.core.resources.recovery_choice_body_passphrase
import com.pasich.encly.core.resources.recovery_choice_body_pin
import com.pasich.encly.core.resources.recovery_keep_passphrase
import com.pasich.encly.core.resources.recovery_keep_pin
import com.pasich.encly.core.resources.recovery_set_new_passphrase
import com.pasich.encly.core.resources.recovery_set_new_pin
import com.pasich.encly.core.resources.security_encryption_passphrase_plain
import com.pasich.encly.core.resources.security_encryption_passphrase_seed_plain
import com.pasich.encly.core.resources.security_encryption_pin_plain
import com.pasich.encly.core.resources.security_encryption_seed_plain
import com.pasich.encly.core.resources.security_protection_passphrase_desc
import com.pasich.encly.core.resources.security_protection_passphrase_slots_desc
import com.pasich.encly.core.resources.security_protection_passphrase_title
import com.pasich.encly.core.resources.security_protection_pin_desc
import com.pasich.encly.core.resources.security_protection_pin_title
import com.pasich.encly.core.resources.security_protection_slots_desc
import com.pasich.encly.core.resources.security_recovery_missing
import com.pasich.encly.core.resources.security_recovery_missing_passphrase
import com.pasich.encly.core.resources.security_recovery_replace_warning_body
import com.pasich.encly.core.resources.security_recovery_replace_warning_body_passphrase
import com.pasich.encly.core.resources.wipe_passphrase_after_erase
import com.pasich.encly.core.resources.wipe_passphrase_confirm_subtitle
import com.pasich.encly.core.resources.wipe_passphrase_confirm_title
import com.pasich.encly.core.resources.wipe_passphrase_current_subtitle
import com.pasich.encly.core.resources.wipe_passphrase_failed
import com.pasich.encly.core.resources.wipe_passphrase_intro
import com.pasich.encly.core.resources.wipe_passphrase_limits
import com.pasich.encly.core.resources.wipe_passphrase_new_subtitle
import com.pasich.encly.core.resources.wipe_passphrase_new_title
import com.pasich.encly.core.resources.wipe_passphrase_remove
import com.pasich.encly.core.resources.wipe_passphrase_removed
import com.pasich.encly.core.resources.wipe_passphrase_row_desc
import com.pasich.encly.core.resources.wipe_passphrase_row_turned_off
import com.pasich.encly.core.resources.wipe_passphrase_same_as_passphrase
import com.pasich.encly.core.resources.wipe_passphrase_saved
import com.pasich.encly.core.resources.wipe_passphrase_set
import com.pasich.encly.core.resources.wipe_passphrase_title
import com.pasich.encly.core.resources.wipe_passphrase_turned_off
import com.pasich.encly.core.resources.wipe_pin_after_erase
import com.pasich.encly.core.resources.wipe_pin_confirm_subtitle
import com.pasich.encly.core.resources.wipe_pin_confirm_title
import com.pasich.encly.core.resources.wipe_pin_current_subtitle
import com.pasich.encly.core.resources.wipe_pin_failed
import com.pasich.encly.core.resources.wipe_pin_intro
import com.pasich.encly.core.resources.wipe_pin_limits
import com.pasich.encly.core.resources.wipe_pin_new_subtitle
import com.pasich.encly.core.resources.wipe_pin_new_title
import com.pasich.encly.core.resources.wipe_pin_remove
import com.pasich.encly.core.resources.wipe_pin_removed
import com.pasich.encly.core.resources.wipe_pin_row_desc
import com.pasich.encly.core.resources.wipe_pin_row_turned_off
import com.pasich.encly.core.resources.wipe_pin_same_as_pin
import com.pasich.encly.core.resources.wipe_pin_saved
import com.pasich.encly.core.resources.wipe_pin_set
import com.pasich.encly.core.resources.wipe_pin_title
import com.pasich.encly.core.resources.wipe_pin_turned_off
import com.pasich.encly.core.security.SecretKind
import org.jetbrains.compose.resources.StringResource

private val str = Res.string

/**
 * The texts that name the unlock secret, for a vault whose secret is a passphrase: the source text (a PIN text) and its
 * passphrase twin. The twins are written for the desktop (no fingerprint, no phone, a system keychain), so they win
 * over the host variants of [hostVariant]. A text that does not name the secret is not here and reads the same.
 * Nothing here names the recovery phrase, which keeps its own wording.
 */
internal val PassphraseTwins: Map<StringResource, StringResource> = mapOf(
    str.lock_subtitle to str.lock_passphrase_subtitle,
    str.lock_wrong_pin to str.lock_wrong_passphrase,
    str.lock_back_to_pin to str.lock_back_to_passphrase,
    str.lock_pin_key_lost to str.lock_passphrase_key_lost,
    str.backup_reauth_subtitle to str.backup_reauth_passphrase_subtitle,
    str.pin_change_bar_title to str.passphrase_change_bar_title,
    str.pin_change_current_title to str.passphrase_change_current_title,
    str.pin_change_current_subtitle to str.passphrase_change_current_subtitle,
    str.pin_create_title to str.passphrase_create_title,
    str.pin_change_new_subtitle to str.passphrase_change_new_subtitle,
    str.pin_change_confirm_title to str.passphrase_change_confirm_title,
    str.pin_change_confirm_subtitle to str.passphrase_change_confirm_subtitle,
    str.pin_changed to str.passphrase_changed,
    str.pin_current_wrong to str.passphrase_current_wrong,
    str.pin_mismatch_retry to str.passphrase_mismatch_retry,
    str.pin_save_failed to str.passphrase_save_failed,
    str.pin_update_failed to str.passphrase_update_failed,
    str.pin_in_use to str.passphrase_in_use,
    str.pin_reset_subtitle to str.passphrase_reset_subtitle,
    str.recovery_choice_body_pin to str.recovery_choice_body_passphrase,
    str.recovery_keep_pin to str.recovery_keep_passphrase,
    str.recovery_set_new_pin to str.recovery_set_new_passphrase,
    str.onboarding_pin_title to str.onboarding_passphrase_title,
    str.onboarding_pin_body to str.onboarding_passphrase_body,
    str.onboarding_pin_again_title to str.onboarding_passphrase_again_title,
    str.onboarding_pin_again_body to str.onboarding_passphrase_again_body,
    str.onboarding_recovery_pin_title to str.onboarding_recovery_passphrase_title,
    str.onboarding_recovery_pin_desc to str.onboarding_recovery_passphrase_desc,
    str.onboarding_recovery_warning to str.onboarding_recovery_warning_passphrase,
    str.onboarding_recovery_skip to str.onboarding_recovery_skip_passphrase,
    str.onboarding_ready_pin_title to str.onboarding_ready_passphrase_title,
    str.onboarding_ready_pin_desc to str.onboarding_ready_passphrase_desc,
    str.onboarding_restore_label to str.onboarding_restore_label_passphrase,
    str.auth_method_pin_title to str.auth_method_passphrase_title,
    str.auth_method_pin_desc to str.auth_method_passphrase_desc,
    str.security_encryption_pin_plain to str.security_encryption_passphrase_plain,
    str.security_encryption_seed_plain to str.security_encryption_passphrase_seed_plain,
    str.security_protection_pin_title to str.security_protection_passphrase_title,
    str.security_protection_pin_desc to str.security_protection_passphrase_desc,
    str.security_protection_slots_desc to str.security_protection_passphrase_slots_desc,
    str.security_recovery_missing to str.security_recovery_missing_passphrase,
    str.security_recovery_replace_warning_body to str.security_recovery_replace_warning_body_passphrase,
    str.backup_needs_phrase_body to str.backup_needs_phrase_body_passphrase,
    str.markdown_export_warning_body to str.markdown_export_warning_body_passphrase,
    str.pdf_export_warning_body to str.pdf_export_warning_body_passphrase,
    str.print_warning_body to str.print_warning_body_passphrase,
    str.faq_change_pin_q to str.faq_change_passphrase_q,
    str.faq_change_pin_a to str.faq_change_passphrase_a,
    str.faq_forgot_pin_q to str.faq_forgot_passphrase_q,
    str.faq_forgot_pin_a to str.faq_forgot_passphrase_a,
    str.faq_pin_length_q to str.faq_passphrase_length_q,
    str.faq_pin_length_a to str.faq_passphrase_length_a,
    str.faq_db_key_a to str.faq_db_key_a_passphrase,
    str.faq_recovery_phrase_a to str.faq_recovery_phrase_a_passphrase,
    str.faq_report_bug_a to str.faq_report_bug_a_passphrase,
    str.faq_markdown_a to str.faq_markdown_a_passphrase,
    str.wipe_pin_title to str.wipe_passphrase_title,
    str.wipe_pin_row_desc to str.wipe_passphrase_row_desc,
    str.wipe_pin_row_turned_off to str.wipe_passphrase_row_turned_off,
    str.wipe_pin_intro to str.wipe_passphrase_intro,
    str.wipe_pin_after_erase to str.wipe_passphrase_after_erase,
    str.wipe_pin_limits to str.wipe_passphrase_limits,
    str.wipe_pin_set to str.wipe_passphrase_set,
    str.wipe_pin_remove to str.wipe_passphrase_remove,
    str.wipe_pin_current_subtitle to str.wipe_passphrase_current_subtitle,
    str.wipe_pin_new_title to str.wipe_passphrase_new_title,
    str.wipe_pin_new_subtitle to str.wipe_passphrase_new_subtitle,
    str.wipe_pin_confirm_title to str.wipe_passphrase_confirm_title,
    str.wipe_pin_confirm_subtitle to str.wipe_passphrase_confirm_subtitle,
    str.wipe_pin_same_as_pin to str.wipe_passphrase_same_as_passphrase,
    str.wipe_pin_failed to str.wipe_passphrase_failed,
    str.wipe_pin_saved to str.wipe_passphrase_saved,
    str.wipe_pin_removed to str.wipe_passphrase_removed,
    str.wipe_pin_turned_off to str.wipe_passphrase_turned_off,
    str.duress_pin_title to str.duress_passphrase_title,
    str.duress_pin_row_desc to str.duress_passphrase_row_desc,
    str.duress_pin_row_turned_off to str.duress_passphrase_row_turned_off,
    str.duress_pin_intro to str.duress_passphrase_intro,
    str.duress_pin_real_vault to str.duress_passphrase_real_vault,
    str.duress_pin_limits to str.duress_passphrase_limits,
    str.duress_pin_status_on to str.duress_passphrase_status_on,
    str.duress_pin_status_off to str.duress_passphrase_status_off,
    str.duress_pin_set to str.duress_passphrase_set,
    str.duress_pin_change to str.duress_passphrase_change,
    str.duress_pin_remove to str.duress_passphrase_remove,
    str.duress_pin_current_subtitle to str.duress_passphrase_current_subtitle,
    str.duress_pin_new_title to str.duress_passphrase_new_title,
    str.duress_pin_new_subtitle to str.duress_passphrase_new_subtitle,
    str.duress_pin_confirm_title to str.duress_passphrase_confirm_title,
    str.duress_pin_confirm_subtitle to str.duress_passphrase_confirm_subtitle,
    str.duress_pin_same_as_pin to str.duress_passphrase_same_as_passphrase,
    str.duress_pin_failed to str.duress_passphrase_failed,
    str.duress_pin_saved to str.duress_passphrase_saved,
    str.duress_pin_saved_kept to str.duress_passphrase_saved_kept,
    str.duress_pin_removed to str.duress_passphrase_removed,
    str.duress_pin_turned_off to str.duress_passphrase_turned_off,
    str.duress_pin_remove_confirm_title to str.duress_passphrase_remove_confirm_title,
)

/** The string that stands in for [res] where the unlock secret is of [kind] (itself for a PIN: nothing changes). */
internal fun secretVariant(res: StringResource, kind: SecretKind): StringResource =
    if (kind == SecretKind.PASSWORD) PassphraseTwins[res] ?: res else res

/**
 * The text of [res] as the unlock secret of [kind] words it, then as the host words it ([hostString]). Screens ask
 * for "the string for this kind" instead of branching on the kind themselves.
 */
@Composable
fun secretString(kind: SecretKind, res: StringResource): String = hostString(secretVariant(res, kind))
