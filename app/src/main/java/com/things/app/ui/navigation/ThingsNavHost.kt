package com.things.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.things.app.ui.anytime.AnytimeScreen
import com.things.app.ui.anytime.sampleAnytimeState
import com.things.app.ui.area.AreaScreen
import com.things.app.ui.area.AreaUiState
import com.things.app.ui.area.sampleAreaState
import com.things.app.ui.home.CreationType
import com.things.app.ui.home.HomeScreen
import com.things.app.ui.home.SystemList
import com.things.app.ui.home.sampleHomeState
import com.things.app.ui.inbox.InboxScreen
import com.things.app.ui.inbox.sampleInboxState
import com.things.app.ui.logbook.LogbookScreen
import com.things.app.ui.logbook.sampleLogbookState
import com.things.app.ui.project.ProjectScreen
import com.things.app.ui.project.ProjectUiState
import com.things.app.ui.project.sampleProjectState
import com.things.app.ui.project.sampleVacationProjectState
import com.things.app.ui.quickfind.QuickFindOverlay
import com.things.app.ui.quickfind.QuickFindTarget
import com.things.app.ui.quickfind.sampleQuickFindState
import com.things.app.ui.someday.SomedayScreen
import com.things.app.ui.someday.sampleSomedayState
import com.things.app.ui.today.TodayScreen
import com.things.app.ui.today.sampleTodayState
import com.things.app.ui.upcoming.UpcomingScreen
import com.things.app.ui.upcoming.sampleUpcomingState

/** One constant per page. Add a new entry here for every page you create. */
object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val UPCOMING = "upcoming"
    const val ANYTIME = "anytime"
    const val LOGBOOK = "logbook"
    const val INBOX = "inbox"
    const val SOMEDAY = "someday"
    const val PROJECT = "project/{projectId}"
    const val AREA = "area/{areaId}"

    fun project(projectId: String) = "project/${Uri.encode(projectId)}"
    fun area(areaId: String) = "area/${Uri.encode(areaId)}"
}

// Push transition: the new page slides in from the end edge, the page below shifts by a third.
private const val PushMillis = 300
private val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Id of the Vacation in Rome project in the Home sample data: it opens the headings sample. */
private const val VacationProjectId = "rome"

private fun routeFor(list: SystemList): String = when (list) {
    SystemList.Inbox -> Routes.INBOX
    SystemList.Today -> Routes.TODAY
    SystemList.Upcoming -> Routes.UPCOMING
    SystemList.Anytime -> Routes.ANYTIME
    SystemList.Someday -> Routes.SOMEDAY
    SystemList.Logbook -> Routes.LOGBOOK
}

private fun routeFor(target: QuickFindTarget): String = when (target) {
    is QuickFindTarget.System -> routeFor(target.list)
    is QuickFindTarget.Area -> Routes.area(target.id)
    is QuickFindTarget.Project -> Routes.project(target.id)
}

/**
 * Static glue: finds the tapped project's title and progress in the sample data so the static
 * project page matches what was tapped. Replace with a repository lookup once real data exists.
 */
private fun sampleProjectStateFor(projectId: String): ProjectUiState {
    val home = sampleHomeState()
    val homeProject = (home.looseProjects + home.areas.flatMap { it.projects })
        .firstOrNull { it.id == projectId }
    val anytimeGroup = sampleAnytimeState().groups.firstOrNull { it.projectId == projectId }
    val todayGroup = sampleTodayState().groups.firstOrNull { it.projectId == projectId }
    return when {
        projectId == VacationProjectId ->
            sampleVacationProjectState(
                id = projectId,
                title = homeProject?.title ?: "Vacation in Rome",
                progress = homeProject?.progress ?: 0.55f,
            )
        homeProject != null -> sampleProjectState(homeProject.id, homeProject.title, homeProject.progress)
        anytimeGroup != null ->
            sampleProjectState(anytimeGroup.projectId, anytimeGroup.projectTitle, anytimeGroup.projectProgress)
        todayGroup != null ->
            sampleProjectState(todayGroup.projectId, todayGroup.projectTitle, todayGroup.projectProgress)
        else -> sampleProjectState()
    }
}

/**
 * Static glue: shows the tapped area's own projects from the Home sample data. Replace with a
 * repository lookup once real data exists.
 */
private fun sampleAreaStateFor(areaId: String): AreaUiState {
    val area = sampleHomeState().areas.firstOrNull { it.id == areaId }
    return if (area != null) {
        AreaUiState(id = area.id, title = area.title, projects = area.projects)
    } else {
        sampleAreaState()
    }
}

/**
 * Every page of the app is registered here. To add a page:
 *  1. Create its screen composable (for example ui/inbox/InboxScreen.kt).
 *  2. Add a route constant in [Routes].
 *  3. Add a composable(Routes.X) { ... } block below.
 *  4. Call navController.navigate(Routes.X) from the screen that links to it.
 *
 * The Quick Find popover is drawn above the NavHost so it can be opened from any page.
 */
@Composable
fun ThingsNavHost() {
    val navController = rememberNavController()

    // Set when the person picks an action in the Home add menu. Part 2 reads this to open the
    // matching editor and then clears it. Nothing is created by setting it.
    var pendingCreation by rememberSaveable { mutableStateOf<CreationType?>(null) }
    var quickFindOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
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
                    onSearchClick = { quickFindOpen = true },
                    onNewToDo = { pendingCreation = CreationType.ToDo },
                    onNewProject = { pendingCreation = CreationType.Project },
                    onNewArea = { pendingCreation = CreationType.Area },
                    onAreaClick = { area ->
                        navController.navigate(Routes.area(area.id)) { launchSingleTop = true }
                    },
                    onProjectClick = { project ->
                        navController.navigate(Routes.project(project.id)) { launchSingleTop = true }
                    },
                    onSystemListClick = { list ->
                        navController.navigate(routeFor(list)) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.TODAY) {
                TodayScreen(
                    state = sampleTodayState(),
                    onBack = { navController.popBackStack() },
                    onProjectClick = { projectId ->
                        navController.navigate(Routes.project(projectId)) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(
                    state = sampleUpcomingState(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ANYTIME) {
                AnytimeScreen(
                    state = sampleAnytimeState(),
                    onBack = { navController.popBackStack() },
                    onProjectClick = { projectId ->
                        navController.navigate(Routes.project(projectId)) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.LOGBOOK) {
                LogbookScreen(
                    state = sampleLogbookState(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.INBOX) {
                InboxScreen(
                    state = sampleInboxState(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SOMEDAY) {
                SomedayScreen(
                    state = sampleSomedayState(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId").orEmpty()
                ProjectScreen(
                    state = sampleProjectStateFor(projectId),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.AREA,
                arguments = listOf(navArgument("areaId") { type = NavType.StringType }),
            ) { entry ->
                val areaId = entry.arguments?.getString("areaId").orEmpty()
                AreaScreen(
                    state = sampleAreaStateFor(areaId),
                    onBack = { navController.popBackStack() },
                    onProjectClick = { projectId ->
                        navController.navigate(Routes.project(projectId)) { launchSingleTop = true }
                    },
                )
            }
        }

        QuickFindOverlay(
            visible = quickFindOpen,
            state = sampleQuickFindState(),
            onDismiss = { quickFindOpen = false },
            onItemClick = { item ->
                quickFindOpen = false
                navController.navigate(routeFor(item.target)) { launchSingleTop = true }
            },
        )
    }
}
