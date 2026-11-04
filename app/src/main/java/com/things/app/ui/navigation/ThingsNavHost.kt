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
import androidx.compose.runtime.remember
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
import com.things.app.ui.home.Area
import com.things.app.ui.home.HomeScreen
import com.things.app.ui.home.HomeUiState
import com.things.app.ui.home.Project
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
import java.util.UUID
import kotlin.math.abs

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

/**
 * The slide while the finger drags the page back from the screen edge. Its first part is a straight
 * line, so the page stays under the finger instead of jumping ahead like it would with the sharp
 * start of [PushEasing]; the end slows down in the same way the button's slide does.
 */
private val GestureEasing = CubicBezierEasing(0.25f, 0.25f, 0.25f, 1f)

/** Id of the Vacation in Rome project in the Home sample data: it opens the headings sample. */
private const val VacationProjectId = "rome"

/** Ids of projects and areas created in the app start with this. They open empty. */
private const val NewIdPrefix = "new-"

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
 * Static glue: finds the tapped project's title and progress in the Home data so the project page
 * matches what was tapped. A project created in the app opens empty. Replace with a repository
 * lookup once real data exists.
 */
private fun projectStateFor(projectId: String, home: HomeUiState): ProjectUiState {
    val homeProject = (home.looseProjects + home.areas.flatMap { it.projects })
        .firstOrNull { it.id == projectId }
    val anytimeGroup = sampleAnytimeState().groups.firstOrNull { it.projectId == projectId }
    val todayGroup = sampleTodayState().groups.firstOrNull { it.projectId == projectId }
    return when {
        projectId.startsWith(NewIdPrefix) ->
            ProjectUiState(id = projectId, title = homeProject?.title.orEmpty(), progress = 0f)
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
 * Static glue: shows the tapped area's own projects from the Home data. Replace with a repository
 * lookup once real data exists.
 */
private fun areaStateFor(areaId: String, home: HomeUiState): AreaUiState {
    val area = home.areas.firstOrNull { it.id == areaId }
    return if (area != null) {
        AreaUiState(id = area.id, title = area.title, projects = area.projects)
    } else {
        sampleAreaState()
    }
}

/** The areas with [from] moved to where [to] is. */
private fun HomeUiState.withAreaMoved(from: String, to: String): HomeUiState {
    val fromIndex = areas.indexOfFirst { it.id == from }
    val toIndex = areas.indexOfFirst { it.id == to }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return this
    return copy(areas = areas.toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) })
}

/**
 * The project [from] moved next to the project [to], which may be in another area or in none.
 * Going down it lands under [to], going up it lands above it. "Down" means later in the Home list:
 * the projects without an area first, then each area in turn.
 */
private fun HomeUiState.withProjectMoved(from: String, to: String): HomeUiState {
    if (from == to) return this
    val order = looseProjects.map { it.id } + areas.flatMap { a -> a.projects.map { it.id } }
    val fromAt = order.indexOf(from)
    val toAt = order.indexOf(to)
    if (fromAt < 0 || toAt < 0) return this
    val moved = (looseProjects + areas.flatMap { it.projects }).first { it.id == from }
    val down = fromAt < toAt

    fun List<Project>.without() = filter { it.id != from }
    fun List<Project>.placed(): List<Project> {
        val index = indexOfFirst { it.id == to }
        if (index < 0) return this
        return toMutableList().also { it.add(if (down) index + 1 else index, moved) }
    }

    return copy(
        looseProjects = looseProjects.without().let { list ->
            if (list.any { it.id == to }) list.placed() else list
        },
        areas = areas.map { area ->
            val rest = area.projects.without()
            area.copy(projects = if (rest.any { it.id == to }) rest.placed() else rest)
        },
    )
}

/** The same Home data with the ring of the project [id] set to [progress]. */
private fun HomeUiState.withProgress(id: String, progress: Float): HomeUiState = copy(
    looseProjects = looseProjects.map { if (it.id == id) it.copy(progress = progress) else it },
    areas = areas.map { area ->
        area.copy(projects = area.projects.map { if (it.id == id) it.copy(progress = progress) else it })
    },
)

/**
 * Every page of the app is registered here. To add a page:
 *  1. Create its screen composable (for example ui/inbox/InboxScreen.kt).
 *  2. Add a route constant in [Routes].
 *  3. Add a composable(Routes.X) { ... } block below.
 *  4. Call navController.navigate(Routes.X) from the screen that links to it.
 *
 * The Quick Find popover is drawn above the NavHost so it can be opened from any page. While it is
 * open the search box of Home is hidden.
 *
 * Going back with the system gesture (from the screen edge) plays the same slide as the back button
 * but follows the finger: it has its own transitions, [GestureEasing], with the same length.
 *
 * Home keeps its projects and areas here while the app runs. New Project, New Area and the dragged
 * + add a row to the Home list and open it for naming in place; if it is left empty the row is
 * dropped again. Holding a project or an area on Home changes the order, swiping one to the left
 * deletes it. The ring of a project on Home follows the to-dos done on its page. Delete Area on an
 * area page removes it.
 */
@Composable
fun ThingsNavHost() {
    val navController = rememberNavController()

    var quickFindOpen by rememberSaveable { mutableStateOf(false) }
    var homeState by remember { mutableStateOf(sampleHomeState()) }
    var editingId by remember { mutableStateOf<String?>(null) }

    /** Adds an empty project to an area (or to the loose projects), names it in place. */
    fun createProject(areaId: String?, index: Int?) {
        val project = Project(NewIdPrefix + UUID.randomUUID(), "", 0f)
        homeState = if (areaId == null) {
            val list = homeState.looseProjects
            val at = (index ?: list.size).coerceIn(0, list.size)
            homeState.copy(looseProjects = list.toMutableList().also { it.add(at, project) })
        } else {
            homeState.copy(
                areas = homeState.areas.map { area ->
                    if (area.id == areaId) {
                        val at = (index ?: area.projects.size).coerceIn(0, area.projects.size)
                        area.copy(projects = area.projects.toMutableList().also { it.add(at, project) })
                    } else {
                        area
                    }
                },
            )
        }
        editingId = project.id
    }

    fun renameInHome(id: String, title: String) {
        homeState = homeState.copy(
            looseProjects = homeState.looseProjects.map { if (it.id == id) it.copy(title = title) else it },
            areas = homeState.areas.map { area ->
                area.copy(
                    title = if (area.id == id) title else area.title,
                    projects = area.projects.map { if (it.id == id) it.copy(title = title) else it },
                )
            },
        )
    }

    fun deleteFromHome(id: String) {
        homeState = homeState.copy(
            looseProjects = homeState.looseProjects.filter { it.id != id },
            areas = homeState.areas
                .filter { it.id != id }
                .map { area -> area.copy(projects = area.projects.filter { it.id != id }) },
        )
    }

    /** The cursor left a row that was being named: an empty one is not kept. */
    fun finishNaming(id: String) {
        if (editingId == id) editingId = null
        val empty = homeState.looseProjects.any { it.id == id && it.title.isBlank() } ||
            homeState.areas.any { a -> a.id == id && a.title.isBlank() || a.projects.any { it.id == id && it.title.isBlank() } }
        if (empty) deleteFromHome(id)
    }

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
            predictivePopEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(PushMillis, easing = GestureEasing),
                    initialOffset = { it / 3 },
                )
            },
            predictivePopExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(PushMillis, easing = GestureEasing),
                )
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = homeState,
                    onSearchClick = { quickFindOpen = true },
                    searchHidden = quickFindOpen,
                    onNewProject = { createProject(null, null) },
                    onNewArea = {
                        val area = Area(NewIdPrefix + UUID.randomUUID(), "", emptyList())
                        homeState = homeState.copy(areas = homeState.areas + area)
                        editingId = area.id
                    },
                    onCreateProjectAt = { areaId, index -> createProject(areaId, index) },
                    editingId = editingId,
                    onTitleChange = { id, title -> renameInHome(id, title) },
                    onEditDone = { id -> finishNaming(id) },
                    onMoveProject = { from, to -> homeState = homeState.withProjectMoved(from, to) },
                    onMoveArea = { from, to -> homeState = homeState.withAreaMoved(from, to) },
                    onDeleteProject = { id -> deleteFromHome(id) },
                    onDeleteArea = { id -> deleteFromHome(id) },
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
                    state = projectStateFor(projectId, homeState),
                    onBack = { navController.popBackStack() },
                    onProgressChange = { progress ->
                        val current = (homeState.looseProjects + homeState.areas.flatMap { it.projects })
                            .firstOrNull { it.id == projectId }
                        if (current != null && abs(current.progress - progress) > 0.001f) {
                            homeState = homeState.withProgress(projectId, progress)
                        }
                    },
                )
            }
            composable(
                route = Routes.AREA,
                arguments = listOf(navArgument("areaId") { type = NavType.StringType }),
            ) { entry ->
                val areaId = entry.arguments?.getString("areaId").orEmpty()
                AreaScreen(
                    state = areaStateFor(areaId, homeState),
                    onBack = { navController.popBackStack() },
                    onProjectClick = { projectId ->
                        navController.navigate(Routes.project(projectId)) { launchSingleTop = true }
                    },
                    onDeleteArea = {
                        deleteFromHome(areaId)
                        navController.popBackStack()
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
