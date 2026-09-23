package com.pasich.encly.presentation.screen.backup

import androidx.annotation.StringRes
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupError

/** The message shown for a failed export/import. Never includes file content or words. */
@StringRes
fun backupErrorMessage(error: BackupError): Int = when (error) {
    BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup

    BackupError.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported

    BackupError.TRUNCATED, BackupError.CORRUPTED, BackupError.INVALID_PAYLOAD ->
        R.string.backup_error_corrupted

    BackupError.TOO_LARGE -> R.string.backup_error_too_large

    BackupError.TARGET_EXISTS -> R.string.backup_error_target_exists

    BackupError.WRONG_SECRET -> R.string.backup_error_wrong_phrase

    BackupError.INVALID_PHRASE -> R.string.backup_error_invalid_phrase

    BackupError.VAULT_UNAVAILABLE -> R.string.backup_error_locked

    BackupError.IO -> R.string.backup_error_io
}

/** Export-specific wording: there the vault, not a picked file, is what is too large. */
@StringRes
fun backupExportErrorMessage(error: BackupError): Int = when (error) {
    BackupError.TOO_LARGE -> R.string.backup_error_export_too_large
    else -> backupErrorMessage(error)
}
