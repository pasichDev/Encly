@file:OptIn(ExperimentalSerializationApi::class)

package com.pasich.encly.core.backup

import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/*
 * The Android/JVM side of the backup payload's JSON: the plaintext of the whole vault goes through
 * a byte array that is zeroized afterwards, with no String copy. kotlinx-io buffers return their
 * segments to a pool without clearing them, so this stays on java.io streams until a platform
 * buffer that can be wiped exists. A Kotlin Multiplatform build would turn these into expect/actual.
 */

/** UTF-8 JSON of [value]. The result is plaintext: the caller wipes it. The serializer's own buffer is wiped here. */
internal fun <T> Json.encodeToWipedBytes(serializer: SerializationStrategy<T>, value: T): ByteArray {
    val out = WipeableByteArrayOutputStream()
    return try {
        encodeToStream(serializer, value, out)
        out.toByteArray()
    } finally {
        out.wipe()
    }
}

/** The value in the UTF-8 JSON [bytes]; the caller keeps (and wipes) them. */
internal fun <T> Json.decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
    decodeFromStream(deserializer, ByteArrayInputStream(bytes))

/** Lets the serializer's internal buffer be zeroized instead of left for the GC. */
private class WipeableByteArrayOutputStream : ByteArrayOutputStream() {
    fun wipe() {
        SensitiveDataCleaner.clear(buf)
        reset()
    }
}
