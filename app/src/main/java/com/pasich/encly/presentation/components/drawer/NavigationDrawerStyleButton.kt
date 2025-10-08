package com.pasich.encly.presentation.components.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

data class StyleButton(
    var name: String,
    var isSelected: Boolean = false,
    var fontTitle: FontFamily,
    var fontBody: FontFamily,
)


@Composable
fun NavigationDrawerStyleButton(
    styleButton: StyleButton,
    onClick: () -> Unit = {}
) {
    if (styleButton.isSelected) {
        Button(
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 10.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Aa",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = styleButton.fontTitle)
                )
                Text(
                    styleButton.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = styleButton.fontBody)
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 10.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Aa",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = styleButton.fontTitle)
                )
                Text(
                    styleButton.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = styleButton.fontBody)
                )
            }
        }
    }
}