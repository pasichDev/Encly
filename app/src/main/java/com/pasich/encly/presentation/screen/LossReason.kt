package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
import com.pasich.encly.R

/** Why the vault cannot be opened. */
enum class LossReason(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    @param:StringRes val action: Int,
) {
    /** The encrypted data or its keys were changed or lost. */
    DAMAGED(R.string.loss_title, R.string.loss_message, R.string.loss_hold_to_wipe),

    /** The data was written by Encly 1.x, which 2.0 cannot open (no migration). */
    OLDER_VERSION(R.string.legacy_vault_title, R.string.legacy_vault_message, R.string.legacy_vault_hold_to_start),
}
