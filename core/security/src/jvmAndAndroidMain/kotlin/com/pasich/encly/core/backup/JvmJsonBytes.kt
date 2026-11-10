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
 * The JVM and Android side of JsonBytes.kt: java.io streams, because the output buffer of a
 * ByteArrayOutputStream can be zeroized, which a kotlinx-io buffer cannot be yet.
 */

internal actual fun <T> Json.encodeToWipedBytes(serializer: SerializationStrategy<T>, value: T): ByteArray {
    val out = WipeableByteArrayOutputStream()
    return try {
        encodeToStream(serializer, value, out)
        out.toByteArray()
    } finally {
        out.wipe()
    }
}

internal actual fun <T> Json.decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
    decodeFromStream(deserializer, ByteArrayInputStream(bytes))

/** Lets the serializer's internal buffer be zeroized instead of left for the GC. */
private class WipeableByteArrayOutputStream : ByteArrayOutputStream() {
    fun wipe() {
        SensitiveDataCleaner.clear(buf)
        reset()
    }
}
