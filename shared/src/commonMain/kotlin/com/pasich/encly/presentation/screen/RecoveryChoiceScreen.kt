package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.recovery_choice_body_pin
import com.pasich.encly.core.resources.recovery_choice_title
import com.pasich.encly.core.resources.recovery_keep_pin
import com.pasich.encly.core.resources.recovery_set_new_pin
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.viewmodel.LockViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val CHOICE_SETTLED_POLL_MS = 1_000L

/**
 * After the recovery phrase unlocked a vault whose PIN still works: keep it, which is the default, or
 * set a new PIN. The vault is open meanwhile, but there is no way past this page except one of the two, so a
 * forgotten PIN is replaced only by the user's own choice. A PIN that cannot be used any more skips this page: the
 * new-PIN screen comes straight up (see [LockScreen]).
 */
@Composable
fun RecoveryChoiceScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = metroViewModel(),
) {
    BackHandler(enabled = true) { }
    // Another window may settle the choice first: this one goes on into the app with it.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(CHOICE_SETTLED_POLL_MS)
            if (viewModel.recoveryStage() == RecoveryStage.NONE && !viewModel.isSessionLocked()) {
                navController.goHome()
                break
            }
        }
    }
    PinEntryScaffold(
        title = stringResource(Res.string.recovery_choice_title),
        subtitle = stringResource(Res.string.recovery_choice_body_pin),
        icon = EnclyIcons.Key,
        modifier = modifier,
        footer = {
            EnclyButton(
                text = stringResource(Res.string.recovery_keep_pin),
                onClick = {
                    viewModel.keepAfterRecovery()
                    navController.goHome()
                },
            )
            EnclyTextButton(
                text = stringResource(Res.string.recovery_set_new_pin),
                onClick = {
                    navController.navigate(NavRoutes.PinCodeConfig.name) {
                        popUpTo(NavRoutes.RecoveryChoiceRoute.name) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { }
}

private fun NavHostController.goHome() {
    navigate(NavRoutes.HomeRoute.name) {
        popUpTo(NavRoutes.RecoveryChoiceRoute.name) { inclusive = true }
    }
}
