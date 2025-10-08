package com.pasich.encly.presentation.dialogs.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Базовый интерфейс для всех контентов боковой панели
 */
sealed interface DrawerContent

/**
 * Основной контент боковой панели с навигационными элементами
 */
object MainDrawerContent : DrawerContent

/**
 * Контент боковой панели для копирования текста
 */
object CopyDrawerContent : DrawerContent

/**
 * Контент боковой панели для перевода текста
 */
object TranslateDrawerContent : DrawerContent

/**
 * Компонент отображающий заголовок с кнопкой назад для контентов
 */
@Composable
fun DrawerHeader(
    title: String, onBackClick: () -> Unit, onCloseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack, contentDescription = "Назад"
            )
        }

        Text(
            text = title, modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        IconButton(onClick = onCloseClick) {
            Icon(
                imageVector = Icons.Default.Close, contentDescription = "CloseDrawer"
            )
        }


    }
}


/**
 * Основной контент для экрана перевода
 */
@Composable
fun TranslateContent(
    onBackClick: () -> Unit, onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        DrawerHeader(
            title = "Перевод текста", onBackClick = onBackClick, onCloseClick = onCloseClick
        )

        // Контент для перевода будет добавлен в реализации
        Text(
            text = "Функция перевода пока не реализована", modifier = Modifier.padding(16.dp)
        )
    }
}
