package com.pasich.encly.core.backup

/** Keys of the backup flags in the app's non-secret flag store. */
public object BackupFlags {
    /** When the last backup was exported (epoch milliseconds); forgotten when the vault is erased. */
    public const val LAST_EXPORT_KEY: String = "backup_last_export_at"
}
