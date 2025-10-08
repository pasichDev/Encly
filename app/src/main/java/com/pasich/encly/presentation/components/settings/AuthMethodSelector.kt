package com.pasich.encly.presentation.components.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.LockKeyholeOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Waypoints
import com.pasich.encly.core.security.AuthType


@Composable
fun AuthMethodSelector(
    selected: AuthType, onSelected: (AuthType) -> Unit, isDisableSeedPhase: Boolean = false
) {
    val options = buildList {
        add(AuthType.NONE to "Низький захист")
        add(AuthType.PIN to "Середній захист")
        if (!isDisableSeedPhase) {
            add(AuthType.SEED_PHRASE to "Високий захист")
        }
    }


    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))

        options.forEach { (authType, description) ->
            val isSelected = selected == authType

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelected(authType) }
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (authType) {
                            AuthType.NONE -> Lucide.LockKeyholeOpen
                            AuthType.PIN -> Lucide.Waypoints
                            AuthType.SEED_PHRASE -> Lucide.Key
                        },
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(15.dp))
                    Column {
                        Text(
                            text = when (authType) {
                                AuthType.NONE -> "Без авторизації"
                                AuthType.PIN -> "ПІН-код"
                                AuthType.SEED_PHRASE -> "Сід-фраза"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
