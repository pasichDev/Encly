package com.pasich.encly.testutil

import org.mockito.ArgumentMatchers
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/*
 * Mockito matchers return null, which Kotlin rejects for non-null parameters. These register
 * the matcher and hand Kotlin a harmless placeholder instead.
 */

internal fun anyByteArray(): ByteArray = ArgumentMatchers.any(ByteArray::class.java) ?: ByteArray(0)

internal fun sameBytes(value: ByteArray): ByteArray = ArgumentMatchers.same(value) ?: value

internal fun anySecretKey(): SecretKey =
    ArgumentMatchers.any(SecretKey::class.java) ?: SecretKeySpec(ByteArray(1), "AES")

internal fun anyString(): String = ArgumentMatchers.anyString() ?: ""

internal fun captureBytes(captor: org.mockito.ArgumentCaptor<ByteArray>): ByteArray = captor.capture() ?: ByteArray(0)

internal fun <T : Any> eqValue(value: T): T = ArgumentMatchers.eq(value) ?: value

internal fun anyCharArray(): CharArray = ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)
