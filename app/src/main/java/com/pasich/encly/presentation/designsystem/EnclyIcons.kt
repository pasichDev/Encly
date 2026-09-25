package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Encly's line icons: 24 viewBox, stroke 1.75, round caps and joins (SPEC Appendix B, plus the extra
 * glyphs drawn in the same grammar). They carry no colour of their own; `Icon` tints them.
 * The `*Bold` variants are the heavier strokes the canvas uses for the FAB, the add-block button,
 * the wordmark and small done checks.
 */
object EnclyIcons {
    /** A drag handle: two columns of three dots. */
    val Grip: ImageVector by lazy {
        enclyIcon(
            "Grip",
            dots = "M9 6m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M15 6m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
                "M9 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M15 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
                "M9 18m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M15 18m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }

    val Lock: ImageVector by lazy {
        enclyIcon(
            "Lock",
            "M7 11H17A2 2 0 0 1 19 13V18A2 2 0 0 1 17 20H7A2 2 0 0 1 5 18V13A2 2 0 0 1 7 11z",
            "M8 11V8a4 4 0 0 1 8 0v3",
        )
    }

    val LockOpen: ImageVector by lazy {
        enclyIcon(
            "LockOpen",
            "M7 11H17A2 2 0 0 1 19 13V18A2 2 0 0 1 17 20H7A2 2 0 0 1 5 18V13A2 2 0 0 1 7 11z",
            "M8 11V8a4 4 0 0 1 7.5-1.9",
        )
    }

    val Fingerprint: ImageVector by lazy {
        enclyIcon(
            "Fingerprint",
            "M7 10.5a5 5 0 0 1 10 0v1.5",
            "M12 10.5v3.5a7 7 0 0 1-1.8 4.7",
            "M9.5 11v2.5a9 9 0 0 1-1.6 5",
            "M14.5 12v2.5c0 1.8-.4 3.4-1.1 4.8",
            "M4.5 13v-2.5a7.5 7.5 0 0 1 12.8-5.3",
            "M17 16.2c-.2.9-.5 1.8-.9 2.6",
        )
    }

    val Backspace: ImageVector by lazy {
        enclyIcon(
            "Backspace",
            "M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z",
            "M12.5 9.5l5 5M17.5 9.5l-5 5",
        )
    }

    val Search: ImageVector by lazy {
        enclyIcon(
            "Search",
            "M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z",
            "M20 20l-4.2-4.2",
        )
    }

    val Menu: ImageVector by lazy {
        enclyIcon(
            "Menu",
            "M4 7h16M4 12h16M4 17h10",
        )
    }

    val Plus: ImageVector by lazy {
        enclyIcon(
            "Plus",
            "M12 5v14M5 12h14",
        )
    }

    val Check: ImageVector by lazy {
        enclyIcon(
            "Check",
            "M5 12.5l4.5 4.5L19 7.5",
        )
    }

    val Back: ImageVector by lazy {
        enclyIcon(
            "Back",
            "M19 12H5M11 6l-6 6 6 6",
        )
    }

    val More: ImageVector by lazy {
        enclyIcon(
            "More",
            dots = "M10.9 5.5a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0zM10.9 12a1.1 1.1 0 1 0 2.2 0" +
                "a1.1 1.1 0 1 0 -2.2 0zM10.9 18.5a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z",
        )
    }

    val Shield: ImageVector by lazy {
        enclyIcon(
            "Shield",
            "M12 3l7 3v6c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6z",
            "M9 12l2.2 2.2L15.5 10",
        )
    }

    val Privacy: ImageVector by lazy {
        enclyIcon(
            "Privacy",
            "M12 3l7 3v6c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6z",
            dots = "M7.85 12a0.95 0.95 0 1 0 1.9 0a0.95 0.95 0 1 0 -1.9 0zM11.05 12a0.95 0.95 0 1 0 1.9 0" +
                "a0.95 0.95 0 1 0 -1.9 0zM14.25 12a0.95 0.95 0 1 0 1.9 0a0.95 0.95 0 1 0 -1.9 0z",
        )
    }

    val Key: ImageVector by lazy {
        enclyIcon(
            "Key",
            "M4 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
            "M11 12l9-9M16.5 6.5l3 3M14 9l2 2",
        )
    }

    val CloudOff: ImageVector by lazy {
        enclyIcon(
            "CloudOff",
            "M3 3l18 18",
            "M9.2 6.4A6 6 0 0 1 17.6 10a4 4 0 0 1 3 5.6",
            "M6.3 9.3A4.5 4.5 0 0 0 7.5 18H17",
        )
    }

    val UserOff: ImageVector by lazy {
        enclyIcon(
            "UserOff",
            "M8.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z",
            "M5 20c.8-3.6 3.6-5.5 7-5.5 1.3 0 2.5.3 3.6.8",
            "M17 17l4 4M21 17l-4 4",
        )
    }

    val Phone: ImageVector by lazy {
        enclyIcon(
            "Phone",
            "M9.5 2.5H14.5A2.5 2.5 0 0 1 17 5V19A2.5 2.5 0 0 1 14.5 21.5H9.5A2.5 2.5 0 0 1 7 19V5" +
                "A2.5 2.5 0 0 1 9.5 2.5z",
            "M11 18.5h2",
        )
    }

    val File: ImageVector by lazy {
        enclyIcon(
            "File",
            "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z",
            "M14 3v5h5",
            "M10 12.5H14A1 1 0 0 1 15 13.5V16.5A1 1 0 0 1 14 17.5H10A1 1 0 0 1 9 16.5V13.5" +
                "A1 1 0 0 1 10 12.5z",
            "M10.5 12.5v-1.2a1.5 1.5 0 0 1 3 0v1.2",
        )
    }

    val Eye: ImageVector by lazy {
        enclyIcon(
            "Eye",
            "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
        )
    }

    val EyeOff: ImageVector by lazy {
        enclyIcon(
            "EyeOff",
            "M3 3l18 18",
            "M10.6 5.6c.5-.1.9-.1 1.4-.1 6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.6 3.4M6.6 6.7" +
                "C4 8.4 2.5 12 2.5 12s3.5 6.5 9.5 6.5c1.6 0 3-.4 4.2-1",
            "M9.9 9.9a3 3 0 0 0 4.2 4.2",
        )
    }

    val Heading1: ImageVector by lazy {
        enclyIcon(
            "Heading1",
            "M6 5v14M16 5v14M6 12h10",
            "M19 13.5l1.5-1v6.5",
        )
    }

    val Heading2: ImageVector by lazy {
        enclyIcon(
            "Heading2",
            "M6 5v14M16 5v14M6 12h10",
            "M18 13.9a1.5 1.5 0 0 1 3 .4c0 1.5-3 3.2-3 4.7h3",
        )
    }

    val Heading3: ImageVector by lazy {
        enclyIcon(
            "Heading3",
            "M6 5v14M16 5v14M6 12h10",
            "M18 12.5h3l-1.8 2.4a2.1 2.1 0 1 1-1.6 3.7",
        )
    }

    val Heading4: ImageVector by lazy {
        enclyIcon(
            "Heading4",
            "M6 5v14M16 5v14M6 12h10",
            "M20.5 19v-6.5L17.8 17h3.7",
        )
    }

    val ListBullet: ImageVector by lazy {
        enclyIcon(
            "ListBullet",
            "M9.5 6.5h11M9.5 12h11M9.5 17.5h11",
            dots = "M3.9 6.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0zM3.9 12a0.9 0.9 0 1 0 1.8 0" +
                "a0.9 0.9 0 1 0 -1.8 0zM3.9 17.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z",
        )
    }

    val ListNumbered: ImageVector by lazy {
        enclyIcon(
            "ListNumbered",
            "M10 6.5h10.5M10 12h10.5M10 17.5h10.5",
            "M4 5l1.5-1v5",
            "M3.8 13.2a1.4 1.4 0 0 1 2.6.6c0 1-2.6 2.4-2.6 2.4h2.8",
        )
    }

    val Checklist: ImageVector by lazy {
        enclyIcon(
            "Checklist",
            "M5 4.5H8A1.5 1.5 0 0 1 9.5 6V9A1.5 1.5 0 0 1 8 10.5H5A1.5 1.5 0 0 1 3.5 9V6" +
                "A1.5 1.5 0 0 1 5 4.5z",
            "M5 7.6l1.2 1.2 2-2.2",
            "M5 13.5H8A1.5 1.5 0 0 1 9.5 15V18A1.5 1.5 0 0 1 8 19.5H5A1.5 1.5 0 0 1 3.5 18V15" +
                "A1.5 1.5 0 0 1 5 13.5z",
            "M13 7.5h7.5M13 16.5h7.5",
        )
    }

    /** View options (sort order, list or grid): lines narrowing like a funnel. */
    val Filter: ImageVector by lazy {
        enclyIcon(
            "Filter",
            "M4 7h16",
            "M7 12h10",
            "M10 17h4",
        )
    }

    val Keyboard: ImageVector by lazy {
        enclyIcon(
            "Keyboard",
            "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
            "M8 14h8",
            dots = "M6.1 10a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M9.1 10a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M13.1 10a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M16.1 10a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z",
        )
    }

    val KeyboardHide: ImageVector by lazy {
        enclyIcon(
            "KeyboardHide",
            "M5 3h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 11h8",
            "M9 18l3 3 3-3",
            dots = "M6.1 7a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M9.1 7a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M13.1 7a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z" +
                "M16.1 7a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z",
        )
    }

    val Quote: ImageVector by lazy {
        enclyIcon(
            "Quote",
            "M5 18c2.5-.8 4-3 4-6V7H5v5h4",
            "M14 18c2.5-.8 4-3 4-6V7h-4v5h4",
        )
    }

    val Link: ImageVector by lazy {
        enclyIcon(
            "Link",
            "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1",
            "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
        )
    }

    val Separator: ImageVector by lazy {
        enclyIcon(
            "Separator",
            "M3.5 12h3.5M10.25 12h3.5M17 12h3.5",
        )
    }

    val Tag: ImageVector by lazy {
        enclyIcon(
            "Tag",
            "M3.5 12.5V4.5a1 1 0 0 1 1-1h8l8.2 8.2a1 1 0 0 1 0 1.4l-7.6 7.6a1 1 0 0 1-1.4 0z",
            "M6.8 8.2a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z",
        )
    }

    val Globe: ImageVector by lazy {
        enclyIcon(
            "Globe",
            "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z",
            "M3.5 12h17M12 3.5c2.3 2.4 3.5 5.2 3.5 8.5s-1.2 6.1-3.5 8.5c-2.3-2.4-3.5-5.2-3.5-8.5" +
                "S9.7 5.9 12 3.5z",
        )
    }

    val Chevron: ImageVector by lazy {
        enclyIcon(
            "Chevron",
            "M9.5 6l6 6-6 6",
        )
    }

    val ChevronDown: ImageVector by lazy {
        enclyIcon(
            "ChevronDown",
            "M6 9.5l6 6 6-6",
        )
    }

    val Info: ImageVector by lazy {
        enclyIcon(
            "Info",
            "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z",
            "M12 11v5.5",
            "M12 7.6v.2",
        )
    }

    val Alert: ImageVector by lazy {
        enclyIcon(
            "Alert",
            "M12 4l9 16H3z",
            "M12 10v4.5",
            "M12 17.2v.2",
        )
    }

    val Sun: ImageVector by lazy {
        enclyIcon(
            "Sun",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z",
            "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7" +
                "l1.4-1.4M17.3 6.7l1.4-1.4",
        )
    }

    val Moon: ImageVector by lazy {
        enclyIcon(
            "Moon",
            "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z",
        )
    }

    val Device: ImageVector by lazy {
        enclyIcon(
            "Device",
            "M5 4.5H19A2 2 0 0 1 21 6.5V14.5A2 2 0 0 1 19 16.5H5A2 2 0 0 1 3 14.5V6.5A2 2 0 0 1 5 4.5" +
                "z",
            "M8 20h8M12 16.5V20",
        )
    }

    val Restore: ImageVector by lazy {
        enclyIcon(
            "Restore",
            "M4 12a8 8 0 1 0 2.4-5.7",
            "M4 4.5v4h4",
            "M12 8v4.5l3 2",
        )
    }

    val Sliders: ImageVector by lazy {
        enclyIcon(
            "Sliders",
            "M4 7h9M17 7h3M4 17h3M11 17h9",
            "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
            "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
        )
    }

    val Download: ImageVector by lazy {
        enclyIcon(
            "Download",
            "M12 4v11M7 10.5l5 5 5-5M5 20h14",
        )
    }

    val Paper: ImageVector by lazy {
        enclyIcon(
            "Paper",
            "M6 3.5h9l3.5 3.5v13.5H6z",
            "M9 11h6M9 14.5h6M9 18h3.5",
        )
    }

    val Palette: ImageVector by lazy {
        enclyIcon(
            "Palette",
            "M12 3.5a8.5 8.5 0 0 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.3-1-1.6-1-2.7 0-1 .8-1.6 1.8-1.6h2.2" +
                "a3.7 3.7 0 0 0 3.7-3.7C20.5 7 16.7 3.5 12 3.5z",
            "M6.8 11a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M9.5 7.3a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            "M14 7.8a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
        )
    }

    val Trash: ImageVector by lazy {
        enclyIcon(
            "Trash",
            "M4 7h16",
            "M9.5 7V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2",
            "M6 7l.9 12.1a1.5 1.5 0 0 0 1.5 1.4h7.2a1.5 1.5 0 0 0 1.5-1.4L18 7",
            "M10 11v5.5M14 11v5.5",
        )
    }

    val Close: ImageVector by lazy {
        enclyIcon(
            "Close",
            "M6 6l12 12M18 6L6 18",
        )
    }

    val Undo: ImageVector by lazy {
        enclyIcon(
            "Undo",
            "M9 14L4.5 9.5 9 5",
            "M4.5 9.5h10a5 5 0 0 1 0 10H11",
        )
    }

    val Redo: ImageVector by lazy {
        enclyIcon(
            "Redo",
            "M15 14l4.5-4.5L15 5",
            "M19.5 9.5h-10a5 5 0 0 0 0 10H13",
        )
    }

    val ArrowUp: ImageVector by lazy {
        enclyIcon(
            "ArrowUp",
            "M12 19V5M6 11l6-6 6 6",
        )
    }

    val ArrowDown: ImageVector by lazy {
        enclyIcon(
            "ArrowDown",
            "M12 5v14M6 13l6 6 6-6",
        )
    }

    val Sort: ImageVector by lazy {
        enclyIcon(
            "Sort",
            "M8 4.5v15M4.5 8L8 4.5 11.5 8",
            "M16 19.5v-15M12.5 16l3.5 3.5 3.5-3.5",
        )
    }

    val Grid: ImageVector by lazy {
        enclyIcon(
            "Grid",
            "M5 3.5H9A1.5 1.5 0 0 1 10.5 5V9A1.5 1.5 0 0 1 9 10.5H5A1.5 1.5 0 0 1 3.5 9V5" +
                "A1.5 1.5 0 0 1 5 3.5z",
            "M15 3.5H19A1.5 1.5 0 0 1 20.5 5V9A1.5 1.5 0 0 1 19 10.5H15A1.5 1.5 0 0 1 13.5 9V5" +
                "A1.5 1.5 0 0 1 15 3.5z",
            "M5 13.5H9A1.5 1.5 0 0 1 10.5 15V19A1.5 1.5 0 0 1 9 20.5H5A1.5 1.5 0 0 1 3.5 19V15" +
                "A1.5 1.5 0 0 1 5 13.5z",
            "M15 13.5H19A1.5 1.5 0 0 1 20.5 15V19A1.5 1.5 0 0 1 19 20.5H15A1.5 1.5 0 0 1 13.5 19V15" +
                "A1.5 1.5 0 0 1 15 13.5z",
        )
    }

    val ListView: ImageVector by lazy {
        enclyIcon(
            "ListView",
            "M5 4H19A1.5 1.5 0 0 1 20.5 5.5V9.5A1.5 1.5 0 0 1 19 11H5A1.5 1.5 0 0 1 3.5 9.5V5.5" +
                "A1.5 1.5 0 0 1 5 4z",
            "M5 13H19A1.5 1.5 0 0 1 20.5 14.5V18.5A1.5 1.5 0 0 1 19 20H5A1.5 1.5 0 0 1 3.5 18.5V14.5" +
                "A1.5 1.5 0 0 1 5 13z",
        )
    }

    val Copy: ImageVector by lazy {
        enclyIcon(
            "Copy",
            "M10.5 8.5H18.5A2 2 0 0 1 20.5 10.5V18.5A2 2 0 0 1 18.5 20.5H10.5A2 2 0 0 1 8.5 18.5V10.5" +
                "A2 2 0 0 1 10.5 8.5z",
            "M15.5 8.5v-3a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3",
        )
    }

    val Duplicate: ImageVector by lazy {
        enclyIcon(
            "Duplicate",
            "M10.5 8.5H18.5A2 2 0 0 1 20.5 10.5V18.5A2 2 0 0 1 18.5 20.5H10.5A2 2 0 0 1 8.5 18.5V10.5" +
                "A2 2 0 0 1 10.5 8.5z",
            "M15.5 8.5v-3a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3",
            "M14.5 12v5M12 14.5h5",
        )
    }

    val Edit: ImageVector by lazy {
        enclyIcon(
            "Edit",
            "M16.5 4.5a2.1 2.1 0 0 1 3 3L8 19l-4 1 1-4z",
            "M14.5 6.5l3 3",
        )
    }

    val External: ImageVector by lazy {
        enclyIcon(
            "External",
            "M14 4.5h5.5V10",
            "M19.5 4.5L11 13",
            "M17 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 4 18.5v-10A1.5 1.5 0 0 1 5.5 7H10",
        )
    }

    val Calendar: ImageVector by lazy {
        enclyIcon(
            "Calendar",
            "M5.5 5H18.5A2 2 0 0 1 20.5 7V18.5A2 2 0 0 1 18.5 20.5H5.5A2 2 0 0 1 3.5 18.5V7" +
                "A2 2 0 0 1 5.5 5z",
            "M3.5 10h17",
            "M8 3v4M16 3v4",
        )
    }

    val CheckCircle: ImageVector by lazy {
        enclyIcon(
            "CheckCircle",
            "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z",
            "M8.5 12.2l2.4 2.4 4.6-4.8",
        )
    }

    val Comment: ImageVector by lazy {
        enclyIcon(
            "Comment",
            "M20.5 15a2 2 0 0 1-2 2H8l-4.5 3.5V5.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2z",
            "M8 8.5h8M8 12h5",
        )
    }

    val Type: ImageVector by lazy {
        enclyIcon(
            "Type",
            "M5 7V5h14v2",
            "M12 5v14",
            "M9.5 19h5",
        )
    }

    val Help: ImageVector by lazy {
        enclyIcon(
            "Help",
            "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z",
            "M9.6 9.4a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.2-2.4 3.7",
            "M12 17.2v.2",
        )
    }

    val Coffee: ImageVector by lazy {
        enclyIcon(
            "Coffee",
            "M4.5 9h11v5.5a4.5 4.5 0 0 1-4.5 4.5H9a4.5 4.5 0 0 1-4.5-4.5z",
            "M15.5 10.5h1.5a2.5 2.5 0 0 1 0 5h-1.5",
            "M8 3.5v2.5M12 3.5v2.5",
        )
    }

    val Heart: ImageVector by lazy {
        enclyIcon(
            "Heart",
            "M12 19.5s-8-4.6-8-10a4.2 4.2 0 0 1 8-1.8 4.2 4.2 0 0 1 8 1.8c0 5.4-8 10-8 10z",
        )
    }

    val Star: ImageVector by lazy {
        enclyIcon(
            "Star",
            "M12.00 3.80L14.29 9.44L20.37 9.88L15.71 13.81L17.17 19.72L12.00 16.50L6.83 19.72" +
                "L8.29 13.81L3.63 9.88L9.71 9.44z",
        )
    }

    val Mail: ImageVector by lazy {
        enclyIcon(
            "Mail",
            "M5.5 5.5H18.5A2 2 0 0 1 20.5 7.5V16.5A2 2 0 0 1 18.5 18.5H5.5A2 2 0 0 1 3.5 16.5V7.5" +
                "A2 2 0 0 1 5.5 5.5z",
            "M4 7.5l8 5.5 8-5.5",
        )
    }

    val Send: ImageVector by lazy {
        enclyIcon(
            "Send",
            "M4.5 4.5L20.5 12 4.5 19.5 7 12z",
            "M7 12h6",
        )
    }

    val PlusBold: ImageVector by lazy {
        enclyIcon(
            "PlusBold",
            "M12 5v14M5 12h14",
            strokeWidth = 2.0f,
        )
    }

    val CheckBold: ImageVector by lazy {
        enclyIcon(
            "CheckBold",
            "M5 12.5l4.5 4.5L19 7.5",
            strokeWidth = 2.5f,
        )
    }
}

private const val STROKE = 1.75f
private val Ink = SolidColor(Color.Black)

private fun enclyIcon(
    name: String,
    vararg paths: String,
    strokeWidth: Float = STROKE,
    dots: String? = null,
): ImageVector = ImageVector.Builder(
    name = "Encly.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    paths.forEach { data ->
        addPath(
            pathData = addPathNodes(data),
            stroke = Ink,
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    dots?.let { addPath(pathData = addPathNodes(it), fill = Ink) }
}.build()
