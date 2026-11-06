package com.things.app.ui.home

/**
 * Home with the projects [added] put right after the project [afterId], in the same place it is in
 * (the projects without an area, or its area). With no such project they go at the end of the
 * projects without an area. The order of [added] is kept.
 */
fun HomeUiState.withProjectsAdded(added: List<Project>, afterId: String?): HomeUiState {
    if (added.isEmpty()) return this

    fun List<Project>.insertedAfter(): List<Project>? {
        val index = indexOfFirst { it.id == afterId }
        return if (afterId == null || index < 0) null else toMutableList().also { it.addAll(index + 1, added) }
    }

    looseProjects.insertedAfter()?.let { return copy(looseProjects = it) }
    val owner = areas.firstOrNull { area -> area.projects.insertedAfter() != null }
    if (owner != null) {
        return copy(
            areas = areas.map { area ->
                if (area.id == owner.id) area.copy(projects = area.projects.insertedAfter() ?: area.projects) else area
            },
        )
    }
    return copy(looseProjects = looseProjects + added)
}
