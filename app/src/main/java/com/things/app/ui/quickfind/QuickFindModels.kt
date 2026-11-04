package com.things.app.ui.quickfind

import com.things.app.ui.home.SystemList

/** Where a Quick Find line leads. Resolving it to a screen is the navigation layer's job. */
sealed interface QuickFindTarget {
    data class System(val list: SystemList) : QuickFindTarget
    data class Area(val id: String) : QuickFindTarget
    data class Project(val id: String) : QuickFindTarget
}

/** One line in the Quick Find list. [progress] is only used by projects (the ring icon). */
data class QuickFindItem(
    val title: String,
    val target: QuickFindTarget,
    val progress: Float = 0f,
    /** Draws the check mark at the end: the list the person is looking at right now. */
    val isCurrent: Boolean = false,
)

/** Everything the Quick Find popover draws. Which lists are "recent" is decided by the logic layer. */
data class QuickFindUiState(
    val recent: List<QuickFindItem> = emptyList(),
)

/** Static sample content for previews and the first static version of the popover. */
fun sampleQuickFindState() = QuickFindUiState(
    recent = listOf(
        QuickFindItem("Inbox", QuickFindTarget.System(SystemList.Inbox), isCurrent = true),
        QuickFindItem("Tasks", QuickFindTarget.Area("tasks")),
        QuickFindItem("Personal", QuickFindTarget.Project("personal"), progress = 0.9f),
        QuickFindItem("Today", QuickFindTarget.System(SystemList.Today)),
    ),
)
