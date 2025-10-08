package com.pasich.encly.presentation.drawer

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveDrawerSheet(
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // For example: if the screen < 600dp - drawer in full width
    val drawerWidthModifier = if (screenWidthDp < 600) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(320.dp) // standard width for tablets/large screens
    }

    ModalDrawerSheet(
        drawerShape = RectangleShape, modifier = drawerWidthModifier
    ) {
        content()
    }
}
