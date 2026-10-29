package com.pasich.encly.editor.ui.blocks

import java.net.IDN

internal actual fun toAsciiHost(host: String): String? = runCatching { IDN.toASCII(host) }.getOrNull()
