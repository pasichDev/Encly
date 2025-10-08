package com.pasich.encly.core.security

object SensitiveDataCleaner {
    fun clear(data: ByteArray) {
        data.fill(0)
    }

    fun clear(data: CharArray) {
        data.fill(0.toChar())
    }
}
