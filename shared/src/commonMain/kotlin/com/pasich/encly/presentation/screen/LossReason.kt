package com.pasich.encly.presentation.screen

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.legacy_vault_hold_to_start
import com.pasich.encly.core.resources.legacy_vault_message
import com.pasich.encly.core.resources.legacy_vault_title
import com.pasich.encly.core.resources.loss_hold_to_wipe
import com.pasich.encly.core.resources.loss_message
import com.pasich.encly.core.resources.loss_title
import org.jetbrains.compose.resources.StringResource

/** Why the vault cannot be opened. */
enum class LossReason(val title: StringResource, val message: StringResource, val action: StringResource) {
    /** The encrypted data or its keys were changed or lost. */
    DAMAGED(Res.string.loss_title, Res.string.loss_message, Res.string.loss_hold_to_wipe),

    /** The data was written by Encly 1.x, which 2.0 cannot open (no migration). */
    OLDER_VERSION(
        Res.string.legacy_vault_title,
        Res.string.legacy_vault_message,
        Res.string.legacy_vault_hold_to_start,
    ),
}
