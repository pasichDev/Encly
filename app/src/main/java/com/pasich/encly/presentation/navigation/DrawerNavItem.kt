package com.pasich.encly.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/** A drawer destination. [groupEnd] draws a hairline after it. */
data class DrawerNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int? = null,
    val groupEnd: Boolean = false,
)
