package com.pasich.encly.domain.model

/**
 * Editor text spacing, stored by ordinal. Append new choices without reordering these values:
 * [DEFAULT] (0) is the long-standing look and [COMPACT] (1) the tight one, so a stored 0 or 1
 * keeps its meaning. Declaration order is therefore not display order (the selector shows
 * Compact, Default, Comfortable).
 */
enum class NoteLineSpacing {
    DEFAULT,
    COMPACT,
    COMFORTABLE,
}
