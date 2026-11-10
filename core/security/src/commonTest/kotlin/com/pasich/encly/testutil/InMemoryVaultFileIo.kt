package com.pasich.encly.testutil

import com.pasich.encly.core.security.VaultFileIo
import kotlinx.io.IOException

/**
 * A [VaultFileIo] whose file is a byte array: [bytes] is the vault file (null while it does not exist), so a
 * test can start from golden bytes and read back exactly what a write left.
 */
internal class InMemoryVaultFileIo(var bytes: ByteArray? = null) : VaultFileIo {
    override fun exists(): Boolean = bytes != null

    override fun read(): ByteArray = bytes?.copyOf() ?: throw IOException("no vault file")

    override fun deleteTemp() = Unit

    override fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?) {
        beforeRename?.invoke()
        this.bytes = bytes.copyOf()
    }

    override fun delete(): Boolean {
        bytes = null
        return true
    }
}
