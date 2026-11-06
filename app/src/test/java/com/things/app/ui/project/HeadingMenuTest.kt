package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingMenuTest {

    private fun populated(id: String = "t1", checked: Boolean = true) = TaskRowModel(
        id = id,
        title = "Book hotel",
        checked = checked,
        hasNote = true,
        hasChecklist = true,
        hasTag = true,
        hasReminder = true,
        isToday = true,
        isRecurring = true,
        chip = "Priority",
        details = TaskDetailsModel(
            notes = "Near Colosseum\n- Single room",
            checklist = listOf(
                ChecklistItemModel("c1", "Confirm reservation", done = true),
                ChecklistItemModel("c2", "Pay deposit"),
            ),
            schedule = ScheduleModel(ScheduleKind.Today, "Today", reminder = "10:00 AM"),
            deadline = DeadlineModel("June 14", "5 days left"),
            repeat = RepeatInfoModel(title = "Every year", summary = "Repeat every year"),
            tags = listOf("Vacation", "Important"),
        ),
    )

    private fun plain(id: String, checked: Boolean = false) = TaskRowModel(id = id, title = "Task $id", checked = checked)

    private fun project(
        top: List<TaskRowModel> = emptyList(),
        headings: List<ProjectHeadingModel> = emptyList(),
        logged: Int = 0,
    ) = ProjectUiState(
        id = "p1",
        title = "Trip",
        progress = 0.1f,
        notes = "Project notes",
        tasks = top,
        headings = headings,
        loggedTasks = List(logged) { LoggedTaskModel("l$it", "Logged $it", "11/2") },
    )

    @Test
    fun convertMovesEveryTaskUnchangedAndInOrder() {
        val tasks = listOf(populated("t1"), plain("t2"), plain("t3", checked = true))
        val source = project(
            top = listOf(plain("top")),
            headings = listOf(
                ProjectHeadingModel("h1", "Hotels & Stays", tasks),
                ProjectHeadingModel("h2", "Other", listOf(plain("o1"))),
            ),
        )

        val result = convertHeading(source, "h1", "proj-new")

        assertNotNull(result)
        val created = result!!.newProject
        assertEquals("proj-new", created.id)
        assertEquals("Hotels & Stays", created.title)
        assertEquals(tasks, created.tasks)
        assertEquals(listOf("t1", "t2", "t3"), created.tasks.map { it.id })
        assertEquals(populated("t1"), created.tasks.first())
        assertTrue(created.headings.isEmpty())
    }

    @Test
    fun convertRemovesOnlyThatHeadingFromTheSource() {
        val source = project(
            top = listOf(plain("top")),
            headings = listOf(
                ProjectHeadingModel("h1", "A", listOf(plain("a1"))),
                ProjectHeadingModel("h2", "B", listOf(plain("b1"))),
            ),
        )

        val updated = convertHeading(source, "h1", "proj-new")!!.source

        assertEquals("p1", updated.id)
        assertEquals("Trip", updated.title)
        assertEquals("Project notes", updated.notes)
        assertEquals(source.tasks, updated.tasks)
        assertEquals(listOf("h2"), updated.headings.map { it.id })
        assertEquals(listOf("b1"), updated.headings.single().tasks.map { it.id })
    }

    @Test
    fun convertEmptyHeadingMakesAnEmptyProject() {
        val source = project(headings = listOf(ProjectHeadingModel("h1", "Empty Section", emptyList())))

        val result = convertHeading(source, "h1", "proj-new")!!

        assertEquals("Empty Section", result.newProject.title)
        assertTrue(result.newProject.tasks.isEmpty())
        assertEquals(0f, result.newProject.progress, 0.001f)
        assertTrue(result.source.headings.isEmpty())
    }

    @Test
    fun convertGivesABlankHeadingTheDefaultName() {
        val source = project(headings = listOf(ProjectHeadingModel("h1", "  ", listOf(plain("a")))))

        assertEquals("New Project", convertHeading(source, "h1", "proj-new")!!.newProject.title)
    }

    @Test
    fun convertComputesTheRingFromTheTicks() {
        val done = project(headings = listOf(ProjectHeadingModel("h1", "A", listOf(plain("a", true), plain("b", true)))))
        val half = project(headings = listOf(ProjectHeadingModel("h1", "A", listOf(plain("a", true), plain("b")))))

        assertEquals(1f, convertHeading(done, "h1", "n")!!.newProject.progress, 0.001f)
        assertEquals(0.5f, convertHeading(half, "h1", "n")!!.newProject.progress, 0.001f)
    }

    @Test
    fun convertRecomputesTheSourceRingWithLoggedItems() {
        val source = project(
            top = listOf(plain("top")),
            headings = listOf(ProjectHeadingModel("h1", "A", listOf(plain("a", true), plain("b", true)))),
            logged = 1,
        )

        // Left: one open to-do and one logged item, so one of two is done.
        assertEquals(0.5f, convertHeading(source, "h1", "n")!!.source.progress, 0.001f)
    }

    @Test
    fun convertKeepsRepeatingTasksAndTheirRepeat() {
        val repeating = populated("r1", checked = false)
        val source = project(headings = listOf(ProjectHeadingModel("h1", "A", listOf(repeating))))

        val task = convertHeading(source, "h1", "n")!!.newProject.tasks.single()

        assertTrue(task.isRecurring)
        assertEquals("Repeat every year", task.details.repeat?.summary)
        assertEquals("Every year", task.details.repeat?.title)
    }

    @Test
    fun convertOfAnUnknownHeadingChangesNothing() {
        val source = project(headings = listOf(ProjectHeadingModel("h1", "A", listOf(plain("a")))))
        val before = source.copy()

        assertNull(convertHeading(source, "missing", "n"))
        assertEquals(before, source)
    }

    @Test
    fun copyHeadingGivesNewIdsAndKeepsEverythingElse() {
        val original = ProjectHeadingModel("h1", "Planning", listOf(populated("t1"), plain("t2", true)))
        var next = 0

        val copy = copyHeading(original, "h-copy") { "copy-${next++}" }

        assertEquals("h-copy", copy.id)
        assertNotEquals(original.id, copy.id)
        assertEquals(original.title, copy.title)
        assertEquals(listOf("copy-0", "copy-1"), copy.tasks.map { it.id })
        copy.tasks.zip(original.tasks).forEach { (new, old) ->
            assertNotEquals(old.id, new.id)
            assertEquals(old, new.copy(id = old.id))
        }
        assertEquals(listOf("t1", "t2"), original.tasks.map { it.id })
    }

    @Test
    fun progressOfCountsLoggedItemsAsDone() {
        assertEquals(0f, progressOf(emptyList()), 0.001f)
        assertEquals(0.75f, progressOf(listOf(plain("a", true), plain("b")), loggedCount = 2), 0.001f)
    }
}
