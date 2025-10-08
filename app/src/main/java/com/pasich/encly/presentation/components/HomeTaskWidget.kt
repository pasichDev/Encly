package com.pasich.encly.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.ui.theme.titleNoteCard


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTaskWidget(
    onTasksClick: () -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val quotes = stringArrayResource(id = R.array.quotes_array)
    val randomQuote = remember { quotes.random() }


    Card(
        onClick = onTasksClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {

            Image(
                painter = painterResource(id = R.drawable.checklist),
                contentDescription = "Tasks",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )

            Column {
                Text(
                    text = if (uiState.activeTasksCount > 0) {
                        "У вас ${uiState.activeTasksCount} активних завдань"
                    } else {
                        "Ваш список завдань порожній"
                    },
                    style = titleNoteCard
                )
                Text(
                    text = randomQuote,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
