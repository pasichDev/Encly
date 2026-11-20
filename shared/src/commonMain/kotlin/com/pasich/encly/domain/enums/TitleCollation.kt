package com.pasich.encly.domain.enums

/**
 * Compares two note titles the way a reader of the current language expects: case-insensitive and
 * with the language's own alphabet order ("Ä" next to "A" in German, "Ї" after "І" in Ukrainian).
 * Android and desktop use a `java.text.Collator` of the default locale (the app's language, which
 * AppCompat and the system keep as the default); iOS uses the system's locale-aware case-insensitive
 * comparison. A new instance per call: a collator is not safe to share between threads.
 */
internal expect fun titleCollator(): Comparator<String>
