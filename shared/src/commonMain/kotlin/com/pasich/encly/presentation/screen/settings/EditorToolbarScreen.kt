package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.settings_editor_toolbar
import com.pasich.encly.core.resources.toolbar_hint
import com.pasich.encly.core.resources.toolbar_move_down
import com.pasich.encly.core.resources.toolbar_move_up
import com.pasich.encly.core.resources.toolbar_reset
import com.pasich.encly.core.resources.toolbar_section_hidden
import com.pasich.encly.core.resources.toolbar_section_main
import com.pasich.encly.core.resources.toolbar_section_more
import com.pasich.encly.core.resources.toolbar_tool_hide
import com.pasich.encly.core.resources.toolbar_tool_show
import com.pasich.encly.domain.model.ToolbarLayout
import com.pasich.encly.domain.model.ToolbarSection
import com.pasich.encly.domain.model.ToolbarTool
import com.pasich.encly.presentation.components.editNote.toBlockTool
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyScrollColumn
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.viewmodel.EditorToolbarViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Settings → Appearance → Editor toolbar: which block tools the editing toolbar offers, which of them sit behind
 * its chevron, and in what order. A tool is dragged by its handle (up or down, across the section titles), and
 * its eye hides it or brings it back. Every change is saved at once; "Reset" restores the toolbar as it ships.
 */
@Composable
fun EditorToolbarScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: EditorToolbarViewModel = metroViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    EditorToolbarContent(
        layout = layout,
        actions = ToolbarActions(
            onBack = { navController.popBackStack() },
            onMove = viewModel::move,
            onToggle = viewModel::toggle,
            onReset = viewModel::reset,
        ),
        modifier = modifier,
    )
}

/** What the screen can ask for. */
private class ToolbarActions(
    val onBack: () -> Unit,
    val onMove: (ToolbarTool, Int) -> Unit,
    val onToggle: (ToolbarTool) -> Unit,
    val onReset: () -> Unit,
)

/** One line of the list: a section title or a tool. */
private sealed interface ToolbarEntry {
    data class Title(val section: ToolbarSection) : ToolbarEntry

    data class Tool(val tool: ToolbarTool) : ToolbarEntry
}

private fun ToolbarLayout.entries(): List<ToolbarEntry> = ToolbarSection.entries.flatMap { section ->
    listOf<ToolbarEntry>(ToolbarEntry.Title(section)) + tools(section).map { ToolbarEntry.Tool(it) }
}

@Composable
private fun EditorToolbarContent(layout: ToolbarLayout, actions: ToolbarActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(Res.string.settings_editor_toolbar),
                onBack = actions.onBack,
                actions = {
                    TextButton(onClick = actions.onReset, enabled = layout != ToolbarLayout.DEFAULT) {
                        Text(stringResource(Res.string.toolbar_reset))
                    }
                },
            )
        },
    ) { padding ->
        EnclyScrollColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = spacing.gutter,
                end = spacing.gutter,
                top = spacing.s,
                bottom = spacing.l,
            ),
        ) {
            Text(
                text = stringResource(Res.string.toolbar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolList(layout, actions)
        }
    }
}

/** The tools in their sections as rows of one height, so a drag of so many rows moves a tool that many places. */
@Composable
private fun ToolList(layout: ToolbarLayout, actions: ToolbarActions) {
    val rowPx = with(LocalDensity.current) { EnclyTheme.spacing.rowHeight.toPx() }
    layout.entries().forEach { entry ->
        when (entry) {
            is ToolbarEntry.Title -> SectionTitle(entry.section)

            is ToolbarEntry.Tool -> key(entry.tool) {
                ToolRow(entry.tool, hidden = entry.tool in layout.hidden, rowPx = rowPx, actions = actions)
            }
        }
    }
}

@Composable
private fun SectionTitle(section: ToolbarSection) {
    val title = when (section) {
        ToolbarSection.MAIN -> Res.string.toolbar_section_main
        ToolbarSection.MORE -> Res.string.toolbar_section_more
        ToolbarSection.HIDDEN -> Res.string.toolbar_section_hidden
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EnclyTheme.spacing.rowHeight),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = EnclyTheme.spacing.xs),
        )
    }
}

/** How far a row has been pulled by its handle, and whether it is being pulled. */
private class DragState {
    var offset by mutableFloatStateOf(0f)
    var active by mutableStateOf(false)
}

@Composable
private fun ToolRow(tool: ToolbarTool, hidden: Boolean, rowPx: Float, actions: ToolbarActions) {
    val block = tool.toBlockTool()
    val label = block.headingLevel?.let { stringResource(block.label, it) } ?: stringResource(block.label)
    val drag = remember { DragState() }
    val spacing = EnclyTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.rowHeight)
            .zIndex(if (drag.active) 1f else 0f)
            .graphicsLayer { translationY = drag.offset }
            .background(
                if (drag.active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium,
            )
            .moveActions(tool, actions.onMove)
            .alpha(if (hidden) HIDDEN_ALPHA else 1f),
    ) {
        Icon(
            EnclyIcons.Grip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(spacing.iconMedium)
                .dragToMove(drag, rowPx) { steps -> actions.onMove(tool, steps) },
        )
        Icon(block.icon, contentDescription = null, modifier = Modifier.size(spacing.iconMedium))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val eye = if (hidden) Res.string.toolbar_tool_show else Res.string.toolbar_tool_hide
        EnclyToolButton(
            icon = if (hidden) EnclyIcons.EyeOff else EnclyIcons.Eye,
            contentDescription = stringResource(eye),
            onClick = { actions.onToggle(tool) },
        )
    }
}

/** TalkBack's way to reorder: "Move up" and "Move down" on the row of [tool]. */
@Composable
private fun Modifier.moveActions(tool: ToolbarTool, onMove: (ToolbarTool, Int) -> Unit): Modifier {
    val up = stringResource(Res.string.toolbar_move_up)
    val down = stringResource(Res.string.toolbar_move_down)
    return semantics {
        customActions = listOf(
            CustomAccessibilityAction(up) { true.also { onMove(tool, -1) } },
            CustomAccessibilityAction(down) { true.also { onMove(tool, 1) } },
        )
    }
}

/** Pulling a row by its handle: it follows the finger, and where it is let go it moves that many rows. */
private fun Modifier.dragToMove(drag: DragState, rowPx: Float, onMove: (steps: Int) -> Unit): Modifier =
    pointerInput(rowPx) {
        detectDragGestures(
            onDragStart = { drag.active = true },
            onDrag = { change, amount ->
                change.consume()
                drag.offset += amount.y
            },
            onDragEnd = {
                val steps = (drag.offset / rowPx).roundToInt()
                drag.active = false
                drag.offset = 0f
                if (steps != 0) onMove(steps)
            },
            onDragCancel = {
                drag.active = false
                drag.offset = 0f
            },
        )
    }

private const val HIDDEN_ALPHA = 0.5f
