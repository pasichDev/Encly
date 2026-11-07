package com.pasich.encly.core.keystore

/**
 * Which failures of `CryptUnprotectData` mean the blob can never be opened again, and which may pass.
 *
 * JNA's `Win32Exception` reports an HRESULT: a plain Win32 code such as `ERROR_INVALID_DATA` (13) arrives as
 * `0x8007000D` (the Win32 facility with the high bit), while the `NTE_*` codes of the crypto API are HRESULTs already.
 * Both spellings are told apart from a failure that may be temporary (the profile is not loaded yet, the service is
 * busy, memory is short), which must not make the PIN look lost: a lost key sends the user to the recovery phrase.
 */
internal object DpapiErrors {
    private const val FACILITY_WIN32_MASK = 0xFFFF0000.toInt()
    private const val HRESULT_WIN32 = 0x80070000.toInt()
    private const val WIN32_CODE_MASK = 0xFFFF

    /** The blob is damaged: ERROR_INVALID_DATA. */
    const val ERROR_INVALID_DATA = 13

    /** The blob is damaged: NTE_BAD_DATA. */
    const val NTE_BAD_DATA = 0x80090005.toInt()

    /** The blob or its key is not usable: NTE_BAD_KEY. */
    const val NTE_BAD_KEY = 0x80090003.toInt()

    /** The master key is in a state that cannot decrypt, e.g. after a password reset: NTE_BAD_KEY_STATE. */
    const val NTE_BAD_KEY_STATE = 0x8009000B.toInt()

    /** The master key the blob names is not in this profile: NTE_NO_KEY. */
    const val NTE_NO_KEY = 0x8009000D.toInt()

    /** The blob was made by a version this system cannot read: NTE_BAD_VER. */
    const val NTE_BAD_VER = 0x80090006.toInt()

    private val FINAL = setOf(ERROR_INVALID_DATA, NTE_BAD_DATA, NTE_BAD_KEY, NTE_BAD_KEY_STATE, NTE_NO_KEY, NTE_BAD_VER)

    /** [code] as the Win32 error it wraps (`0x8007xxxx` becomes `xxxx`), or unchanged when it is another HRESULT. */
    fun win32Code(code: Int): Int =
        if (code and FACILITY_WIN32_MASK == HRESULT_WIN32) code and WIN32_CODE_MASK else code

    /** True when the failure [code] (a Win32 error or an HRESULT) says the blob can never be opened. */
    fun isFinal(code: Int): Boolean = win32Code(code) in FINAL

    /** [code] for a message: hex for an HRESULT, the number for a plain Win32 error. */
    fun describe(code: Int): String = "0x" + Integer.toHexString(code).uppercase()
}
