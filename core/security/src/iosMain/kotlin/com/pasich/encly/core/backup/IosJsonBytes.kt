@file:OptIn(UnsafeIoApi::class, ExperimentalSerializationApi::class)

package com.pasich.encly.core.backup

import kotlinx.io.Buffer
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink

/*
 * The iOS side of JsonBytes.kt. There is no java.io here, and a kotlinx-io Buffer hands its segments back to a
 * pool without clearing them, so the plaintext is zeroized segment by segment before the buffer is dropped:
 * UnsafeBufferOperations gives the segment arrays themselves. What the serializer keeps in its own scratch
 * arrays is out of reach, as it is with encodeToStream on the JVM.
 */

internal actual fun <T> Json.encodeToWipedBytes(serializer: SerializationStrategy<T>, value: T): ByteArray {
    val buffer = Buffer()
    try {
        encodeToSink(serializer, value, buffer)
        return buffer.drainAndWipe()
    } finally {
        buffer.wipe()
    }
}

internal actual fun <T> Json.decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
    val buffer = Buffer()
    try {
        buffer.write(bytes)
        // A peek reads the segments in place and consumes nothing, so they are still here to be wiped.
        return decodeFromSource(deserializer, buffer.peek())
    } finally {
        buffer.wipe()
    }
}

/** Everything in the buffer as a byte array, each segment zeroized as it is read. */
private fun Buffer.drainAndWipe(): ByteArray {
    val out = ByteArray(size.toInt())
    var written = 0
    while (!exhausted()) {
        UnsafeBufferOperations.readFromHead(this) { bytes, start, end ->
            bytes.copyInto(out, written, start, end)
            written += end - start
            bytes.fill(0, start, end)
            end - start
        }
    }
    return out
}

/** Zeroizes what is left in the buffer and empties it. */
private fun Buffer.wipe() {
    while (!exhausted()) {
        UnsafeBufferOperations.readFromHead(this) { bytes, start, end ->
            bytes.fill(0, start, end)
            end - start
        }
    }
}
