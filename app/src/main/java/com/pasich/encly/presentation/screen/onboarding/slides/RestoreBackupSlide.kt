package com.pasich.encly.presentation.screen.onboarding.slides

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.presentation.screen.backup.RecoveryPhraseField
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel

/**
 * First-run "Restore from backup": pick the encrypted file, then type the 12 words it was made
 * with. The vault is created with that phrase and the backup is written after PIN setup.
 */
@Composable
fun RestoreBackupSlide(
    uiState: OnboardingViewModel.OnboardingUiState,
    onPickFile: () -> Unit,
    onRestore: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var phrase by remember { mutableStateOf("") }

    BackHandler(onBack = onCancel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            imageVector = Lucide.ArchiveRestore,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_restore_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_restore_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (!uiState.restoreFileReady) {
            Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_restore_pick))
            }
        } else {
            Text(
                text = stringResource(R.string.backup_enter_phrase_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            RecoveryPhraseField(value = phrase, onValueChange = { phrase = it })
            Button(
                onClick = { onRestore(phrase) },
                enabled = phrase.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.restore))
            }
            OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_restore_other_file))
            }
        }

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.back))
        }
    }
}

/**
 * The Storage Access Framework "open document" picker for backup files. [onUnavailable] runs
 * instead of a crash on a device with no documents app to handle it.
 */
@Composable
fun rememberBackupFilePicker(onPick: (android.net.Uri?) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onPick)
    return {
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}
