package com.pasich.encly.ui.screens

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.TASKS_ADD_ARG

/** The app's route patterns, as NavHost registers them, for the test graph. */
internal object TestRoutes {
    class Route(val name: String, val pattern: String, val arguments: List<NamedNavArgument>)

    val TASKS = "${NavRoutes.TasksRoute.name}?$TASKS_ADD_ARG={$TASKS_ADD_ARG}"
    val EDIT_NOTE = "${NavRoutes.EditNoteRoute.name}/{idNote}" +
        "?isReadTrashOnly={isReadTrashOnly}&copySource={copySource}&addTag={addTag}"

    val patterns: List<Route> = NavRoutes.entries.map { route ->
        val pattern = when (route) {
            NavRoutes.TasksRoute -> TASKS
            NavRoutes.EditNoteRoute -> EDIT_NOTE
            else -> route.name
        }
        Route(route.name, pattern, argumentsFor(pattern))
    }

    fun argumentsFor(pattern: String): List<NamedNavArgument> = when (pattern) {
        TASKS -> listOf(
            navArgument(TASKS_ADD_ARG) {
                type = NavType.BoolType
                defaultValue = false
            },
        )

        EDIT_NOTE -> listOf(
            navArgument("idNote") {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument("isReadTrashOnly") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("copySource") {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument("addTag") {
                type = NavType.LongType
                defaultValue = 0L
            },
        )

        else -> emptyList()
    }
}
