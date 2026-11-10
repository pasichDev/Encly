package com.pasich.encly.presentation.pane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.drawer.MainPermanentDrawer
import com.pasich.encly.presentation.navigation.LocalEraseEpoch
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.navigation.TabCommands
import com.pasich.encly.presentation.screen.MainScreen
import com.pasich.encly.presentation.screen.NoteOpener
import com.pasich.encly.presentation.viewmodel.NoteTabsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.WidthClass
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The notes in a medium window: a navigation rail instead of the drawer button, the list centred. */
@Composable
fun MediumMainRoot(navController: NavHostController, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxSize()) {
        PageNavigation(WidthClass.Medium, NavRoutes.HomeRoute, navController)
        CenteredContent(modifier = Modifier.weight(1f)) {
            MainScreen(navController = navController, drawerState = null)
        }
    }
}

/**
 * The notes in an expanded window: the permanent drawer, the list of notes and, beside it, the open notes
 * (see [NoteDetailPane]). A note picked in the list opens in the pane instead of taking the window.
 */
@Composable
fun ExpandedMainRoot(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    tabsViewModel: NoteTabsViewModel = metroViewModel(),
) {
    val tabs = tabsViewModel.tabs
    val scope = rememberCoroutineScope()
    val eraseEpoch = LocalEraseEpoch.current
    val opener = remember(tabs, scope, eraseEpoch) { PaneNoteOpener(tabs, scope) { eraseEpoch?.invoke() } }
    RememberOpenNoteForRelock(tabs)
    TabCommands(tabs)

    Row(modifier = modifier.fillMaxSize()) {
        MainPermanentDrawer(
            selectedRoute = NavRoutes.HomeRoute.name,
            onItemClick = { item -> navController.openDestination(item, from = NavRoutes.HomeRoute) },
        )
        Box(modifier = Modifier.width(EnclyTheme.spacing.listPaneWidth).fillMaxHeight()) {
            MainScreen(navController = navController, drawerState = null, noteOpener = opener)
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NoteDetailPane(tabs = tabs, navController = navController, modifier = Modifier.weight(1f))
    }
}

/** Opens the notes of the list as tabs of the detail pane. */
private class PaneNoteOpener(
    private val tabs: NoteTabs,
    private val scope: CoroutineScope,
    private val eraseEpoch: () -> Long?,
) : NoteOpener {
    override fun editNote(noteId: Long, params: String) {
        scope.launch { tabs.open(noteId, params, eraseEpoch()) }
    }

    override val supportsTabs: Boolean get() = true

    override val activeNoteId: Long? get() = tabs.active?.noteId?.takeIf { it > 0L }

    override fun openInNewTab(note: Note) {
        scope.launch { tabs.openInBackground(note.id, note.title, eraseEpoch()) }
    }
}

/**
 * Keeps the id and the vault epoch of the note shown in the pane on the home entry, where the phone's
 * editor keeps them on its own: a background re-lock then returns to that note after the unlock (see
 * [RelockReturn]). Only the shown note travels, never its content, and only once it is stored.
 */
@Composable
private fun RememberOpenNoteForRelock(tabs: NoteTabs) {
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry
    val active = tabs.active
    val noteId = active?.noteId?.takeIf { it > 0L }
    val epoch = active?.epoch
    LaunchedEffect(entry, noteId, epoch) {
        val state = entry?.savedStateHandle ?: return@LaunchedEffect
        if (noteId == null) {
            state.remove<Long>(RelockReturn.OPEN_NOTE_ID)
            state.remove<Long>(RelockReturn.OPEN_NOTE_EPOCH)
        } else {
            state[RelockReturn.OPEN_NOTE_ID] = noteId
            if (epoch !=
                null
            ) {
                state[RelockReturn.OPEN_NOTE_EPOCH] = epoch
            } else {
                state.remove<Long>(RelockReturn.OPEN_NOTE_EPOCH)
            }
        }
    }
}
