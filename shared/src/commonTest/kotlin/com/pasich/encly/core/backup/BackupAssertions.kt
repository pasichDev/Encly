package com.pasich.encly.core.backup

import org.junit.Assert.fail
import kotlin.test.assertEquals

/** Fails unless [block] throws a [BackupException] with the error [expected]. */
internal fun assertError(expected: BackupError, block: () -> Unit) {
    try {
        block()
        fail("expected BackupException($expected)")
    } catch (e: BackupException) {
        assertEquals(expected, e.error)
    }
}
