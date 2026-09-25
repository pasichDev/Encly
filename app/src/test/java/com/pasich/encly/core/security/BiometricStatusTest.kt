package com.pasich.encly.core.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricStatusTest {
    @Test
    fun aSensorWithoutAnEnrolledFingerprintIsNotEnrolledRatherThanUnavailable() {
        assertEquals(
            BiometricStatus.NOT_ENROLLED,
            BiometricStatus.fromCanAuthenticate(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun theOtherResultsMapToAvailableOrUnavailable() {
        assertEquals(BiometricStatus.AVAILABLE, BiometricStatus.fromCanAuthenticate(BiometricManager.BIOMETRIC_SUCCESS))
        listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        ).forEach { assertEquals(BiometricStatus.UNAVAILABLE, BiometricStatus.fromCanAuthenticate(it)) }
    }
}
