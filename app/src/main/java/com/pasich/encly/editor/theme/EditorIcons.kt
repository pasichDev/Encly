package com.pasich.encly.editor.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icons the editor draws: the block kinds of the slash picker, the link card's globe, the
 * "Add link" plus, a checked box's tick and the rows of the block sheets. They carry no colour of
 * their own; `Icon` tints them. The defaults are 24 dp line icons (viewBox 24, stroke 1.75, round
 * caps and joins); a host may pass its own set.
 */
@Immutable
@Suppress("LongParameterList") // One icon per use.
class EditorIcons(
    val text: ImageVector = DefaultIcons.Type,
    val heading1: ImageVector = DefaultIcons.Heading1,
    val heading2: ImageVector = DefaultIcons.Heading2,
    val heading3: ImageVector = DefaultIcons.Heading3,
    val heading4: ImageVector = DefaultIcons.Heading4,
    val checklist: ImageVector = DefaultIcons.Checklist,
    val bulletList: ImageVector = DefaultIcons.ListBullet,
    val numberedList: ImageVector = DefaultIcons.ListNumbered,
    val quote: ImageVector = DefaultIcons.Quote,
    val link: ImageVector = DefaultIcons.Link,
    val separator: ImageVector = DefaultIcons.Separator,
    val globe: ImageVector = DefaultIcons.Globe,
    val plus: ImageVector = DefaultIcons.Plus,
    val check: ImageVector = DefaultIcons.Check,
    val external: ImageVector = DefaultIcons.External,
    val copy: ImageVector = DefaultIcons.Copy,
    val edit: ImageVector = DefaultIcons.Edit,
    val arrowUp: ImageVector = DefaultIcons.ArrowUp,
    val arrowDown: ImageVector = DefaultIcons.ArrowDown,
    val duplicate: ImageVector = DefaultIcons.Duplicate,
    val trash: ImageVector = DefaultIcons.Trash,
)

private const val STROKE = 1.75f
private val Ink = SolidColor(Color.Black)

@Suppress("LongMethod") // The path data of every default icon, in one place.
private object DefaultIcons {
    val Type = icon("Type", "M5 7V5h14v2", "M12 5v14", "M9.5 19h5")

    val Heading1 = icon("Heading1", "M6 5v14M16 5v14M6 12h10", "M19 13.5l1.5-1v6.5")

    val Heading2 = icon("Heading2", "M6 5v14M16 5v14M6 12h10", "M18 13.9a1.5 1.5 0 0 1 3 .4c0 1.5-3 3.2-3 4.7h3")

    val Heading3 = icon("Heading3", "M6 5v14M16 5v14M6 12h10", "M18 12.5h3l-1.8 2.4a2.1 2.1 0 1 1-1.6 3.7")

    val Heading4 = icon("Heading4", "M6 5v14M16 5v14M6 12h10", "M20.5 19v-6.5L17.8 17h3.7")

    val ListBullet = icon(
        "ListBullet",
        "M9.5 6.5h11M9.5 12h11M9.5 17.5h11",
        dots = "M3.9 6.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0zM3.9 12a0.9 0.9 0 1 0 1.8 0" +
            "a0.9 0.9 0 1 0 -1.8 0zM3.9 17.5a0.9 0.9 0 1 0 1.8 0a0.9 0.9 0 1 0 -1.8 0z",
    )

    val ListNumbered = icon(
        "ListNumbered",
        "M10 6.5h10.5M10 12h10.5M10 17.5h10.5",
        "M4 5l1.5-1v5",
        "M3.8 13.2a1.4 1.4 0 0 1 2.6.6c0 1-2.6 2.4-2.6 2.4h2.8",
    )

    val Checklist = icon(
        "Checklist",
        "M5 4.5H8A1.5 1.5 0 0 1 9.5 6V9A1.5 1.5 0 0 1 8 10.5H5A1.5 1.5 0 0 1 3.5 9V6A1.5 1.5 0 0 1 5 4.5z",
        "M5 7.6l1.2 1.2 2-2.2",
        "M5 13.5H8A1.5 1.5 0 0 1 9.5 15V18A1.5 1.5 0 0 1 8 19.5H5A1.5 1.5 0 0 1 3.5 18V15A1.5 1.5 0 0 1 5 13.5z",
        "M13 7.5h7.5M13 16.5h7.5",
    )

    val Quote = icon("Quote", "M5 18c2.5-.8 4-3 4-6V7H5v5h4", "M14 18c2.5-.8 4-3 4-6V7h-4v5h4")

    val Link = icon(
        "Link",
        "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1",
        "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
    )

    val Separator = icon("Separator", "M3.5 12h3.5M10.25 12h3.5M17 12h3.5")

    val Globe = icon(
        "Globe",
        "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z",
        "M3.5 12h17M12 3.5c2.3 2.4 3.5 5.2 3.5 8.5s-1.2 6.1-3.5 8.5c-2.3-2.4-3.5-5.2-3.5-8.5S9.7 5.9 12 3.5z",
    )

    val Plus = icon("Plus", "M12 5v14M5 12h14")

    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")

    val External = icon(
        "External",
        "M14 4.5h5.5V10",
        "M19.5 4.5L11 13",
        "M17 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 4 18.5v-10A1.5 1.5 0 0 1 5.5 7H10",
    )

    val Copy = icon(
        "Copy",
        "M10.5 8.5H18.5A2 2 0 0 1 20.5 10.5V18.5A2 2 0 0 1 18.5 20.5H10.5A2 2 0 0 1 8.5 18.5V10.5A2 2 0 0 1 10.5 8.5z",
        "M15.5 8.5v-3a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3",
    )

    val Edit = icon("Edit", "M16.5 4.5a2.1 2.1 0 0 1 3 3L8 19l-4 1 1-4z", "M14.5 6.5l3 3")

    val ArrowUp = icon("ArrowUp", "M12 19V5M6 11l6-6 6 6")

    val ArrowDown = icon("ArrowDown", "M12 5v14M6 13l6 6 6-6")

    val Duplicate = icon(
        "Duplicate",
        "M10.5 8.5H18.5A2 2 0 0 1 20.5 10.5V18.5A2 2 0 0 1 18.5 20.5H10.5A2 2 0 0 1 8.5 18.5V10.5A2 2 0 0 1 10.5 8.5z",
        "M15.5 8.5v-3a2 2 0 0 0-2-2h-8a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3",
        "M14.5 12v5M12 14.5h5",
    )

    val Trash = icon(
        "Trash",
        "M4 7h16",
        "M9.5 7V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2",
        "M6 7l.9 12.1a1.5 1.5 0 0 0 1.5 1.4h7.2a1.5 1.5 0 0 0 1.5-1.4L18 7",
        "M10 11v5.5M14 11v5.5",
    )

    private fun icon(name: String, vararg paths: String, dots: String? = null): ImageVector = ImageVector.Builder(
        name = "Editor.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = Ink,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        dots?.let { addPath(pathData = addPathNodes(it), fill = Ink) }
    }.build()
}
