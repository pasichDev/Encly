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
 * Base interface for all drawer panel contents
 */
sealed interface DrawerContent

/**
 * Main drawer panel content with navigation elements
 */
object MainDrawerContent : DrawerContent

/**
 * Drawer panel content for translating text
 */
object TranslateDrawerContent : DrawerContent

/**
 * Component that displays a header with a back button for the contents
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
 * Main content for the translation screen
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

        // Translation content will be added in the implementation
        Text(
            text = "Функция перевода пока не реализована", modifier = Modifier.padding(16.dp)
        )
    }
}
