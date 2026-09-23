package com.pasich.encly.presentation.components.appbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.defaultButtonSize
import com.pasich.encly.ui.theme.heyHomeBar
import com.pasich.encly.ui.theme.horizontalDefault
import kotlinx.coroutines.launch

@Composable
fun HomeBar(
    drawerState: DrawerState,
    isGrid: Boolean,
    onToggleView: () -> Unit,
    showSortDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalDefault),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                },
            )
            {
                Icon(

                    painter = painterResource(R.drawable.ic_home_drawer),
                    modifier = Modifier
                        .size(defaultButtonSize),
                    contentDescription = stringResource(R.string.open_menu),
                )
            }

            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.home_greeting),
                style = heyHomeBar,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = showSortDialog,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sort),
                    modifier = Modifier
                        .size(defaultButtonSize),
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.sort),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            IconButton(onClick = onToggleView) {
                if (isGrid) {
                    Icon(
                        painter = painterResource(R.drawable.ic_grid),
                        modifier = Modifier
                            .size(defaultButtonSize),
                        contentDescription = stringResource(R.string.view_switch_to_list),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_list),
                        modifier = Modifier
                            .size(defaultButtonSize),
                        contentDescription = stringResource(R.string.view_switch_to_grid),
                    )
                }
            }
        }
    }
}
