package com.pasich.encly.presentation.components.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Waypoints
import com.pasich.encly.R
import com.pasich.encly.core.security.AuthType

/**
 * v2 has one mandatory day-to-day authorization factor: the PIN.
 * Recovery seed and biometric are independent DEK unlock slots, not weaker/stronger "modes".
 */
@Composable
fun AuthMethodSelector(selected: AuthType, onSelect: (AuthType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))
        PinAuthMethodCard(
            selected = selected == AuthType.PIN,
            onClick = { onSelect(AuthType.PIN) },
        )
    }
}

@Composable
private fun PinAuthMethodCard(selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Waypoints,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(15.dp))
            PinAuthMethodText(selected = selected, contentColor = contentColor)
        }
    }
}

@Composable
private fun PinAuthMethodText(selected: Boolean, contentColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = stringResource(R.string.auth_method_pin_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
        Text(
            text = stringResource(R.string.auth_method_pin_desc),
            fontSize = 14.sp,
            color = if (selected) {
                contentColor.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
