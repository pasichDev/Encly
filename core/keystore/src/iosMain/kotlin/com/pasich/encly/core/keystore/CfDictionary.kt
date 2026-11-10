@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.core.keystore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataAppendBytes
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetMutableBytePtr
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.posix.memcpy
import platform.posix.memset

/**
 * A mutable CoreFoundation dictionary for the Security framework's queries and attribute lists, with the CF objects
 * made for it, released together by [release]. Kotlin maps cannot stand in for it: their keys would be the
 * framework's own `CFStringRef` constants, which are not Objective-C objects.
 *
 * A secret put in with [putBytes] lives in a mutable CFData that [release] zeroizes before it lets go of it.
 */
internal class CfDictionary {
    private val owned = mutableListOf<CFTypeRef>()
    private val secrets = mutableListOf<CFDataRef>()

    val ref: CFDictionaryRef = checkNotNull(
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr),
    )

    /** A constant, or an object someone else owns. */
    fun put(key: CFStringRef?, value: CFTypeRef?): CfDictionary = apply { CFDictionaryAddValue(ref, key, value) }

    fun putBoolean(key: CFStringRef?, value: Boolean): CfDictionary =
        put(key, if (value) kCFBooleanTrue else kCFBooleanFalse)

    fun putString(key: CFStringRef?, value: String): CfDictionary = putOwned(key, CFBridgingRetain(value))

    fun putInt(key: CFStringRef?, value: Int): CfDictionary = memScoped {
        val holder = alloc<IntVar>()
        holder.value = value
        putOwned(key, CFNumberCreate(null, kCFNumberSInt32Type, holder.ptr))
    }

    fun putBytes(key: CFStringRef?, value: ByteArray): CfDictionary {
        val data = newSecretData(value)
        secrets += data
        return put(key, data)
    }

    /** An object the dictionary now shares with the caller; [release] drops the reference made here. */
    fun putOwned(key: CFStringRef?, value: CFTypeRef?): CfDictionary = apply {
        if (value != null) owned += value
        CFDictionaryAddValue(ref, key, value)
    }

    fun release() {
        secrets.forEach(::zeroizeAndRelease)
        owned.forEach { CFRelease(it) }
        CFRelease(ref)
    }
}

/** Runs [block] with a dictionary that is released afterwards. */
internal inline fun <R> withCfDictionary(block: (CfDictionary) -> R): R {
    val dictionary = CfDictionary()
    try {
        return block(dictionary)
    } finally {
        dictionary.release()
    }
}

/** A mutable CFData holding a copy of [value], to be dropped with [zeroizeAndRelease]. */
internal fun newSecretData(value: ByteArray): CFDataRef {
    val data = checkNotNull(CFDataCreateMutable(null, 0))
    if (value.isNotEmpty()) {
        value.usePinned { CFDataAppendBytes(data, it.addressOf(0).reinterpret<UByteVar>(), value.size.convert()) }
    }
    return data
}

/** The bytes of [data] in a fresh array; [data] stays the caller's. */
internal fun cfDataToBytes(data: CFDataRef): ByteArray {
    val length = CFDataGetLength(data).toInt()
    val bytes = ByteArray(length)
    if (length > 0) bytes.usePinned { memcpy(it.addressOf(0), CFDataGetBytePtr(data), length.convert()) }
    return bytes
}

/** Overwrites a CFData made by [newSecretData] with zeros and releases it. */
internal fun zeroizeAndRelease(data: CFDataRef) {
    CFDataGetMutableBytePtr(data)?.let { memset(it, 0, CFDataGetLength(data).convert()) }
    CFRelease(data)
}
