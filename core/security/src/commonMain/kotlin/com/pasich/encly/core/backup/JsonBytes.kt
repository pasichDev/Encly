package com.pasich.encly.core.backup

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/*
 * The backup payload's JSON as bytes. The plaintext of the whole vault goes through a byte array that is
 * zeroized afterwards, with no String copy. kotlinx-io buffers return their segments to a pool without
 * clearing them, so each platform streams through a buffer it can wipe (the JVM one is JvmJsonBytes.kt).
 */

/** UTF-8 JSON of [value]. The result is plaintext: the caller wipes it. The serializer's own buffer is wiped here. */
internal expect fun <T> Json.encodeToWipedBytes(serializer: SerializationStrategy<T>, value: T): ByteArray

/** The value in the UTF-8 JSON [bytes]; the caller keeps (and wipes) them. */
internal expect fun <T> Json.decodeFromBytes(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T
