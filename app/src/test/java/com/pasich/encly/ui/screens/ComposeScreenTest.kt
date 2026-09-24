package com.pasich.encly.ui.screens

import android.app.Application
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * Base for the Compose screen tests. They run on the JVM under Robolectric, on a phone-sized
 * screen, with a plain Application (no Hilt graph, no keystore). Every `hiltViewModel()` in the
 * composition resolves to a ViewModel the test built from fakes (see [FakeViewModels]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ComposeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    private val owners = mutableListOf<FakeViewModels>()

    @After
    fun clearViewModels() {
        owners.forEach { it.viewModelStore.clear() }
    }

    protected fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    protected fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)

    protected fun viewModels(vararg viewModels: ViewModel): FakeViewModels =
        FakeViewModels(*viewModels).also(owners::add)

    /** [content] in the app theme, with [owner] answering every `hiltViewModel()`. */
    protected fun setScreen(
        owner: FakeViewModels = viewModels(),
        settings: ThemeSettings = ThemeSettings(),
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            EnclyTheme(settings = settings, dark = dark) {
                ProvideViewModels(owner, content)
            }
        }
        rule.waitForIdle()
    }

    protected lateinit var nav: NavHostController

    /**
     * [content] as the destination [route] of a graph that has every app route, the others as
     * stubs, so the screen can navigate and a test can see where it went ([currentRoute]).
     * [start] is where the back stack begins; tests reach [route] with [navigateTo] when it differs.
     */
    protected fun setNavScreen(
        owner: FakeViewModels = viewModels(),
        route: String,
        start: String = route,
        settings: ThemeSettings = ThemeSettings(),
        dark: Boolean = false,
        liveTheme: StateFlow<ThemeSettings>? = null,
        content: @Composable (NavHostController) -> Unit,
    ) {
        rule.setContent {
            val controller = rememberNavController()
            nav = controller
            // With [liveTheme] the app theme follows the stored settings, as AppTheme does.
            val theme = liveTheme?.collectAsState()?.value ?: settings
            EnclyTheme(settings = theme, dark = if (liveTheme != null) theme.isDark(false) else dark) {
                NavHost(navController = controller, startDestination = start) {
                    TestRoutes.patterns.filter { it.pattern != route }.forEach { stub ->
                        composable(stub.pattern, arguments = stub.arguments) {
                            Text("stub:${stub.name}")
                        }
                    }
                    composable(route, arguments = TestRoutes.argumentsFor(route)) {
                        ProvideViewModels(owner) { content(controller) }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    protected fun navigateTo(route: String) {
        rule.runOnIdle { nav.navigate(route) }
        rule.waitForIdle()
    }

    /** The route pattern of the destination on top, e.g. `HomeRoute`. */
    protected fun currentRoute(): String? = rule.runOnIdle { nav.currentBackStackEntry?.destination?.route }

    /** The route's name without its arguments, e.g. `TasksRoute` for `TasksRoute?add={add}`. */
    protected fun currentRouteName(): String? = currentRoute()?.substringBefore('?')?.substringBefore('/')

    /**
     * The screen shows real content, not a blank body: at least [minTexts] visible texts
     * besides the top bar title, inside the window.
     */
    protected fun assertBodyNotEmpty(minTexts: Int = 2) {
        val root = rule.onRoot().fetchSemanticsNode()
        val window = root.boundsInRoot
        val texts = visibleTexts().filter { node ->
            val bounds = node.boundsInRoot
            bounds.height > 0f && bounds.width > 0f && bounds.overlaps(window)
        }
        assertTrue("expected at least $minTexts visible texts, found ${texts.map(::textOf)}", texts.size >= minTexts)
    }

    protected fun visibleTexts(): List<SemanticsNode> = rule.onAllNodes(hasAnyText, useUnmergedTree = true)
        .fetchSemanticsNodes()

    protected fun textOf(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString { it.text }
        ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
        ?: ""

    /**
     * Waits for [condition], letting both real threads (the ViewModels' IO and Default work) and
     * the Compose clock (animations and `delay` in effects) move on.
     */
    protected fun waitFor(timeoutMillis: Long = WAIT_MS, describe: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >
                deadline
            ) {
                throw AssertionError("condition not met in $timeoutMillis ms ${describe()}")
            }
            rule.mainClock.advanceTimeBy(FRAME_MS)
            rule.waitForIdle()
            Thread.sleep(POLL_MS)
        }
        rule.waitForIdle()
    }

    protected fun waitForText(
        text: String,
        substring: Boolean = false,
        ignoreCase: Boolean = false,
        timeoutMillis: Long = WAIT_MS,
    ) {
        waitFor(timeoutMillis, describe = { "for \"$text\"; shown: ${visibleTexts().map(::textOf)}" }) {
            countText(text, substring, ignoreCase) > 0
        }
    }

    protected fun countText(text: String, substring: Boolean = false, ignoreCase: Boolean = false): Int =
        rule.onAllNodes(hasText(text, substring = substring, ignoreCase = ignoreCase), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    /** Taps [pin] on the on-screen keypad. */
    protected fun typePin(pin: String) {
        pin.forEach { digit ->
            rule.onNode(hasText(digit.toString()) and hasClickAction()).performClick()
            rule.waitForIdle()
        }
    }

    /** The recovery phrase cell [number] (1-based) of 12. */
    protected fun phraseCell(number: Int) = rule.onNodeWithContentDescription(
        str(com.pasich.encly.R.string.recovery_word_cell, number, PHRASE_WORDS),
    )

    /** What the 12 cells hold, in order. */
    protected fun phraseCells(): List<String> = (1..PHRASE_WORDS).map { number ->
        phraseCell(number).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    }

    /**
     * Brings [text] into view: in a scrolling column it is already composed; in a lazy list the
     * first scrollable list is scrolled until it is.
     */
    protected fun scrollToText(text: String, substring: Boolean = false, ignoreCase: Boolean = false) {
        val matcher = hasText(text, substring = substring, ignoreCase = ignoreCase)
        if (rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(matcher)
        } else {
            rule.onAllNodes(matcher)[0].performScrollTo()
        }
        rule.waitForIdle()
    }

    protected companion object {
        const val WAIT_MS = 5_000L
        const val PHRASE_WORDS = 12
        const val FRAME_MS = 16L
        const val POLL_MS = 5L

        /** A valid BIP39 phrase (its checksum holds). */
        val PHRASE = (List(11) { "abandon" } + "about").joinToString(" ")

        val hasAnyText = SemanticsMatcher("has text") {
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.isNotBlank() } ||
                !it.config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrBlank()
        }
    }
}

/**
 * A [ViewModelStoreOwner] holding ready-made ViewModels. It deliberately has no default factory:
 * `hiltViewModel()` then asks the store directly and gets the instance put here.
 */
class FakeViewModels(vararg viewModels: ViewModel) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    init {
        viewModels.forEach { add(it) }
    }

    fun add(viewModel: ViewModel) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = viewModel as T
        }
        ViewModelProvider.create(viewModelStore, factory)[viewModel::class]
    }
}

@Composable
fun ProvideViewModels(owner: FakeViewModels, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
