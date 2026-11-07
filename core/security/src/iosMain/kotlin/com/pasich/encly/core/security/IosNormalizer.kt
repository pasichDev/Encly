@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

/*
 * The iOS side of Passwords.normalize: Foundation's canonical composition. It works on a String, which cannot
 * be wiped; Passwords.normalize only gets here for text outside plain Latin.
 */

internal actual fun normalizeNfc(text: CharArray): CharArray =
    (text.concatToString() as NSString).precomposedStringWithCanonicalMapping.toCharArray()
