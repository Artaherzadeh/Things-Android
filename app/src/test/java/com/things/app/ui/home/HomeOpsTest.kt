package com.things.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOpsTest {

    private fun home() = sampleHomeState().copy(
        looseProjects = listOf(Project("p1", "One", 0f), Project("p2", "Two", 0f)),
        areas = listOf(
            Area("a1", "Area", listOf(Project("p3", "Three", 0f), Project("p4", "Four", 0f))),
        ),
    )

    private val fresh = listOf(Project("n1", "New 1", 0f), Project("n2", "New 2", 0f))

    @Test
    fun newProjectsComeRightAfterTheirSourceAmongTheLooseProjects() {
        val result = home().withProjectsAdded(fresh, afterId = "p1")

        assertEquals(listOf("p1", "n1", "n2", "p2"), result.looseProjects.map { it.id })
        assertEquals(home().areas, result.areas)
    }

    @Test
    fun newProjectsComeRightAfterTheirSourceInItsArea() {
        val result = home().withProjectsAdded(fresh, afterId = "p3")

        assertEquals(listOf("p3", "n1", "n2", "p4"), result.areas.single().projects.map { it.id })
        assertEquals(home().looseProjects, result.looseProjects)
    }

    @Test
    fun withoutASourceTheyGoToTheEndOfTheLooseProjects() {
        assertEquals(
            listOf("p1", "p2", "n1", "n2"),
            home().withProjectsAdded(fresh, afterId = null).looseProjects.map { it.id },
        )
        assertEquals(
            listOf("p1", "p2", "n1", "n2"),
            home().withProjectsAdded(fresh, afterId = "missing").looseProjects.map { it.id },
        )
    }

    @Test
    fun addingNothingChangesNothing() {
        assertEquals(home(), home().withProjectsAdded(emptyList(), afterId = "p1"))
    }
}
