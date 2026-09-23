package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import com.pasich.encly.R
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.presentation.components.HomeTaskWidget
import com.pasich.encly.presentation.components.appbar.HomeBar
import com.pasich.encly.presentation.dialogs.NotesSortBottomSheet
import com.pasich.encly.presentation.drawer.MainDrawer
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.MainListStateViewModel
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun MainRootScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    DismissibleNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = true,
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
                onNoteClick = { noteId ->
                    val navOptions = NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build()
                    navController.navigate("${NavRoutes.EditNoteRoute.name}/$noteId", navOptions)
                },
            )
        },
    ) {
        MainScreen(
            drawerState = drawerState,
            navController = navController,
            modifier = Modifier.clickable(
                enabled = drawerState.isOpen,
                onClick = {
                    if (drawerState.isOpen) {
                        scope.launch {
                            drawerState.close()
                        }
                    }
                },
            ),
        )
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
) {
    val isGrid by mainListStateViewModel.isGridNoteList.collectAsState()
    val showTasks by settingsViewModel.showTasksFlow.collectAsState()

    // State restoration for scroll states
    val listScrollState = rememberLazyListState()
    val gridScrollState = rememberLazyStaggeredGridState()

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

    var bottomSheetsType by rememberSaveable {
        mutableStateOf(
            BottomSheetsOpenType.NONE,
        )
    }
    val scope = rememberCoroutineScope()

    // Function for optimized navigation
    val navigateToEditNote = { noteId: Long, additionalParams: String ->
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .build()
        navController.navigate(
            "${NavRoutes.EditNoteRoute.name}/$noteId$additionalParams",
            navOptions,
        )
    }

    val navigateToTasks = {
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .build()
        navController.navigate(NavRoutes.TasksRoute.name, navOptions)
    }

    NotesSortBottomSheet(
        isBottomSheetVisible = BottomSheetsOpenType.SORT == bottomSheetsType,
        sheetState = sheetState,
        onDismiss = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                bottomSheetsType = BottomSheetsOpenType.NONE
            }
        },
    )

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val addTagParam = if (noteListViewModel.state.value.selectedTag != 0L) {
                        "?addTag=${noteListViewModel.state.value.selectedTag}"
                    } else {
                        ""
                    }
                    navigateToEditNote(-1, addTagParam)
                },
                contentColor = Color.White,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(16.dp)
                    .size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.note_add),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HomeBar(drawerState, isGrid = isGrid, onToggleView = {
                mainListStateViewModel.updateGridNoteList()
            }, showSortDialog = {
                bottomSheetsType = BottomSheetsOpenType.SORT
                scope.launch { sheetState.show() }
            })

            val isVisible = remember {
                derivedStateOf {
                    if (isGrid) {
                        gridScrollState.firstVisibleItemIndex == 0 &&
                            gridScrollState.firstVisibleItemScrollOffset == 0
                    } else {
                        listScrollState.firstVisibleItemIndex == 0 &&
                            listScrollState.firstVisibleItemScrollOffset == 0
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(visible = isVisible.value && showTasks) {
                HomeTaskWidget(
                    onTasksClick = { navigateToTasks() },
                )
            }
            AnimatedVisibility(visible = isVisible.value && showTasks) {
                Spacer(Modifier.height(10.dp))
            }
            TagsList()

            NotesList(
                isGrid,
                listScrollState,
                gridScrollState,
                onItemClick = { note ->
                    navigateToEditNote(note.id, "")
                },
                onSecondActionClick = { note ->
                    navigateToEditNote(-1, "?copySource=${note.id}")
                },
            )
        }
    }
}
