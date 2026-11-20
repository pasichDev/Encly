package com.pasich.encly.domain.enums

import platform.Foundation.NSString
import platform.Foundation.localizedCaseInsensitiveCompare

internal actual fun titleCollator(): Comparator<String> =
    Comparator { a, b -> (a as NSString).localizedCaseInsensitiveCompare(b).toInt() }
