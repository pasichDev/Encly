package com.pasich.encly.domain.enums

import java.text.Collator
import java.util.Locale

// SECONDARY: letters differ by base letter and accent, not by case.
internal actual fun titleCollator(): Comparator<String> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    return Comparator { a, b -> collator.compare(a, b) }
}
