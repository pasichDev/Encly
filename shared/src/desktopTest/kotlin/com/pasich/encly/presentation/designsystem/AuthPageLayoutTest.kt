package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.presentation.screen.LockActions
import com.pasich.encly.presentation.screen.LockCapabilities
import com.pasich.encly.presentation.screen.LockChecks
import com.pasich.encly.presentation.screen.LockFormState
import com.pasich.encly.presentation.screen.LockForms
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.presentation.screen.pincode.PassphraseStep
import com.pasich.encly.presentation.screen.pincode.PassphraseStepActions
import com.pasich.encly.presentation.screen.pincode.PassphraseStepStyle
import com.pasich.encly.presentation.screen.pincode.PassphraseStepText
import com.pasich.encly.ui.theme.DensityProfile
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalDensityProfile
import com.pasich.encly.ui.theme.LocalPointerFirst
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first-run and lock pages on a desktop window: a card as wide as a form, the primary button right under the
 * field. On a touch host, or in a window narrower than medium, they keep the phone layout (the button at the foot of
 * the window). Sizes are in dp: the test density is 1.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AuthPageLayoutTest {
    private fun text(res: StringResource): String = runBlocking { getString(res) }

    private fun page(
        width: Int,
        height: Int,
        pointerFirst: Boolean,
        content: @Composable () -> Unit,
        check: DesktopComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width, height) {
        setContent {
            val widthClass = WidthClass.of(width.dp, height.dp)
            CompositionLocalProvider(
                LocalWidthClass provides widthClass,
                LocalDensityProfile provides DensityProfile.of(widthClass, pointerFirst),
                LocalPointerFirst provides pointerFirst,
            ) { EnclyTheme(settings = ThemeSettings(), dark = false) { AuthPage(content = content) } }
        }
        waitForIdle()
        check()
    }

    private val lockScreen: @Composable () -> Unit = {
        LockForms(
            form = LockFormState(),
            busy = false,
            capabilities = LockCapabilities(
                biometricEnabled = false,
                recoveryAvailable = true,
                secretKind = SecretKind.PASSWORD,
            ),
            actions = LockActions(
                checks = LockChecks(pin = { _, _ -> }, seed = { _, _ -> }),
                lockoutRemainingMillis = { 0L },
                onUnlock = {},
                onRecoveryUnlock = {},
                onKeyLoss = {},
                onPromptBiometric = {},
            ),
        )
    }

    private val passphraseStep: @Composable () -> Unit = {
        PassphraseStep(
            text = PassphraseStepText(
                title = "New",
                subtitle = "Type it",
                label = text(Res.string.passphrase_label),
                submit = "Go",
            ),
            input = PassphraseInput(),
            actions = PassphraseStepActions(onSubmit = {}),
            style = PassphraseStepStyle(compact = false, inCard = authCardApplies()),
        )
    }

    private fun DesktopComposeUiTest.field(): DpRect =
        onNodeWithContentDescription(text(Res.string.passphrase_label)).getBoundsInRoot()

    private fun DesktopComposeUiTest.button(label: String): DpRect =
        onNode(hasText(label) and hasClickAction()).getBoundsInRoot()

    private fun gap(field: DpRect, button: DpRect): Dp = button.top - field.bottom

    @Test
    fun theLockScreenOfADesktopWindowIsACardWithUnlockUnderTheField() =
        page(width = 1280, height = 800, pointerFirst = true, content = lockScreen) {
            val field = field()
            val unlock = button(text(Res.string.lock_unlock))

            assertTrue(gap(field, unlock) in 0.dp..CLOSE, "Unlock is ${gap(field, unlock)} under the field")
            assertTrue(field.width <= FORM_MAX, "the form is ${field.width} wide")
            assertTrue(unlock.width <= FORM_MAX, "the button is ${unlock.width} wide")
            assertTrue(unlock.bottom < 800.dp - FAR_FROM_EDGE, "the button is pinned to the window's foot")
            assertEquals(640f, (field.left + field.right).value / 2, 1f)
        }

    @Test
    fun aPassphraseStepOfADesktopWindowHasItsButtonUnderTheField() =
        page(width = 1280, height = 800, pointerFirst = true, content = passphraseStep) {
            val field = field()
            val submit = button("Go")

            assertTrue(gap(field, submit) in 0.dp..CLOSE, "Go is ${gap(field, submit)} under the field")
            assertTrue(field.width <= FORM_MAX, "the form is ${field.width} wide")
            assertTrue(submit.bottom < 800.dp - FAR_FROM_EDGE, "the button is pinned to the window's foot")
        }

    @Test
    fun aTouchHostKeepsThePhoneLayoutInAWideWindow() =
        page(width = 1280, height = 800, pointerFirst = false, content = lockScreen) {
            val field = field()
            val unlock = button(text(Res.string.lock_unlock))

            assertTrue(gap(field, unlock) > FAR, "Unlock is only ${gap(field, unlock)} under the field")
            assertTrue(field.width > FORM_MAX, "the form is only ${field.width} wide")
            assertTrue(unlock.bottom > 800.dp - FAR_FROM_EDGE, "the button is not at the window's foot")
        }

    @Test
    fun aCompactDesktopWindowKeepsThePhoneLayout() =
        page(width = 500, height = 800, pointerFirst = true, content = lockScreen) {
            val field = field()
            val unlock = button(text(Res.string.lock_unlock))

            assertTrue(gap(field, unlock) > FAR, "Unlock is only ${gap(field, unlock)} under the field")
            assertTrue(unlock.bottom > 800.dp - FAR_FROM_EDGE, "the button is not at the window's foot")
        }

    @Test
    fun aLowDesktopWindowKeepsThePhoneLayoutToo() =
        page(width = 1280, height = 400, pointerFirst = true, content = passphraseStep) {
            // Lower than the wide layouts take (a phone held sideways): today's page, whatever the pointer.
            assertTrue(button("Go").bottom > 400.dp - FAR_FROM_EDGE)
        }

    private companion object {
        /** Under the field "directly": a form's gaps, far less than the phone page's. */
        val CLOSE = 80.dp

        /** The phone page leaves at least this between the field and the button in a window this high. */
        val FAR = 200.dp

        /** The widest a form of the card is, narrower than the old 720 dp page. */
        val FORM_MAX = 600.dp

        /** A button within this of the window's foot is pinned to it. */
        val FAR_FROM_EDGE = 120.dp
    }
}
