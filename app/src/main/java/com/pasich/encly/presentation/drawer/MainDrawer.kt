package com.pasich.encly.presentation.drawer

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.presentation.components.HighlightedText
import com.pasich.encly.presentation.components.drawer.CustomNavigationDrawerItem
import com.pasich.encly.presentation.navigation.DrawerNavItem
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.utils.NotesTextFormatter
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawer(
    drawerState: DrawerState,
    onItemClick: (DrawerNavItem) -> Unit,
    onNoteClick: (Long) -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    noteListViewModel: NoteListViewModel = hiltViewModel(),
    statisticViewModel: StatisticViewModel = hiltViewModel()
) {

    val totalTags by statisticViewModel.totalTagsCreated.collectAsStateWithLifecycle()
    val totalTasks by statisticViewModel.totalTasksCreated.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val showTasks by settingsViewModel.showTasksFlow.collectAsState()
    val drawerWidth = getDrawerWidth()

    // Стан для пошуку
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    // Збереження недавніх пошукових запитів (можна винести в ViewModel)
    var recentSearches by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Додавання до недавніх пошуків при пошуку
    fun addToRecentSearches(query: String) {
        if (query.isNotBlank() && !recentSearches.contains(query)) {
            recentSearches = listOf(query) + recentSearches.take(4) // Зберігаємо тільки 5 останніх
        }
    }

    // Отримуємо нотатки для пошуку
    val state by noteListViewModel.state.collectAsStateWithLifecycle()

    // Фільтровані нотатки на основі пошукового запиту
    val filteredNotes = remember(searchQuery, state.notes) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            state.notes.filter { note ->
                note.note.title.contains(searchQuery, ignoreCase = true) ||
                        NotesTextFormatter.jsonToPlainText(
                            note.note.value
                        ).contains(searchQuery, ignoreCase = true)
            }.take(5)
        }
    }

    val drawerItems = buildList {
        add(
            DrawerNavItem(
                title = stringResource(R.string.main_drawer_tags),
                iconRes = R.drawable.ic_tags,
                route = NavRoutes.EditTagRoute.name,
                badgeCount = totalTags
            )
        )
        if (!showTasks) {
            add(
                DrawerNavItem(
                    title = stringResource(R.string.main_drawer_tasks),
                    iconRes = R.drawable.checklist,
                    route = NavRoutes.TasksRoute.name,
                    badgeCount = totalTasks
                )
            )
        }
        addAll(
            listOf(
                DrawerNavItem(
                    title = stringResource(R.string.main_drawer_trash),
                    iconRes = R.drawable.ic_trash,
                    route = NavRoutes.TrashRoute.name
                ),
                DrawerNavItem(
                    title = stringResource(R.string.main_drawer_settings),
                    iconRes = R.drawable.ic_settings,
                    route = NavRoutes.SettingsRoute.name
                ),
                DrawerNavItem(
                    title = stringResource(R.string.main_drawer_support),
                    iconRes = R.drawable.ic_cofee,
                    route = NavRoutes.SupportRoute.name
                ),
                DrawerNavItem(
                    title = stringResource(R.string.main_drawer_faq),
                    iconRes = R.drawable.ic_faq,
                    route = NavRoutes.FaqRoute.name
                ),
                DrawerNavItem(
                    title = stringResource(R.string.about),
                    iconRes = R.drawable.ic_about,
                    route = NavRoutes.AboutRoute.name
                )
            )
        )
    }

    /*  // Динамічно формуємо список елементів залежно від налаштувань та авторизації
val items = listOfNotNull(
    NavigationItem(tagsTitle, R.drawable.ic_tags, badgeCount = totalTags),
    if (!showTasks) NavigationItem(
        tasksTitle,
        R.drawable.checklist,
        badgeCount = totalTasks
    ) else null,
    NavigationItem(trashTitle, R.drawable.ic_trash),
    NavigationItem(settingsTitle, R.drawable.ic_settings),
    NavigationItem(supportTitle, R.drawable.ic_cofee),
    NavigationItem(faqTitle, R.drawable.ic_faq),
    NavigationItem(aboutApp, R.drawable.ic_about)
)



     */

    ModalDrawerSheet(drawerShape = RectangleShape, modifier = Modifier.width(drawerWidth)) {

        SearchBar(
            query = searchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onQueryChange = {
                searchQuery = it
                isSearchActive = it.isNotBlank()
            },
            onSearch = {
                addToRecentSearches(searchQuery)
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_notes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(R.string.search_notes),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = searchQuery.isNotBlank(),
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = "Очистити пошук",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(16.dp),
            content = {
                // Контент результатів пошуку
                AnimatedVisibility(
                    visible = filteredNotes.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),

                        ) {
                        items(filteredNotes) { item ->
                            SearchResultItem(
                                title = item.note.title.ifBlank { "Без назви" },
                                content = NotesTextFormatter.jsonToPlainText(
                                    item.note.value
                                ),
                                searchQuery = searchQuery,
                                onClick = {
                                    onNoteClick(item.note.id)
                                    searchQuery = ""
                                    isSearchActive = false
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }

                        // Показуємо індикатор, якщо є ще результати
                        if (filteredNotes.size == 5) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Показано перші 5 результатів",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Показуємо повідомлення, якщо немає результатів
                AnimatedVisibility(
                    visible = searchQuery.isNotBlank() && filteredNotes.isEmpty(),
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Нотатки не знайдено",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Спробуйте інші ключові слова",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Показуємо повідомлення, якщо користувач нічого не ввів
                AnimatedVisibility(
                    visible = searchQuery.isEmpty(),
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Введіть ключові слова для пошуку — пошук здійснюється в заголовках і вмісті нотаток.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                drawerItems.forEach { item ->
                    CustomNavigationDrawerItem(
                        item = item,
                        onItemClick = {
                            scope.launch {
                                onItemClick(item)
                                drawerState.close()
                            }
                        }
                    )

                    if (item.iconRes == R.drawable.ic_trash || item.iconRes == R.drawable.ic_settings) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp, horizontal = 25.dp))
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))


        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun getDrawerWidth(): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = configuration.screenWidthDp >= 600

    return if (isTablet) {
        320.dp
    } else {
        screenWidth * 0.8f
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    content: String,
    searchQuery: String,
    onClick: () -> Unit
) {


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },

        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {


            // Контент нотатки
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Заголовок нотатки
                HighlightedText(
                    text = title,
                    searchQuery = searchQuery,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // Превью контенту, якщо він є
                if (content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HighlightedText(
                        text = content,
                        searchQuery = searchQuery,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 5
                    )
                }
            }
        }
    }
}
