package com.pasich.encly.presentation.components.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyCardStyle
import com.pasich.encly.presentation.designsystem.EnclyProgressTrack
import com.pasich.encly.ui.theme.EnclyTheme

private const val PERCENT = 100f
private const val PROGRESS_ANIMATION_MS = 250

/** Task progress: an outlined card with the share done, a continuous track and the counts. */
@Composable
fun TaskProgressCard(
    completionPercentage: Int,
    completedTasksCount: Int,
    totalTasksCount: Int,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = completionPercentage / PERCENT,
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MS),
        label = "progress",
    )
    EnclyCard(modifier = modifier, style = EnclyCardStyle.OUTLINED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.task_progress_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.percent_value, completionPercentage),
                style = EnclyTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        EnclyProgressTrack(progress = animatedProgress)
        Text(
            text = pluralStringResource(
                R.plurals.task_progress_summary,
                totalTasksCount,
                completedTasksCount,
                totalTasksCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
