package com.pasich.encly.testutil

import org.mockito.ArgumentMatchers

/*
 * Mockito matchers return null, which Kotlin rejects for non-null parameters. These register
 * the matcher and hand Kotlin a harmless placeholder instead.
 */

internal fun anyByteArray(): ByteArray = ArgumentMatchers.any(ByteArray::class.java) ?: ByteArray(0)

internal fun <T : Any> eqValue(value: T): T = ArgumentMatchers.eq(value) ?: value

internal fun anyCharArray(): CharArray = ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)
