package com.pasich.encly.core.crypto

/**
 * Expected bytes for the tests of the crypto API: RFC 5869 (HKDF), RFC 7914 (PBKDF2), the McGrew and Viega
 * AES-GCM test cases, FIPS 180 (SHA-256) and the PBKDF2 stretch of the app's PIN parameters, computed with
 * the platform JCA. :core:security pins the formats built on them.
 */
internal object GoldenVectors {
    val rfc5869Tc1Okm32 = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf")

    val rfc5869Tc2Okm32 = hex("b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c")

    val rfc7914C1 = hex("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc")

    val rfc7914C80000 = hex("4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56")

    val gcmTc15 = hex(
        "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838" +
            "c5f61e6393ba7a0abcc9f662898015adb094dac5d93471bdec1a502270e3cc6c",
    )

    val gcmTc16 = hex(
        "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838" +
            "c5f61e6393ba7a0abcc9f66276fc6ece0f4e1768cddf8853bb2d551b",
    )

    val sha256Abc = hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    val pinSw = hex("4f5705502cd1802172fbf3eecd6a1b14fb647976595714bce7c922b66c30a4e7")

    fun hex(text: String): ByteArray = ByteArray(text.length / 2) {
        text.substring(2 * it, 2 * it + 2).toInt(16).toByte()
    }
}
