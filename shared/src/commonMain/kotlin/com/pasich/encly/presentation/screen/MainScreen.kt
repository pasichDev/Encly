package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_now
import com.pasich.encly.core.resources.main_drawer_settings
import com.pasich.encly.core.resources.note_add
import com.pasich.encly.core.resources.notes_title
import com.pasich.encly.core.resources.open_menu
import com.pasich.encly.core.resources.search_clear
import com.pasich.encly.core.resources.search_placeholder
import com.pasich.encly.core.resources.view_options
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.presentation.components.HomeTaskWidget
import com.pasich.encly.presentation.designsystem.EnclyFab
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySearchField
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.dialogs.NotesSortBottomSheet
import com.pasich.encly.presentation.dialogs.rememberSheetDismisser
import com.pasich.encly.presentation.drawer.MainDrawer
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.NewNoteCommand
import com.pasich.encly.presentation.navigation.TASKS_ADD_ARG
import com.pasich.encly.presentation.pane.ExpandedMainRoot
import com.pasich.encly.presentation.pane.MediumMainRoot
import com.pasich.encly.presentation.viewmodel.LockNowViewModel
import com.pasich.encly.presentation.viewmodel.MainListStateViewModel
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The scrim over the notes while the drawer is open. */
private const val DRAWER_SCRIM_ALPHA = 0.32f

@Composable
fun MainRootScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    when (LocalWidthClass.current) {
        WidthClass.Compact -> CompactMainRoot(navController, modifier)
        WidthClass.Medium -> MediumMainRoot(navController, modifier)
        WidthClass.Expanded -> ExpandedMainRoot(navController, modifier)
    }
}

/** The phone layout: the notes, with the navigation drawer sliding over them. */
@Composable
private fun CompactMainRoot(navController: NavHostController, modifier: Modifier = Modifier) {
    // Not saved: coming back from a page opened in the drawer shows the notes with it closed.
    val drawerState = remember { DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    // The screen draws under the status bar: the scrim dims the whole window, and the drawer
    // pads its own content below the bar.
    val statusBarDp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = DRAWER_SCRIM_ALPHA),
        drawerContent = {
            MainDrawer(
                drawerState = drawerState,
                topInset = statusBarDp,
                onItemClick = { selectedItem ->
                    scope.launch {
                        val navOptions = NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setRestoreState(true)
                            .build()
                        navController.navigate(selectedItem.route, navOptions)
                    }
                },
            )
        },
    ) {
        MainScreen(
            drawerState = drawerState,
            navController = navController,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    drawerState: DrawerState?,
    modifier: Modifier = Modifier,
    noteOpener: NoteOpener? = null,
    mainListStateViewModel: MainListStateViewModel = metroViewModel(),
    settingsViewModel: SettingsViewModel = metroViewModel(),
    noteListViewModel: NoteListViewModel = metroViewModel(),
    searchViewModel: NoteSearchViewModel = metroViewModel(),
    lockNowViewModel: LockNowViewModel = metroViewModel(),
) {
    val isGrid by mainListStateViewModel.isGridNoteList.collectAsState()
    val showTasks by settingsViewModel.showTasksFlow.collectAsState()
    // The field reads local state (a flow round trip can drop fast keystrokes); the ViewModel
    // gets every change.
    // Plain remember: a search query is note content and must not land in saved state.
    var query by remember { mutableStateOf("") }
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()

    // State restoration for scroll states
    val listScrollState = rememberLazyListState()
    val gridScrollState = rememberLazyStaggeredGridState()
    val atTop by rememberAtTop(isGrid, listScrollState, gridScrollState)
    val scrollRequest by noteListViewModel.scrollToTopRequest.collectAsStateWithLifecycle()
    ScrollToTopOnRequest(scrollRequest, isGrid, listScrollState, gridScrollState, noteListViewModel::onScrolledToTop)

    var viewOptionsVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val navigation = remember(navController) { MainNavigation(navController) }
    val notes = noteOpener ?: navigation
    // The keyboard and the menu bar start a note the way the button below does.
    NewNoteCommand { notes.newNote(noteListViewModel.state.value.selectedTag) }

    if (viewOptionsVisible) {
        ViewOptionsSheet(isGrid, mainListStateViewModel::updateGridNoteList) { viewOptionsVisible = false }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            NewNoteFab(expanded = atTop, onClick = { notes.newNote(noteListViewModel.state.value.selectedTag) })
        },
    ) { padding ->
        // The Scaffold has no top bar slot, so `padding` carries the status bar; consuming it
        // keeps the notes bar below from padding the same inset again.
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NotesTopBar(
                onMenu = drawerState?.let { drawer -> { scope.launch { drawer.open() } } },
                onLockNow = lockNowViewModel::lockNow,
                onSettings = { navigation.open(NavRoutes.SettingsRoute.name) },
            )
            NotesList(
                isGrid = isGrid,
                listScrollState = listScrollState,
                gridScrollState = gridScrollState,
                search = NotesSearch(query, searchResults),
                swipe = settingsViewModel.swipeSettings.collectAsState().value ?: SwipeSettings.Off,
                onItemClick = { note -> notes.editNote(note.id) },
                onSecondActionClick = { note -> notes.editNote(-1, "?copySource=${note.id}") },
                tabs = notes.tabs(),
                header = {
                    NotesHeader(
                        query = query,
                        onQueryChange = {
                            query = it
                            searchViewModel.onQueryChange(it)
                        },
                        onViewOptions = { viewOptionsVisible = true },
                        tasks = HomeTasks(
                            onOpen = { navigation.open(NavRoutes.TasksRoute.name) },
                            onNewTask = { navigation.open("${NavRoutes.TasksRoute.name}?$TASKS_ADD_ARG=true") },
                        ).takeIf { showTasks && query.isBlank() },
                    )
                },
            )
        }
    }
}

/** Whether the notes are scrolled to the top; the FAB then shows its label. */
@Composable
private fun rememberAtTop(isGrid: Boolean, list: LazyListState, grid: LazyStaggeredGridState): State<Boolean> =
    remember(isGrid, list, grid) {
        derivedStateOf { if (isGrid) grid.firstVisibleItemIndex == 0 else list.firstVisibleItemIndex == 0 }
    }

/**
 * Scrolls the notes back to the top while the ViewModel has a [request] pending (a new sort order
 * or a new note), then reports it done through [onScrollComplete]. The request stays pending while the
 * editor is open, so coming back to a list that got a new note meanwhile scrolls too; an
 * unchanged list, or one restored after process death, keeps its position.
 */
@Composable
private fun ScrollToTopOnRequest(
    request: Int?,
    isGrid: Boolean,
    list: LazyListState,
    grid: LazyStaggeredGridState,
    onScrollComplete: (Int) -> Unit,
) {
    val currentOnScrollComplete by rememberUpdatedState(onScrollComplete)
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        if (isGrid) grid.scrollToItem(0) else list.scrollToItem(0)
        currentOnScrollComplete(request)
    }
}

/** "New note", collapsed to its icon once the list scrolls. */
@Composable
private fun NewNoteFab(expanded: Boolean, onClick: () -> Unit) {
    EnclyFab(
        text = stringResource(Res.string.note_add),
        icon = EnclyIcons.PlusBold,
        expanded = expanded,
        onClick = onClick,
    )
}

/** The view options sheet (sort order, list or grid); [onClose] runs once it has slid away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewOptionsSheet(isGrid: Boolean, onToggleView: () -> Unit, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NotesSortBottomSheet(
        isBottomSheetVisible = true,
        sheetState = sheetState,
        isGrid = isGrid,
        onToggleView = onToggleView,
        onDismiss = rememberSheetDismisser(sheetState, onClose),
    )
}

/** Where a note from the notes list opens: a full screen on a phone, the detail pane in an expanded window. */
interface NoteOpener {
    /** Opens note [noteId] (-1: a new one), with the editor route's query [params] such as a copy source. */
    fun editNote(noteId: Long, params: String = "")

    /** A new note, tagged with the chip selected in the list (0: none). */
    fun newNote(selectedTag: Long) = editNote(-1, if (selectedTag != 0L) "?addTag=$selectedTag" else "")

    /** Whether notes open as tabs beside the list: the list then marks the shown one and can open another. */
    val supportsTabs: Boolean get() = false

    /** The stored note shown beside the list, or null. */
    val activeNoteId: Long? get() = null

    /** Opens [note] as a tab of its own without leaving the one shown; only where [supportsTabs]. */
    fun openInNewTab(note: Note) = editNote(note.id)
}

/** What the notes list shows of the tabs beside it, or null where the opener has none. */
private fun NoteOpener.tabs(): NotesTabs? = takeIf { it.supportsTabs }?.let { opener ->
    NotesTabs(activeNoteId = { opener.activeNoteId }, onOpenInNewTab = opener::openInNewTab)
}

/** Where the notes screen goes: a note (new, existing or a copy) or another screen. */
private class MainNavigation(private val navController: NavHostController) : NoteOpener {
    override fun editNote(noteId: Long, params: String) {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        navController.navigate("${NavRoutes.EditNoteRoute.name}/$noteId$params", navOptions)
    }

    fun open(route: String) {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true).build()
        navController.navigate(route, navOptions)
    }
}

/** The root bar of the notes screen: menu, "Notes", Lock now and Settings. */
@Composable
private fun NotesTopBar(onMenu: (() -> Unit)?, onLockNow: () -> Unit, onSettings: () -> Unit) {
    EnclyTopBar(
        title = stringResource(Res.string.notes_title),
        navigation = onMenu?.let { menu ->
            { IconWithTooltip(EnclyIcons.Menu, stringResource(Res.string.open_menu), menu) }
        },
        actions = {
            IconWithTooltip(EnclyIcons.Lock, stringResource(Res.string.lock_now), onLockNow)
            IconWithTooltip(EnclyIcons.Sliders, stringResource(Res.string.main_drawer_settings), onSettings)
        },
    )
}

/** Where the home tasks card leads: all tasks, or straight to a new one. Null hides the card. */
private class HomeTasks(val onOpen: () -> Unit, val onNewTask: () -> Unit)

/** Above the notes: the search field with its view options, the tag chips and the tasks card. */
@Composable
private fun NotesHeader(query: String, onQueryChange: (String) -> Unit, onViewOptions: () -> Unit, tasks: HomeTasks?) {
    val gutter = EnclyTheme.spacing.listGutter
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap)) {
        EnclySearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.search_placeholder),
            modifier = Modifier.padding(horizontal = gutter),
            trailing = {
                if (query.isNotEmpty()) {
                    IconWithTooltip(EnclyIcons.Close, stringResource(Res.string.search_clear), onClick = {
                        onQueryChange("")
                    })
                } else {
                    IconWithTooltip(EnclyIcons.Filter, stringResource(Res.string.view_options), onViewOptions)
                }
            },
        )
        if (query.isBlank()) TagsList()
        if (tasks != null) {
            HomeTaskWidget(
                onTasksClick = tasks.onOpen,
                onNewTask = tasks.onNewTask,
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
    }
}
