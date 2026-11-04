package com.things.app.ui.area

import com.things.app.ui.home.Project

/**
 * Everything the area page draws: the area title and the projects inside it, in order.
 * Which projects belong to the area is decided by the logic layer; this only describes the list.
 */
data class AreaUiState(
    val id: String,
    val title: String,
    val projects: List<Project> = emptyList(),
)

/** Static sample content for previews and as a fallback for the static page. */
fun sampleAreaState(
    id: String = "tasks",
    title: String = "Tasks",
) = AreaUiState(
    id = id,
    title = title,
    projects = listOf(
        Project("personal", "Personal", 0.9f),
        Project("work", "Work", 0.5f),
        Project("finance", "Finance", 0.8f),
        Project("education", "Education", 0.7f),
        Project("health", "Health", 0.35f),
        Project("shop", "Shop", 0.5f),
    ),
)
