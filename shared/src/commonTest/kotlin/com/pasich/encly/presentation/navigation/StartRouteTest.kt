package com.pasich.encly.presentation.navigation

import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.presentation.designsystem.AppCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartRouteTest {
    @Test
    fun anOpenVaultStartsOnHome() {
        assertEquals(NavRoutes.HomeRoute, startRoute(InitialStatus.MAIN))
        assertEquals(NavRoutes.HomeRoute, startRoute(InitialStatus.MAIN, RecoveryStage.NONE))
    }

    @Test
    fun anOpenVaultThatOwesAPinOrTheChoiceToKeepItStartsThere() {
        assertEquals(NavRoutes.PinCodeConfig, startRoute(InitialStatus.MAIN, RecoveryStage.MUST_SET_PIN))
        assertEquals(NavRoutes.RecoveryChoiceRoute, startRoute(InitialStatus.MAIN, RecoveryStage.CHOOSE))
    }

    @Test
    fun theRecoveryStageChangesNothingForAVaultThatIsNotOpen() {
        assertEquals(NavRoutes.LockRoute, startRoute(InitialStatus.AUTH, RecoveryStage.MUST_SET_PIN))
        assertEquals(NavRoutes.OnboardingRoute, startRoute(InitialStatus.ONBOARDING, RecoveryStage.CHOOSE))
        assertNull(startRoute(InitialStatus.NO, RecoveryStage.CHOOSE))
    }
}

class CommandAllowedTest {
    @Test
    fun anyCommandRunsWithTheVaultOpenAndNothingOwed() {
        AppCommand.entries.forEach { assertTrue(commandAllowed(it, vaultOpen = true, recovery = RecoveryStage.NONE)) }
    }

    @Test
    fun nothingRunsWithTheVaultClosed() {
        AppCommand.entries.forEach { assertFalse(commandAllowed(it, vaultOpen = false, recovery = RecoveryStage.NONE)) }
        assertFalse(commandAllowed(AppCommand.LockApp, vaultOpen = false, recovery = RecoveryStage.CHOOSE))
    }

    @Test
    fun whileARecoveryUnlockOwesAPinOnlyTheLockRuns() {
        listOf(RecoveryStage.CHOOSE, RecoveryStage.MUST_SET_PIN).forEach { stage ->
            assertTrue(commandAllowed(AppCommand.LockApp, vaultOpen = true, recovery = stage))
            AppCommand.entries.filter { it != AppCommand.LockApp }.forEach {
                assertFalse(commandAllowed(it, vaultOpen = true, recovery = stage), "$it at $stage")
            }
        }
    }
}
