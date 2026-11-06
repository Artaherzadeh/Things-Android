package com.things.app.ui.home

import com.things.app.ui.components.ExportProject
import com.things.app.ui.components.TaskRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeExportTest {

    private fun home() = sampleHomeState().copy(
        looseProjects = listOf(
            Project("p1", "First", 0f),
            Project("p2", "Second", 0f),
        ),
        areas = listOf(
            Area(
                "a1",
                "Area One",
                listOf(
                    Project("p3", "Third", 0f),
                    Project("p4", "Fourth", 0f),
                ),
            ),
        ),
    )

    private fun resolve(id: String) = ExportProject(
        title = "Old title",
        tasks = listOf(TaskRowModel(id = "task-$id", title = "Content $id")),
    )

    @Test
    fun selectedProjectsFollowHomeOrderNotSelectionOrder() {
        val result = markdownForHomeSelection(home(), linkedSetOf("p2", "p1"), ::resolve)

        assertTrue(result.indexOf("# First") < result.indexOf("# Second"))
        assertTrue(result.contains("- [ ] Content p1"))
        assertTrue(result.contains("- [ ] Content p2"))
        assertFalse(result.contains("Content p3"))
        assertFalse(result.contains("Old title"))
    }

    @Test
    fun selectedAreaIncludesEveryProjectOnce() {
        val requested = mutableListOf<String>()

        val result = markdownForHomeSelection(home(), setOf("a1", "p3")) { id ->
            requested += id
            resolve(id)
        }

        assertEquals(listOf("p3", "p4"), requested)
        assertTrue(result.startsWith("# Area One\n"))
        assertTrue(result.contains("## Third\n"))
        assertTrue(result.contains("## Fourth\n"))
        assertEquals(1, result.lineSequence().count { it == "- [ ] Content p3" })
    }

    @Test
    fun projectSelectedWithoutItsAreaIsWrittenOnItsOwn() {
        val result = markdownForHomeSelection(home(), setOf("p3"), ::resolve)

        assertTrue(result.startsWith("# Third\n"))
        assertFalse(result.contains("# Area One"))
        assertFalse(result.contains("Content p4"))
    }

    @Test
    fun mixedSelectionKeepsHomeOrder() {
        val result = markdownForHomeSelection(home(), linkedSetOf("a1", "p2"), ::resolve)

        assertTrue(result.indexOf("# Second") < result.indexOf("# Area One"))
        assertTrue(result.contains("Content p3"))
        assertTrue(result.contains("Content p4"))
    }

    @Test
    fun emptyOrUnknownSelectionResolvesNothing() {
        val resolver: (String) -> ExportProject = { error("No project should be resolved") }

        assertEquals("", markdownForHomeSelection(home(), emptySet(), resolver))
        assertEquals("", markdownForHomeSelection(home(), setOf("missing"), resolver))
    }

    @Test
    fun emptyAreaIsStillWrittenWithItsTitle() {
        val state = home().copy(areas = listOf(Area("empty", "Empty Area", emptyList())))

        assertEquals("# Empty Area\n", markdownForHomeSelection(state, setOf("empty"), ::resolve))
    }
}
