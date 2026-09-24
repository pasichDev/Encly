package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.pasich.encly.R
import com.pasich.encly.presentation.components.HomeTaskWidget
import com.pasich.encly.presentation.designsystem.EnclyFab
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySearchField
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.dialogs.NotesSortBottomSheet
import com.pasich.encly.presentation.drawer.MainDrawer
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.TASKS_ADD_ARG
import com.pasich.encly.presentation.viewmodel.LockNowViewModel
import com.pasich.encly.presentation.viewmodel.MainListStateViewModel
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

/** The scrim over the notes while the drawer is open. */
private const val DRAWER_SCRIM_ALPHA = 0.32f

@Composable
fun MainRootScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = DRAWER_SCRIM_ALPHA),
        drawerContent = {
            MainDrawer(
                drawerState = drawerState,
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
        MainScreen(drawerState = drawerState, navController = navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    modifier: Modifier = Modifier,
    mainListStateViewModel: MainListStateViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    noteListViewModel: NoteListViewModel = hiltViewModel(),
    searchViewModel: NoteSearchViewModel = hiltViewModel(),
    lockNowViewModel: LockNowViewModel = hiltViewModel(),
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

    if (viewOptionsVisible) {
        ViewOptionsSheet(
            isGrid = isGrid,
            onToggleView = mainListStateViewModel::updateGridNoteList,
            onClose = { viewOptionsVisible = false },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            NewNoteFab(expanded = atTop, onClick = { navigation.newNote(noteListViewModel.state.value.selectedTag) })
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NotesTopBar(
                onMenu = { scope.launch { drawerState.open() } },
                onLockNow = lockNowViewModel::lockNow,
                onSettings = { navigation.open(NavRoutes.SettingsRoute.name) },
            )
            NotesList(
                isGrid = isGrid,
                listScrollState = listScrollState,
                gridScrollState = gridScrollState,
                search = NotesSearch(query, searchResults),
                onItemClick = { note -> navigation.editNote(note.id) },
                onSecondActionClick = { note -> navigation.editNote(-1, "?copySource=${note.id}") },
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
        text = stringResource(R.string.note_add),
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
    val scope = rememberCoroutineScope()
    NotesSortBottomSheet(
        isBottomSheetVisible = true,
        sheetState = sheetState,
        isGrid = isGrid,
        onToggleView = onToggleView,
        onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } },
    )
}

/** Where the notes screen goes: a note (new, existing or a copy) or another screen. */
private class MainNavigation(private val navController: NavHostController) {
    fun editNote(noteId: Long, params: String = "") {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        navController.navigate("${NavRoutes.EditNoteRoute.name}/$noteId$params", navOptions)
    }

    /** A new note, tagged with the chip selected in the list (0: none). */
    fun newNote(selectedTag: Long) = editNote(-1, if (selectedTag != 0L) "?addTag=$selectedTag" else "")

    fun open(route: String) {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true).build()
        navController.navigate(route, navOptions)
    }
}

/** The root bar of the notes screen: menu, "Notes", Lock now and Settings. */
@Composable
private fun NotesTopBar(onMenu: () -> Unit, onLockNow: () -> Unit, onSettings: () -> Unit) {
    EnclyTopBar(
        title = stringResource(R.string.notes_title),
        navigation = {
            IconButton(onClick = onMenu) {
                Icon(EnclyIcons.Menu, contentDescription = stringResource(R.string.open_menu))
            }
        },
        actions = {
            IconButton(onClick = onLockNow) {
                Icon(EnclyIcons.Lock, contentDescription = stringResource(R.string.lock_now))
            }
            IconButton(onClick = onSettings) {
                Icon(EnclyIcons.Sliders, contentDescription = stringResource(R.string.main_drawer_settings))
            }
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
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier.padding(horizontal = gutter),
            trailing = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(EnclyIcons.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                } else {
                    IconButton(onClick = onViewOptions) {
                        Icon(EnclyIcons.Sliders, contentDescription = stringResource(R.string.view_options))
                    }
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
