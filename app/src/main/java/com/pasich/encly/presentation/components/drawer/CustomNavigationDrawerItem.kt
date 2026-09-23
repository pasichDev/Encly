package com.pasich.encly.presentation.components.drawer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pasich.encly.presentation.navigation.DrawerNavItem

@Composable
fun CustomNavigationDrawerItem(item: DrawerNavItem, onItemClick: () -> Unit, modifier: Modifier = Modifier) {
    NavigationDrawerItem(
        label = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            )
        },
        selected = false,
        onClick = { onItemClick() },
        icon = {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = item.title,
                modifier = Modifier.size(24.dp),
            )
        },
        badge = {
            item.badgeCount?.let { Text(text = it.toString()) }
        },
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
