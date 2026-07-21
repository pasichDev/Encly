package com.pasich.encly.presentation.screen

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.dialogs.RequestDeleteTagsDialog
import com.pasich.encly.presentation.viewmodel.TagListEvent
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

enum class TagEditTypeAction {
    EDIT, VISIBLE, DELETE
}

// Focus management state
@Composable
private fun rememberFocusManager() = LocalFocusManager.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTagScreen(navController: NavHostController) {
    val focusManager = rememberFocusManager()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Clear focus when clicking outside
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_tags)) },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { padding ->
        ListTagsEdit(
            modifier = Modifier.padding(padding),
            globalFocusManager = focusManager,
            keyboardController = keyboardController
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun ListTagsEdit(
    modifier: Modifier,
    viewModel: TagListViewModel = hiltViewModel(),
    globalFocusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var tagDelete by remember { mutableStateOf(Tag()) }
    val listState = rememberLazyListState()
    rememberCoroutineScope()

    // Track currently focused item to prevent multiple focuses
    var currentlyFocusedItemId by remember { mutableStateOf<Long?>(null) }

    // Clear focus when dialog is shown
    LaunchedEffect(showDialog) {
        if (showDialog) {
            globalFocusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    AnimatedVisibility(
        visible = state.baseState.isLoading,
        enter = fadeIn(initialAlpha = 0.3f),
        exit = fadeOut(targetAlpha = 0f)
    ) {
        Box(
            modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    AnimatedVisibility(
        visible = !state.baseState.isLoading,
        enter = fadeIn(
            initialAlpha = 0f,
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = 100,
                easing = FastOutSlowInEasing
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 150,
                easing = LinearEasing
            )
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Clear focus when clicking empty space
                    globalFocusManager.clearFocus()
                    keyboardController?.hide()
                    currentlyFocusedItemId = null
                }
        ) {
            NewTagItem(
                item = "",
                onSave = {
                    viewModel.onEvent(TagListEvent.AddTag(it))
                    currentlyFocusedItemId = null
                },
                globalFocusManager = globalFocusManager,
                keyboardController = keyboardController,
                onFocusChange = { isFocused ->
                    if (isFocused) {
                        currentlyFocusedItemId = -1 // Special ID for new tag item
                    }
                }
            )

            val hapticFeedback = LocalHapticFeedback.current
            val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                // Clear focus during reordering
                globalFocusManager.clearFocus()
                keyboardController?.hide()
                currentlyFocusedItemId = null

                viewModel.onEvent(TagListEvent.ReorderTagsLive(from.index, to.index))
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(state.listTags, key = { _, item -> item.id }) { index, item ->
                    ReorderableItem(reorderableLazyListState, key = item.id) { isDragging ->
                        TagEditItem(
                            item = item,
                            onAction = { action, tag ->
                                when (action) {
                                    TagEditTypeAction.EDIT -> {
                                        viewModel.onEvent(TagListEvent.UpdateTag(tag))
                                        currentlyFocusedItemId = null
                                    }

                                    TagEditTypeAction.VISIBLE -> {
                                        viewModel.onEvent(TagListEvent.ToggleVisibleTag(tag))
                                    }

                                    TagEditTypeAction.DELETE -> {
                                        globalFocusManager.clearFocus()
                                        keyboardController?.hide()
                                        currentlyFocusedItemId = null
                                        tagDelete = tag
                                        showDialog = true
                                    }
                                }
                            },
                            isDragging = isDragging,
                            globalFocusManager = globalFocusManager,
                            keyboardController = keyboardController,
                            onFocusChange = { isFocused ->
                                if (isFocused) {
                                    currentlyFocusedItemId = item.id
                                }
                            },
                            shouldPreventFocus = currentlyFocusedItemId != null && currentlyFocusedItemId != item.id
                        )
                    }
                }
            }
        }

        RequestDeleteTagsDialog(
            showDialog = showDialog,
            onDismiss = { showDialog = false },
            onConfirm = {
                viewModel.onEvent(TagListEvent.DeleteTag(tagDelete))
                tagDelete = Tag()
                showDialog = false
            })
    }
}


@Composable
fun NewTagItem(
    item: String,
    onSave: (Tag) -> Unit,
    globalFocusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onFocusChange: (Boolean) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(item)) }
    val focusRequester = remember { FocusRequester() }
    var isTextFieldEnabled by remember { mutableStateOf(false) }

    // Handle focus changes
    LaunchedEffect(isTextFieldEnabled) {
        onFocusChange(isTextFieldEnabled)
    }

    fun handleSave() {
        if (text.text.isNotBlank()) {
            onSave(Tag(nameTag = text.text.trim()))
            text = TextFieldValue("")
        }
        isTextFieldEnabled = false
        globalFocusManager.clearFocus()
        keyboardController?.hide()
    }

    fun handleCancel() {
        text = TextFieldValue("")
        isTextFieldEnabled = false
        globalFocusManager.clearFocus()
        keyboardController?.hide()
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (isTextFieldEnabled) MaterialTheme.colorScheme.outlineVariant
                    else Color.Transparent
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { handleSave() }),
                visualTransformation = VisualTransformation.None,
                onValueChange = { newValue ->
                    val filteredText = newValue.text.take(20)
                    text = newValue.copy(text = filteredText)
                },
                leadingIcon = {
                    Row {
                        Spacer(Modifier.width(3.dp))
                        IconButton(onClick = {
                            if (isTextFieldEnabled) {
                                handleCancel()
                            } else {
                                isTextFieldEnabled = true
                            }
                        }) {
                            if (isTextFieldEnabled) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Cancel New",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(0.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = "Add Tag",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(0.dp),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 0.dp, horizontal = 0.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isTextFieldEnabled = focusState.isFocused
                    },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 1,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                label = { Text(stringResource(R.string.create_new_tag)) },
                trailingIcon = {
                    if (isTextFieldEnabled) {
                        IconButton(onClick = { handleSave() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                })
            Spacer(modifier = Modifier.width(20.dp))
        }

        if (isTextFieldEnabled) {
            LaunchedEffect(isTextFieldEnabled) {
                text = text.copy(selection = TextRange(text.text.length))
                focusRequester.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (isTextFieldEnabled) MaterialTheme.colorScheme.outlineVariant
                    else Color.Transparent
                )
        )
    }
}


@Composable
fun TagEditItem(
    item: Tag,
    onAction: (TagEditTypeAction, Tag) -> Unit,
    isDragging: Boolean = false,
    globalFocusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onFocusChange: (Boolean) -> Unit,
    shouldPreventFocus: Boolean
) {
    var text by remember { mutableStateOf(TextFieldValue(item.nameTag)) }
    val focusRequester = remember { FocusRequester() }
    var isTextFieldEnabled by remember { mutableStateOf(false) }
    rememberCoroutineScope()

    // Sync text with item changes
    LaunchedEffect(item.nameTag) {
        text = TextFieldValue(item.nameTag)
    }

    // Handle focus changes
    LaunchedEffect(isTextFieldEnabled) {
        onFocusChange(isTextFieldEnabled)
    }

    // Prevent focus if another item is focused
    LaunchedEffect(shouldPreventFocus) {
        if (shouldPreventFocus && isTextFieldEnabled) {
            isTextFieldEnabled = false
            globalFocusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun handleSave() {
        if (text.text.isNotBlank()) {
            item.nameTag = text.text.trim()
            onAction(TagEditTypeAction.EDIT, item)
        }
        isTextFieldEnabled = false
        globalFocusManager.clearFocus()
        keyboardController?.hide()
    }

    fun handleDelete() {
        isTextFieldEnabled = false
        globalFocusManager.clearFocus()
        keyboardController?.hide()
        onAction(TagEditTypeAction.DELETE, item)
    }

    fun handleEnableEdit() {
        if (!shouldPreventFocus) {
            isTextFieldEnabled = true
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (isTextFieldEnabled) MaterialTheme.colorScheme.outlineVariant
                    else Color.Transparent
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                    else Color.Transparent
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTextFieldEnabled) {
                TextField(
                    value = text,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { handleSave() }),
                    visualTransformation = VisualTransformation.None,
                    onValueChange = { newValue ->
                        val filteredText = newValue.text.replace(" ", "").take(20)
                        text = newValue.copy(text = filteredText)
                    },
                    leadingIcon = {
                        IconButton(onClick = { handleDelete() }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Tag",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(0.dp),
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 0.dp, horizontal = 0.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isTextFieldEnabled = focusState.isFocused
                        },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onBackground,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    maxLines = 1,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    trailingIcon = {
                        IconButton(onClick = { handleSave() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    })
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                ) {

                    // Icon with a drag handle
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(24.dp)
                        //  .draggableHandle()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drag),
                            contentDescription = "Drag to reorder",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(15.dp))
                    Text(
                        text = item.nameTag,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = { onAction(TagEditTypeAction.VISIBLE, item) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            painter = if (item.isVisible) painterResource(R.drawable.ic_visible) else painterResource(
                                R.drawable.ic_unvisible
                            ),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            contentDescription = "Visible Tag"
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    IconButton(
                        onClick = { handleEnableEdit() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit, contentDescription = "Edit Tag"
                        )
                    }
                }
            }
        }

        if (isTextFieldEnabled && !shouldPreventFocus) {
            LaunchedEffect(isTextFieldEnabled) {
                text = text.copy(selection = TextRange(text.text.length))
                focusRequester.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (isTextFieldEnabled) MaterialTheme.colorScheme.outlineVariant
                    else Color.Transparent
                )
        )
    }
}