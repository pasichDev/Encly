package com.pasich.encly.presentation.screen.backup

import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.backup_error_corrupted
import com.pasich.encly.core.resources.backup_error_export_too_large
import com.pasich.encly.core.resources.backup_error_invalid_phrase
import com.pasich.encly.core.resources.backup_error_io
import com.pasich.encly.core.resources.backup_error_locked
import com.pasich.encly.core.resources.backup_error_not_a_backup
import com.pasich.encly.core.resources.backup_error_target_exists
import com.pasich.encly.core.resources.backup_error_too_large
import com.pasich.encly.core.resources.backup_error_unsupported
import com.pasich.encly.core.resources.backup_error_wrong_phrase
import org.jetbrains.compose.resources.StringResource

/** The message shown for a failed export/import. Never includes file content or words. */
fun backupErrorMessage(error: BackupError): StringResource = when (error) {
    BackupError.NOT_A_BACKUP -> Res.string.backup_error_not_a_backup

    BackupError.UNSUPPORTED_VERSION -> Res.string.backup_error_unsupported

    BackupError.TRUNCATED, BackupError.CORRUPTED, BackupError.INVALID_PAYLOAD ->
        Res.string.backup_error_corrupted

    BackupError.TOO_LARGE -> Res.string.backup_error_too_large

    BackupError.TARGET_EXISTS -> Res.string.backup_error_target_exists

    BackupError.WRONG_SECRET -> Res.string.backup_error_wrong_phrase

    BackupError.INVALID_PHRASE -> Res.string.backup_error_invalid_phrase

    BackupError.VAULT_UNAVAILABLE -> Res.string.backup_error_locked

    BackupError.IO -> Res.string.backup_error_io
}

/** Export-specific wording: there the vault, not a picked file, is what is too large. */
fun backupExportErrorMessage(error: BackupError): StringResource = when (error) {
    BackupError.TOO_LARGE -> Res.string.backup_error_export_too_large
    else -> backupErrorMessage(error)
}
