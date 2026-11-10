package com.pasich.encly.domain.model

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.state.HEADING_TOOL
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A block tool the editor toolbar can offer. The names are stored (and backed up), so they must not change.
 * [type] is the block the tool applies, [headingLevel] the level it names ("Heading 3"); [HEADING] is the
 * plain "Heading" tool, which makes an H2 and shows active for every level.
 */
enum class ToolbarTool(val type: BlockType, val headingLevel: Int? = null) {
    HEADING(HEADING_TOOL),
    LIST_CHECK(BlockType.LIST_CHECK),
    LIST_BULLET(BlockType.LIST_BULLET),
    LIST_NUMBER(BlockType.LIST_NUMBER),
    QUOTE(BlockType.QUOTE),
    CODE(BlockType.CODE),
    TABLE(BlockType.TABLE),
    TEXT(BlockType.TEXT),
    H1(BlockType.H1, headingLevel = 1),
    H2(BlockType.H2, headingLevel = 2),
    H3(BlockType.H3, headingLevel = 3),
    H4(BlockType.H4, headingLevel = 4),
    LINK(BlockType.LINK),
    SEPARATOR(BlockType.SEPARATOR),
}

/** Where a tool is: in the toolbar's own row, behind the "more" chevron, or not offered. */
enum class ToolbarSection { MAIN, MORE, HIDDEN }

/**
 * Which tools the editor toolbar shows ([main]), which sit behind its "more" chevron ([more]), and which the user
 * has put away ([hidden]), each in order. Every [ToolbarTool] is in exactly one of them. Stored as
 * `main:A,B;more:C;hidden:D`.
 */
data class ToolbarLayout(
    val main: List<ToolbarTool>,
    val more: List<ToolbarTool>,
    val hidden: List<ToolbarTool> = emptyList(),
) {
    /** The tools of [section], in order. */
    fun tools(section: ToolbarSection): List<ToolbarTool> = when (section) {
        ToolbarSection.MAIN -> main
        ToolbarSection.MORE -> more
        ToolbarSection.HIDDEN -> hidden
    }

    /** Where [tool] is. */
    fun sectionOf(tool: ToolbarTool): ToolbarSection = when (tool) {
        in main -> ToolbarSection.MAIN
        in more -> ToolbarSection.MORE
        else -> ToolbarSection.HIDDEN
    }

    /** This layout with [tool] hidden, or, if it is hidden, back in the section it has by default. */
    fun toggled(tool: ToolbarTool): ToolbarLayout = when {
        tool in hidden && tool in DEFAULT.main -> copy(main = main + tool, hidden = hidden - tool)
        tool in hidden -> copy(more = more + tool, hidden = hidden - tool)
        else -> ToolbarLayout(main - tool, more - tool, hidden + tool)
    }

    /**
     * This layout with [tool] moved [steps] places down the list of all tools (up for a negative number). The
     * list is the main tools, the "more" divider, the more tools, the "hidden" divider, the hidden tools; the
     * tool takes the section of the divider it ends up after.
     */
    fun moved(tool: ToolbarTool, steps: Int): ToolbarLayout {
        val flat: List<Any> = main + MORE_DIVIDER + more + HIDDEN_DIVIDER + hidden
        val without = flat.filter { it != tool }
        val target = (flat.indexOf(tool) + steps).coerceIn(0, without.size)
        val moved = without.take(target) + tool + without.drop(target)
        val firstDivider = moved.indexOf(MORE_DIVIDER)
        val secondDivider = moved.indexOf(HIDDEN_DIVIDER)
        return ToolbarLayout(
            main = moved.take(firstDivider).filterIsInstance<ToolbarTool>(),
            more = moved.subList(firstDivider + 1, secondDivider).filterIsInstance<ToolbarTool>(),
            hidden = moved.drop(secondDivider + 1).filterIsInstance<ToolbarTool>(),
        )
    }

    /** The stored form. */
    fun encode(): String = ToolbarSection.entries.joinToString(";") { section ->
        "${section.name.lowercase()}:${tools(section).joinToString(",") { it.name }}"
    }

    companion object {
        private val MORE_DIVIDER = Any()
        private val HIDDEN_DIVIDER = Any()

        /** The toolbar as it ships: the common tools in the row, the rest behind the chevron. */
        val DEFAULT = ToolbarLayout(
            main = listOf(
                ToolbarTool.HEADING,
                ToolbarTool.LIST_CHECK,
                ToolbarTool.LIST_BULLET,
                ToolbarTool.LIST_NUMBER,
                ToolbarTool.QUOTE,
                ToolbarTool.CODE,
            ),
            more = listOf(
                ToolbarTool.TEXT,
                ToolbarTool.H1,
                ToolbarTool.H2,
                ToolbarTool.H3,
                ToolbarTool.H4,
                ToolbarTool.LINK,
                ToolbarTool.SEPARATOR,
                ToolbarTool.TABLE,
            ),
        )

        /**
         * The layout [stored] describes. A tool name this version does not know is dropped, a tool named twice
         * stays where it came first, and a tool the text leaves out (one added by a later version) is put where
         * it is by default; text that names no section is the default layout.
         */
        fun decode(stored: String?): ToolbarLayout {
            val sections = stored.orEmpty().split(';').mapNotNull { part ->
                val section = ToolbarSection.entries.firstOrNull { it.name.equals(part.substringBefore(':'), true) }
                section?.takeIf { ':' in part }?.let { it to part.substringAfter(':').split(',').mapNotNull(::named) }
            }.toMap()
            if (sections.isEmpty()) return DEFAULT
            val seen = HashSet<ToolbarTool>()
            val taken = ToolbarSection.entries.associateWith { sections[it].orEmpty().filter(seen::add) }
            val missing = ToolbarTool.entries.filter { it !in seen }
            return ToolbarLayout(
                main = taken.getValue(ToolbarSection.MAIN) + missing.filter { it in DEFAULT.main },
                more = taken.getValue(ToolbarSection.MORE) + missing.filter { it !in DEFAULT.main },
                hidden = taken.getValue(ToolbarSection.HIDDEN),
            )
        }

        private fun named(name: String): ToolbarTool? = ToolbarTool.entries.firstOrNull { it.name == name.trim() }
    }
}

/** How big the toolbar's buttons are: the icon is [icon] dp inside a [button] dp target. */
data class ToolbarScale(val button: Int, val icon: Int)

/**
 * The buttons of a row of [buttons] across [availableDp]: 44 dp with a 22 dp icon while they fit, then 40 and 36 dp.
 * A row may run a little past the edge (the last button is still reached by scrolling); beyond that the smallest
 * size is used and the row scrolls.
 */
fun toolbarScale(buttons: Int, availableDp: Int): ToolbarScale =
    SCALES.firstOrNull { buttons * it.button + ROW_EXTRA_DP <= availableDp + OVERFLOW_ALLOWED_DP } ?: SCALES.last()

private val SCALES = listOf(
    ToolbarScale(button = 44, icon = 22),
    ToolbarScale(button = 40, icon = 20),
    ToolbarScale(button = 36, icon = 18),
)

/** What a row needs besides its buttons: the rule between groups and the gap after the toggle. */
private const val ROW_EXTRA_DP = 10

/** How far past the screen's edge a row may reach before its buttons shrink. */
private const val OVERFLOW_ALLOWED_DP = 24

/**
 * "Editor toolbar" (Settings → Appearance): which block tools the toolbar offers and in what order. Kept in the
 * app's flags and read at once, so the first note opened already shows it. Unreadable text is the default layout.
 */
@SingleIn(AppScope::class)
@Inject
class EditorToolbar(private val flags: KeyValueStore) {
    private val _layout = MutableStateFlow(ToolbarLayout.decode(flags.getString(LAYOUT_KEY, null)))

    /** The layout in use, live. */
    val layout: StateFlow<ToolbarLayout> = _layout.asStateFlow()

    /** Uses and stores [layout]. */
    fun set(layout: ToolbarLayout) {
        flags.edit { putString(LAYOUT_KEY, layout.encode()) }
        _layout.value = layout
    }

    /** Back to the toolbar as it ships. */
    fun reset() = set(ToolbarLayout.DEFAULT)

    private companion object {
        const val LAYOUT_KEY = "editor_toolbar"
    }
}
