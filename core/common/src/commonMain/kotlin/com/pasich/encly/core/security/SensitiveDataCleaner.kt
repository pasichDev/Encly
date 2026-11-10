package com.pasich.encly.core.security

public object SensitiveDataCleaner {
    public fun clear(data: ByteArray) {
        data.fill(0)
    }

    public fun clear(data: CharArray) {
        data.fill(0.toChar())
    }
}
