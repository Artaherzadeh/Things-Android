package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskToProjectTest {

    private fun plain(id: String, checked: Boolean = false) = TaskRowModel(id = id, title = "Task $id", checked = checked)

    @Test
    fun aTodoBecomesAProjectWithItsTitleNotesAndChecklist() {
        val todo = TaskRowModel(
            id = "t",
            title = "Plan the trip",
            details = TaskDetailsModel(
                notes = "Bring the guide\n- passports",
                checklist = listOf(
                    ChecklistItemModel("c1", "Book flights", done = true),
                    ChecklistItemModel("c2", "Book hotel"),
                ),
            ),
        )

        val project = todo.toProjectState("proj-1")

        assertEquals("proj-1", project.id)
        assertEquals("Plan the trip", project.title)
        assertEquals("Bring the guide\n- passports", project.notes)
        assertEquals(listOf("proj-1-c1", "proj-1-c2"), project.tasks.map { it.id })
        assertEquals(listOf("Book flights", "Book hotel"), project.tasks.map { it.title })
        assertEquals(listOf(true, false), project.tasks.map { it.checked })
        assertEquals(0.5f, project.progress, 0.001f)
        assertTrue(project.headings.isEmpty())
    }

    @Test
    fun aTodoWithoutChecklistOrTitleMakesAnEmptyNamedProject() {
        val project = TaskRowModel(id = "t", title = "  ").toProjectState("p")

        assertEquals("New Project", project.title)
        assertTrue(project.tasks.isEmpty())
        assertEquals(0f, project.progress, 0.001f)
    }

    @Test
    fun theIdsOfTheNewTodosCanBeChosen() {
        val todo = TaskRowModel(
            id = "t",
            title = "X",
            details = TaskDetailsModel(checklist = listOf(ChecklistItemModel("c1", "A"))),
        )

        assertEquals(listOf("made-c1"), todo.toProjectState("p") { "made-${it.id}" }.tasks.map { it.id })
    }

    @Test
    fun removingTodosTakesThemOutOfEveryPartAndFixesTheRing() {
        val state = ProjectUiState(
            id = "p",
            title = "P",
            progress = 0f,
            tasks = listOf(plain("a", checked = true), plain("b")),
            headings = listOf(ProjectHeadingModel("h", "H", listOf(plain("c"), plain("d", checked = true)))),
            laterTasks = listOf(plain("e")),
            loggedTasks = listOf(LoggedTaskModel("l", "Done", "11/2")),
        )

        val result = state.withoutTasks(setOf("b", "c", "e"))

        assertEquals(listOf("a"), result.tasks.map { it.id })
        assertEquals(listOf("d"), result.headings.single().tasks.map { it.id })
        assertTrue(result.laterTasks.isEmpty())
        assertEquals("P", result.title)
        // a and d are done, plus one logged item: three of three.
        assertEquals(1f, result.progress, 0.001f)
    }

    @Test
    fun removingNothingChangesNothingExceptTheRingValue() {
        val state = ProjectUiState(id = "p", title = "P", progress = 0.3f, tasks = listOf(plain("a")))

        val result = state.withoutTasks(emptySet())

        assertEquals(state.tasks, result.tasks)
        assertEquals(state.headings, result.headings)
    }
}
