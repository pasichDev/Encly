package com.pasich.encly.ui.screens

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.viewmodel.InlineSubtaskTarget
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Tasks screen in a real activity that is recreated (a rotation) or sent to the background
 * while a new sub-task is being typed into the list's inline field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TasksScreenRecreationTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val app by lazy { TestApp(context) }
    private lateinit var tasks: TasksViewModel
    private var scenario: ActivityScenario<TasksHostActivity>? = null

    @Before
    fun setUp() {
        app.withTasks(Task(id = 1, title = "Shop"))
        tasks = app.tasksVm()
        TasksHostActivity.viewModels = FakeViewModels(tasks)
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = TasksHostActivity::class.java.name
                packageName = context.packageName
            },
        )
        scenario = ActivityScenario.launch(TasksHostActivity::class.java)
        rule.waitForIdle()
        rule.runOnIdle {
            tasks.startAddingSubtask(1)
            tasks.onInlineTextChange("Mil")
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario?.close()
        TasksHostActivity.viewModels?.viewModelStore?.clear()
        TasksHostActivity.viewModels = null
    }

    @Test
    fun aRotationKeepsTheTypedSubtaskInItsField() {
        checkNotNull(scenario).recreate()
        rule.waitForIdle()

        // Nothing half-typed was saved, and the field is still open with what was typed.
        assertTrue(app.tasks.subtasks.value.isEmpty())
        assertEquals(InlineSubtaskTarget.Add(1), tasks.inlineEdit?.target)
        assertEquals("Mil", tasks.inlineEdit?.text)
    }

    @Test
    fun leavingTheAppSavesTheTypedSubtask() {
        checkNotNull(scenario).moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()

        assertEquals(listOf("Mil"), app.tasks.subtasks.value.map { it.title })
    }
}

/** Hosts [TasksScreen] with ViewModels that outlive the activity, as the back stack keeps them. */
class TasksHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = checkNotNull(viewModels)
        setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = NavRoutes.TasksRoute.name) {
                    composable(NavRoutes.TasksRoute.name) { ProvideViewModels(owner) { TasksScreen(nav) } }
                }
            }
        }
    }

    companion object {
        var viewModels: FakeViewModels? = null
    }
}
