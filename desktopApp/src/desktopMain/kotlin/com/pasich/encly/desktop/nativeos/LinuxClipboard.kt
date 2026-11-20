package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.platform.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.awt.datatransfer.Clipboard as AwtClipboard

/**
 * Linux: the text plus the `x-kde-passwordManagerHint` target with the value `secret`, which Klipper and CopyQ read as
 * "a password: do not keep it in the history". Plain AWT has no way to add a target, so a flavor is mapped to that
 * native name through the [SystemFlavorMap] and offered next to the string flavor.
 *
 * It is a hint, not a guarantee: managers that do not look for it (GPaste and others) may still keep the clip, which
 * the 60 second wipe of [com.pasich.encly.desktop.platform.ClipboardWiper] cannot reach. Not verified on a real KDE
 * session by the tests; they check the offered flavors.
 */
internal class LinuxClipboard(private val clipboard: () -> AwtClipboard?) : ClipboardWriter {
    init {
        val flavors = SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap
        flavors?.addUnencodedNativeForFlavor(HINT_FLAVOR, HINT_TARGET)
        flavors?.addFlavorForUnencodedNative(HINT_TARGET, HINT_FLAVOR)
    }

    override fun write(text: String): Boolean {
        val board = clipboard() ?: return false
        return try {
            board.setContents(SecretText(text), null)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** [text] as a string, together with the hint that it is a secret. */
    private class SecretText(private val text: String) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor, HINT_FLAVOR)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor ||
            flavor == HINT_FLAVOR

        @Throws(UnsupportedFlavorException::class, IOException::class)
        override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
            DataFlavor.stringFlavor -> text
            HINT_FLAVOR -> ByteArrayInputStream(HINT_VALUE)
            else -> throw UnsupportedFlavorException(flavor)
        }
    }

    companion object {
        /** The native target the password-manager hint of KDE's clipboard is carried in. */
        const val HINT_TARGET = "x-kde-passwordManagerHint"

        val HINT_FLAVOR = DataFlavor("application/x-kde-passwordmanagerhint;class=java.io.InputStream")
        private val HINT_VALUE = "secret".toByteArray(Charsets.US_ASCII)

        fun create(): LinuxClipboard = LinuxClipboard {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        }
    }
}
