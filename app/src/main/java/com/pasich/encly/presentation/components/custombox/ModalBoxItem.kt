package com.pasich.encly.presentation.components.custombox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.modalItemIconSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ModalBoxItem(
    title: String,
    modifier: Modifier = Modifier,
    roundPosition: RoundPosition = RoundPosition.Full,
    icon: Painter = painterResource(id = R.drawable.ic_about),
    active: Boolean = false,
    next: Boolean = false,
    confirmationRequest: Color? = null,
    action: () -> Unit = {},
    enable: Boolean = true,
    checked: Boolean = false,
) {
    var shapeRound by remember { mutableStateOf(getRoundPosition(roundPosition)) }
    var accentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    var confirmState by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    confirmationRequest?.let {
        accentColor = it.copy(alpha = 0.8f)
    }

    if (checked) {
        accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    }

    // One root: the card plus the gap that separates it from the next item of its group.
    Column(modifier = modifier) {
        ElevatedCard(
            shape = shapeRound,
            modifier = Modifier
                .clip(shapeRound)
                .then(
                    if (enable) {
                        Modifier.clickable {
                            if (confirmationRequest != null) {
                                if (confirmState) {
                                    action()
                                    confirmState = false
                                } else {
                                    confirmState = true
                                    coroutineScope.launch {
                                        delay(5000L)
                                        confirmState = false
                                    }
                                }
                            } else {
                                action()
                            }
                        }
                    } else {
                        Modifier // no click if disabled
                    },
                )
                .alpha(if (enable) 1f else 0.5f), // Visual feedback for disabled
            colors = if (active) {
                CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary)
            } else {
                CardDefaults.elevatedCardColors()
            },
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(shapeRound)
                    .fillMaxSize()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RenderBoxIcon(
                    if (checked) painterResource(id = R.drawable.ic_check_item) else icon,
                    active,
                    accentColor,
                    confirmState,
                )
                Spacer(modifier = Modifier.width(15.dp))
                Column(modifier = Modifier.weight(0.5f)) {
                    Text(
                        text = if (confirmState) stringResource(R.string.tap_again_to_delete) else title,
                        style = MaterialTheme.typography.titleMedium.copy(color = accentColor),
                    )
                }

                if (next) {
                    RenderBoxIcon(
                        painterResource(id = R.drawable.ic_arrow_right),
                        active,
                        accentColor,
                        false,
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(
                if (roundPosition == RoundPosition.Last || roundPosition == RoundPosition.Full) {
                    10.dp
                } else {
                    2.dp
                },
            ),
        )
    }
}

@Composable
private fun RenderBoxIcon(
    icon: Painter,
    reverseColors: Boolean,
    accentColor: Color,
    confirmState: Boolean,
    modifier: Modifier = Modifier
        .padding(4.dp)
        .scale(if (reverseColors) 0.8f else 1f)
        .size(modalItemIconSize),
) {
    if (confirmState) {
        CircularProgressIndicator(
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier,
        )
    } else {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = modifier,

        )
    }
}
