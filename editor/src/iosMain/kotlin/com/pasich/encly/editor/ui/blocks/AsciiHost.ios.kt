package com.pasich.encly.editor.ui.blocks

internal actual fun toAsciiHost(host: String): String? = Nameprep.prepare(host)?.let(Punycode::toAsciiHost)
