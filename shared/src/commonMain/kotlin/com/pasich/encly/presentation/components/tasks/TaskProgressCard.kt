package com.pasich.encly.presentation.components.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.percent_value
import com.pasich.encly.core.resources.task_progress_summary
import com.pasich.encly.presentation.designsystem.EnclyProgressTrack
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/** Task progress on one line: how many are done, a continuous track, and the share done. */
@Composable
fun TaskProgressCard(
    completionPercentage: Int,
    completedTasksCount: Int,
    totalTasksCount: Int,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = completionPercentage / PERCENT,
        animationSpec = EnclyMotion.medium(),
        label = "progress",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier,
    ) {
        Text(
            text = pluralStringResource(
                Res.plurals.task_progress_summary,
                totalTasksCount,
                completedTasksCount,
                totalTasksCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyProgressTrack(progress = animatedProgress, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.percent_value, completionPercentage),
            style = EnclyTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
