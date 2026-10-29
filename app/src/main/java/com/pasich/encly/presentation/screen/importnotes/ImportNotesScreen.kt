package com.pasich.encly.presentation.screen.importnotes

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.import_importing
import com.pasich.encly.core.resources.import_reading
import com.pasich.encly.core.resources.import_title
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.viewmodel.ImportNotesViewModel
import com.pasich.encly.presentation.viewmodel.ImportStep
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Settings → Import notes. Works in a decoy session too: it imports into whichever vault is
 * open. The files are another app's, so the picker is opened with the vault kept open for it.
 */
@Composable
fun ImportNotesScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: ImportNotesViewModel = metroViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        viewModel::onFilesPicked,
    )
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onFolderPicked)
    val actions = remember(viewModel, files, folder) {
        ImportNotesActions(
            onChooseFiles = { launchPicker(viewModel) { files.launch(arrayOf("*/*")) } },
            onChooseFolder = { launchPicker(viewModel) { folder.launch(null) } },
            onImport = viewModel::confirm,
            onCancel = viewModel::cancel,
            onChooseSource = viewModel::chooseSource,
            onToggle = viewModel::toggle,
            onToggleAll = viewModel::toggleAll,
            onFilter = viewModel::setFilter,
            onExpand = viewModel::expand,
        )
    }
    // The import is one short transaction; leaving in the middle would only roll it back.
    BackHandler(enabled = step == ImportStep.Importing) {}
    BackHandler(enabled = step != ImportStep.Start && step != ImportStep.Importing) { viewModel.cancel() }
    ImportNotesContent(
        step = step,
        actions = actions,
        onBack = { navController.popBackStack() },
        modifier = modifier,
    )
}

/** Keeps the vault open for the picker only once it has really started (no picker app: a message). */
private fun launchPicker(viewModel: ImportNotesViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        viewModel.onPickerUnavailable()
        return
    }
    viewModel.onPickerLaunched()
}

/** The page for a [step]; stateless, so every state can be shown and tested on its own. */
@Composable
fun ImportNotesContent(
    step: ImportStep,
    actions: ImportNotesActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (step) {
        ImportStep.Reading -> AuthLoading(stringResource(Res.string.import_reading), modifier)

        ImportStep.Importing -> AuthLoading(stringResource(Res.string.import_importing), modifier)

        is ImportStep.Preview -> ImportReviewScreen(step.preview, step.review, actions, modifier)

        else -> Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                EnclyTopBar(
                    title = stringResource(Res.string.import_title),
                    onBack = if (step == ImportStep.Start) onBack else actions.onCancel,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
            ) {
                when (step) {
                    ImportStep.Start -> StartPage(actions)
                    ImportStep.ChooseSource -> ChooseSourcePage(actions)
                    is ImportStep.Done -> DonePage(step.result, actions)
                    is ImportStep.Failed -> FailedPage(step.failure, actions)
                }
            }
        }
    }
}
