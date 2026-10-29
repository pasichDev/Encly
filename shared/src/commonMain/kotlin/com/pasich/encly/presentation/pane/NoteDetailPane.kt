package com.pasich.encly.presentation.pane

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.close
import com.pasich.encly.core.resources.pane_select_note
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.designsystem.EnclyHorizontalScrollbar
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTooltip
import com.pasich.encly.presentation.designsystem.LocalEnclyShortcuts
import com.pasich.encly.presentation.designsystem.enclyInteractive
import com.pasich.encly.presentation.designsystem.enclyInteractiveRound
import com.pasich.encly.presentation.designsystem.enclyMiddleClick
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The detail pane's route for the editor of one tab (see [paneEditorRoute]). */
private const val PANE_EDITOR = "PaneEditor"

/** The detail pane's route while no note is open. */
private const val PANE_EMPTY = "PaneEmpty"

private const val TAB_ARG = "tab"

/** The test tag of a tab of the strip. */
internal const val NOTE_TAB_TAG = "note-tab"

/**
 * What the editor of the detail pane needs from its host: the [tabs] and the tab it is the editor of
 * ([tabKey]), which it uses to leave (Back closes the tab after saving) and to say what its note is called.
 */
class EditorPane(val tabs: NoteTabs, val tabKey: Long)

/** The route that opens [tab]'s note in the pane's own navigation host. */
internal fun paneEditorRoute(tab: NoteTab): String {
    val query = if (tab.params.isEmpty()) "?" else "${tab.params}&"
    return "$PANE_EDITOR/${tab.noteId}${query}$TAB_ARG=${tab.key}"
}

/**
 * The expanded layout's detail pane: the strip of open notes, and the editor of the one shown (or a prompt
 * to pick a note). The editor is the same screen the phone shows full-screen, so its saving and leaving rules
 * are unchanged; it runs in a navigation host of its own so that it has the ViewModel, saved state and
 * lifecycle of a destination, and leaving a tab pops that destination, which drops the note from memory.
 * [navController] is the app's, for what the editor opens beyond the pane (the tag manager).
 */
@Composable
fun NoteDetailPane(tabs: NoteTabs, navController: NavHostController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (tabs.tabs.isNotEmpty()) {
            NoteTabStrip(
                tabs = tabs.tabs,
                activeKey = tabs.activeKey,
                onSelect = { key -> scope.launch { tabs.activate(key) } },
                onClose = tabs::close,
            )
        }
        // The strip (when there is one) pads the status bar; the editor below must not pad it again.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
            PaneEditorHost(tabs = tabs, appNavController = navController)
        }
    }
}

/**
 * The pane's navigation host: a single destination at a time, the editor of the shown tab or the empty
 * prompt. A change of tab replaces the whole back stack, so the editor that was shown is popped and its
 * ViewModel cleared.
 */
@Composable
private fun PaneEditorHost(tabs: NoteTabs, appNavController: NavHostController) {
    val controller = rememberNavController()
    val start = remember { tabs.active?.let(::paneEditorRoute) ?: PANE_EMPTY }
    var shownKey by remember { mutableStateOf(tabs.activeKey) }

    NavHost(
        navController = controller,
        startDestination = start,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(PANE_EMPTY) { EmptyPane() }
        composable(
            route = "$PANE_EDITOR/{idNote}?copySource={copySource}&addTag={addTag}&$TAB_ARG={$TAB_ARG}",
            arguments = listOf(
                navArgument("idNote") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("copySource") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("addTag") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(TAB_ARG) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { entry -> PaneEditor(entry, tabs, appNavController) }
    }

    LaunchedEffect(tabs.activeKey) {
        if (tabs.activeKey == shownKey) return@LaunchedEffect
        shownKey = tabs.activeKey
        val route = tabs.active?.let(::paneEditorRoute) ?: PANE_EMPTY
        controller.navigate(route) { popUpTo(controller.graph.id) { inclusive = true } }
    }
}

@Composable
private fun PaneEditor(entry: NavBackStackEntry, tabs: NoteTabs, appNavController: NavHostController) {
    val tabKey = entry.arguments?.read { getLongOrNull(TAB_ARG) } ?: 0L
    EditNoteScreen(navController = appNavController, pane = EditorPane(tabs, tabKey))
}

/** What the pane shows while no note is open. */
@Composable
private fun EmptyPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.pane_select_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The strip of open notes: one tab each, the shown one marked, a close button on every tab. A tab takes the
 * pointer like any control (a hand cursor, a focus ring), closes on a middle click, and the strip shows a
 * scrollbar while the tabs do not fit.
 */
@Composable
internal fun NoteTabStrip(
    tabs: List<NoteTab>,
    activeKey: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .height(EnclyTheme.spacing.tabHeight),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scroll)
                .padding(horizontal = EnclyTheme.spacing.xs),
        ) {
            tabs.forEach { tab ->
                NoteTabItem(
                    tab = tab,
                    selected = tab.key == activeKey,
                    onSelect = { onSelect(tab.key) },
                    onClose = { onClose(tab.key) },
                )
            }
        }
        EnclyHorizontalScrollbar(scroll)
    }
}

@Composable
private fun NoteTabItem(tab: NoteTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = tab.title.ifBlank { stringResource(Res.string.untitled) }
    val closeLabel = "${stringResource(Res.string.close)} $title"
    // The shown tab is the one Ctrl+W / ⌘W closes, so its hint names the key.
    val closeKey = LocalEnclyShortcuts.current.label(AppCommand.CloseTab)
    val closeHint = if (selected) "$closeLabel ($closeKey)" else closeLabel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = Modifier
            .widthIn(max = EnclyTheme.spacing.tabMaxWidth)
            .height(EnclyTheme.spacing.tabHeight)
            .background(
                color = if (selected) colors.surface else colors.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
            )
            .testTag(NOTE_TAB_TAG)
            .enclyInteractive(MaterialTheme.shapes.small)
            .enclyMiddleClick(onClose)
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
            }
            .padding(start = EnclyTheme.spacing.s, end = EnclyTheme.spacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Leaves room for the close button; a weighted Text would collapse in a Row that wraps its content.
            modifier = Modifier.widthIn(
                max = EnclyTheme.spacing.tabMaxWidth - EnclyTheme.spacing.icon - EnclyTheme.spacing.xs -
                    EnclyTheme.spacing.s - EnclyTheme.spacing.xxs * 2,
            ),
        )
        TabCloseButton(label = closeLabel, hint = closeHint, onClose = onClose)
    }
}

/** The close button of a tab: its [label] for assistive technology, its [hint] (with the key) on hover. */
@Composable
private fun TabCloseButton(label: String, hint: String, onClose: () -> Unit) {
    EnclyTooltip(label = hint) {
        Icon(
            imageVector = EnclyIcons.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(EnclyTheme.spacing.icon + EnclyTheme.spacing.xs)
                .enclyInteractiveRound()
                .clickable(onClick = onClose)
                .padding(EnclyTheme.spacing.xs)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
        )
    }
}
