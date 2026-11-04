package com.things.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.things.app.ui.home.CreationType
import com.things.app.ui.home.HomeScreen
import com.things.app.ui.home.sampleHomeState

/** One constant per page. Add a new entry here for every page you create. */
object Routes {
    const val HOME = "home"
    // const val INBOX = "inbox"
    // const val TODAY = "today"
    // const val PROJECT = "project/{projectId}"
}

/**
 * Every page of the app is registered here. To add a page:
 *  1. Create its screen composable (for example ui/inbox/InboxScreen.kt).
 *  2. Add a route constant in [Routes].
 *  3. Add a composable(Routes.X) { ... } block below.
 *  4. Call navController.navigate(Routes.X) from the screen that links to it.
 */
@Composable
fun ThingsNavHost() {
    val navController = rememberNavController()

    // Set when the person picks an action in the Home add menu. Part 2 reads this to open the
    // matching editor and then clears it. Nothing is created by setting it.
    var pendingCreation by rememberSaveable { mutableStateOf<CreationType?>(null) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = sampleHomeState(),
                onNewToDo = { pendingCreation = CreationType.ToDo },
                onNewProject = { pendingCreation = CreationType.Project },
                onNewArea = { pendingCreation = CreationType.Area },
                // Wire these up as the matching pages are added, e.g.:
                // onSystemListClick = { list -> navController.navigate(list.name.lowercase()) },
            )
        }
    }
}
