package com.pasich.encly.presentation.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Компонент для отображения скелетона заголовка и блоков заметки при загрузке
 */
@Composable
fun NoteSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(30.dp)
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(12.dp))

        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Расширение для применения эффекта мерцания к любому модификатору
 */
@Composable
fun Modifier.shimmerEffect() = this.then(
    Modifier.background(
        ShimmerBrush()
    )
)
