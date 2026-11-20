@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.crypto

import com.pasich.encly.core.crypto.swift.encly_p256_point_from_x
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned

/**
 * Decoding of NIST P-256 curve points, for the one caller that needs a point nobody knows the discrete log of
 * (the iOS PIN factor). CryptoKit does the work through EnclyAesGcm.swift: the square root and the on-curve
 * check are Apple's, not ours.
 */
public object P256Points {
    public const val X_BYTES: Int = 32
    public const val POINT_BYTES: Int = 65

    /**
     * The point with abscissa [x] (32 bytes, big endian) as ANSI X9.63 `04 ‖ X ‖ Y`, or null when no point of the
     * curve has that x (about half of all values) or [x] is not below the field prime. Of the two points with
     * this x, CryptoKit picks one; either gives the same ECDH x coordinate.
     */
    public fun fromX(x: ByteArray): ByteArray? {
        require(x.size == X_BYTES) { "x must be $X_BYTES bytes" }
        val point = ByteArray(POINT_BYTES)
        val status = x.usePinned { xPinned ->
            point.usePinned {
                encly_p256_point_from_x(
                    xPinned.addressOf(0).reinterpret(),
                    X_BYTES.convert(),
                    it.addressOf(0).reinterpret(),
                )
            }
        }
        return if (status == 0) point else null
    }
}
