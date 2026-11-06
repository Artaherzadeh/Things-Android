package com.things.app.ui.home

import com.things.app.ui.components.ExportArea
import com.things.app.ui.components.ExportProject
import com.things.app.ui.components.exportArea
import com.things.app.ui.components.exportProject

/**
 * The Markdown of the Home rows that are ticked, in the order Home shows them: the projects without
 * an area first, then each area with its projects.
 *
 * A ticked area is exported with every project in it. A project that is ticked together with its own
 * area is written once, inside that area. A project ticked without its area is written on its own.
 * [resolveProject] gives the current contents of a project by its id; the title shown on Home is
 * the one that is written.
 */
fun markdownForHomeSelection(
    home: HomeUiState,
    selected: Set<String>,
    resolveProject: (String) -> ExportProject,
): String {
    if (selected.isEmpty()) return ""

    val parts = mutableListOf<String>()
    val written = mutableSetOf<String>()

    fun resolved(project: Project): ExportProject =
        resolveProject(project.id).copy(title = project.title)

    home.looseProjects.forEach { project ->
        if (project.id in selected && written.add(project.id)) {
            parts += exportProject(resolved(project)).trimEnd('\n')
        }
    }

    home.areas.forEach { area ->
        if (area.id in selected) {
            val projects = area.projects.mapNotNull { project ->
                if (written.add(project.id)) resolved(project) else null
            }
            parts += exportArea(ExportArea(area.title, projects)).trimEnd('\n')
        } else {
            area.projects.forEach { project ->
                if (project.id in selected && written.add(project.id)) {
                    parts += exportProject(resolved(project)).trimEnd('\n')
                }
            }
        }
    }

    return if (parts.isEmpty()) "" else parts.joinToString("\n\n") + "\n"
}
