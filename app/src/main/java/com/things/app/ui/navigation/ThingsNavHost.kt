package com.things.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import com.things.app.ui.home.SystemList
import com.things.app.ui.home.sampleHomeState
import com.things.app.ui.today.TodayScreen
import com.things.app.ui.today.sampleTodayState

/** One constant per page. Add a new entry here for every page you create. */
object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    // const val INBOX = "inbox"
    // const val PROJECT = "project/{projectId}"
}

// Push transition: the new page slides in from the end edge, the page below shifts by a third.
private const val PushMillis = 300
private val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(PushMillis, easing = PushEasing),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(PushMillis, easing = PushEasing),
                targetOffset = { it / 3 },
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(PushMillis, easing = PushEasing),
                initialOffset = { it / 3 },
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(PushMillis, easing = PushEasing),
            )
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                state = sampleHomeState(),
                onNewToDo = { pendingCreation = CreationType.ToDo },
                onNewProject = { pendingCreation = CreationType.Project },
                onNewArea = { pendingCreation = CreationType.Area },
                onSystemListClick = { list ->
                    if (list == SystemList.Today) {
                        navController.navigate(Routes.TODAY) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(Routes.TODAY) {
            TodayScreen(
                state = sampleTodayState(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
