package com.things.app.ui.home

/** The fixed lists at the top of the home screen. */
enum class SystemList(val title: String) {
    Inbox("Inbox"),
    Today("Today"),
    Upcoming("Upcoming"),
    Anytime("Anytime"),
    Someday("Someday"),
    Logbook("Logbook"),
}

/** A project (sub page). [progress] is 0f..1f and drives the ring icon. */
data class Project(
    val id: String,
    val title: String,
    val progress: Float = 0f,
)

/** An area (main section) which groups projects and can be collapsed. */
data class Area(
    val id: String,
    val title: String,
    val projects: List<Project>,
)

/** Home lists no to-dos: only the system lists, projects and areas. */
data class HomeUiState(
    val inboxCount: Int = 0,
    val todayCount: Int = 0,
    /** Pink badge shown beside the Today count (overdue / newly added items). */
    val todayBadge: Int = 0,
    /** Projects that don't belong to any area. */
    val looseProjects: List<Project> = emptyList(),
    val areas: List<Area> = emptyList(),
)

/** Matches the populated marketing screenshot. */
fun sampleHomeState() = HomeUiState(
    inboxCount = 2,
    todayCount = 7,
    todayBadge = 1,
    areas = listOf(
        Area(
            id = "family",
            title = "Family",
            projects = listOf(
                Project("rome", "Vacation in Rome", 0.55f),
                Project("bike", "Buy a New Bike", 0.75f),
                Project("party", "Throw Party for Eve", 0.2f),
            ),
        ),
        Area(
            id = "work",
            title = "Work",
            projects = listOf(
                Project("presentation", "Prepare Presentation", 0.3f),
                Project("julia", "Onboard Julia", 0.35f),
            ),
        ),
    ),
)

/** Matches the first launch screenshot. */
fun emptyHomeState() = HomeUiState(
    todayCount = 3,
    looseProjects = listOf(Project("meet", "Meet Things for iPhone", 0f)),
)
