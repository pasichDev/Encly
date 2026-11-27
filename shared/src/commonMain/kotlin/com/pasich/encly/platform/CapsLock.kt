package com.pasich.encly.platform

/**
 * Whether Caps Lock is on right now, or null where the host cannot tell (a touch keyboard, a system that does not
 * report it). The passphrase field hints at it, because a masked field shows nothing of what the wrong case did.
 */
internal expect fun capsLockOn(): Boolean?
