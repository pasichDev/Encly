package com.pasich.encly.core.security

/**
 * Expected bytes for the golden tests, computed once with the platform JCA (`Cipher`, `Mac`,
 * `SecretKeyFactory`, `MessageDigest`) over the fixed inputs described in [CryptoGoldenTest],
 * and cross-checked against RFC 5869 (HKDF), RFC 7914 (PBKDF2) and the McGrew and Viega AES-GCM
 * test cases. They pin the on-disk formats: a change here is a change to what users' vaults
 * and backups hold.
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

    val pinSlot = hex(
        "03000306090c0f1215181b1e2124272a2d202122232425262728292a2b58a9c03c53e5da37e57c6d6bd7b79ab45c8f05" +
            "ff14a64ca347f3ca7ad54a01253bca20ffa570bb18224cd4725d9b1e73",
    )

    val wipeSlot = hex(
        "03202122232425262728292a2b27b18aa6b5910246745d4d3702abbff4370bbe364d253f08b6fdf78894aa997d676544" +
            "c50176745e3ae5c0bc24d2f2a8",
    )

    val duressSlot = hex(
        "03202122232425262728292a2b84ee003088b65199cf851f23dbd85498733e690ab0a0ab22184d78fbcd9ab2ca8d5b9e" +
            "a99ef53ca2242d4856334771a0",
    )

    val decoyKeyKek = hex("69c119168825a610a9652135ed080e5b8ed8f39462046af535277a11ebcb746d")

    val decoyKeyActive = hex(
        "03202122232425262728292a2b76adf108921fa1ecdf5e950c998d88f08aa772815a5ec6a659b485f0c84c1485f93486" +
            "768d6b641d1dc5fa212846404f",
    )

    val decoyKeyOff = hex(
        "03202122232425262728292a2b76adf108921fa1ecdf5e950c998d88f08aa772815a5ec6a659b485f0c84c148579c57f" +
            "ed2d3ab2273355da6fa8f81369",
    )

    val seedHash = hex("c557eec878dfd852ba3f88087c4f350f09c55537ab5e549c3cd14320ec3cef38")

    val backupRoot = hex("1a33cffc42964db0c0dd84e985bc0d0acfd3264809e289e8a85700cc38cfeb63")

    val recoveryKek = hex("2833d39a5f37b2e447c0b94b87e3119db7ecf45e8998896871db06f70f5c3bb2")

    val recoverySlot = hex(
        "202122232425262728292a2bcb388f3b03d75bca17e4901a28acf127c14f78f1c8f68f1423dfd8b9090e9836123328c4" +
            "24d8ac31a180e35e1ff24e3a",
    )

    val backupSlotKek = hex("04ffdfc969efc8042f6fc218f3095d990c0516fdbf56f0815cf353299950f7ab")

    val backupSlot = hex(
        "202122232425262728292a2b2cf5957fd9db925e7adaf2368792a5252840aa956091122bb6d12694ffa4fee167d4a1f7" +
            "4ba4fb1c6c1b8a48f70c89d8",
    )

    val backupFileKey = hex("76a68f389c49864c6bb7ac38a71665ec305f011547c63c5f0b73e3d718a58864")

    val backupFile = hex(
        "454e434c5942414b00010120101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f0c606162" +
            "636465666768696a6b0000002e675ac6b816f6b9eaecd39c3160cd8444d40d892d26ad4ff9254b356403bbfb6d1dda04" +
            "a7ef6bcf8bba1903b2e9f3",
    )

    val pinSw = hex("4f5705502cd1802172fbf3eecd6a1b14fb647976595714bce7c922b66c30a4e7")

    val pinHw = hex("8cd97cc6eb415406f66958ab3716a0716f1095f80f2ae50b1c3cf75c5c0eb82c")

    val pinKek = hex("83935b5bf598bc380ce3109b8a7eb79a20461489842956d7047884c25aecbc8e")

    val pinWipeKek = hex("09657ab46e417017d30f5027cd867f73c98775ac04f53bd21b40dc93373693aa")

    val pinDecoyKek = hex("7ca0eaeecd58955a53e89e88ceab672071b444b6cfdef8a599560c320178462d")

    val chainWipeSlot = hex(
        "03202122232425262728292a2b0c738a6beff788e0460581a9d1a9abcb80c3e48bc78483d626077ef97735281fc75775" +
            "2ee29415e563911fc43810d57b",
    )

    val chainPinSlot = hex(
        "03000306090c0f1215181b1e2124272a2d202122232425262728292a2b2897f118c5ddd3b16fa2d1905004f668dc1227" +
            "c936b5f1670082bd62540203934aa663fa9e357ebf336f908ccf9395a8",
    )

    val chainDuressSlot = hex(
        "03202122232425262728292a2be20e7f8a806e9355535f9c4dba72b1c258b2973442f6b5359c141b1f81868d2d17d38e" +
            "fddb368becdab848194f27ec96",
    )

    val storeFile = hex(
        "454e434c595653330000000200016100000003010203000262630000000058f24fb12b5c0a411161cd12ebdb9fdbf492" +
            "19ef0aee2e78afa6e5562e370519",
    )

    fun hex(text: String): ByteArray = ByteArray(text.length / 2) {
        text.substring(2 * it, 2 * it + 2).toInt(16).toByte()
    }
}
