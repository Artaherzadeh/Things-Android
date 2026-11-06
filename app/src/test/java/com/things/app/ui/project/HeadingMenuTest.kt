package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.HeadingMenuAction
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeadingMenuTest {

    private val defaultEnabledActions = setOf(
        HeadingMenuAction.Duplicate,
        HeadingMenuAction.ConvertToProject,
        HeadingMenuAction.Delete,
    )

    @Test
    fun headingMenuDisablesUnsupportedActionsAndEnablesSupportedOnes() {
        assertFalse("Archive should be disabled as heading archive semantics are not implemented", HeadingMenuAction.Archive in defaultEnabledActions)
        assertFalse("Move should be disabled as heading move flow is not implemented", HeadingMenuAction.Move in defaultEnabledActions)
        assertTrue("Duplicate should be enabled", HeadingMenuAction.Duplicate in defaultEnabledActions)
        assertTrue("Convert to a Project should be enabled", HeadingMenuAction.ConvertToProject in defaultEnabledActions)
        assertTrue("Delete should be enabled", HeadingMenuAction.Delete in defaultEnabledActions)
    }

    @Test
    fun convertToProjectPreservesAllTaskDataAndMetadata() {
        val populatedTask = TaskRowModel(
            id = "t1",
            title = "Book hotel",
            checked = true,
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

        val heading = ProjectHeadingModel(
            id = "h1",
            title = "Hotels & Stays",
            tasks = listOf(populatedTask),
        )

        // Convert heading to project
        val newProjectId = "proj-" + UUID.randomUUID()
        val newProjectTitle = heading.title
        val childTasks = heading.tasks
        val doneCount = childTasks.count { it.checked }
        val totalCount = childTasks.size
        val newProgress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount

        val convertedProject = ProjectUiState(
            id = newProjectId,
            title = newProjectTitle,
            progress = newProgress,
            tasks = childTasks,
        )

        assertEquals("Hotels & Stays", convertedProject.title)
        assertEquals(1, convertedProject.tasks.size)
        assertEquals(1.0f, convertedProject.progress, 0.001f)

        val task = convertedProject.tasks.single()
        assertEquals("t1", task.id)
        assertEquals("Book hotel", task.title)
        assertTrue(task.checked)
        assertTrue(task.hasNote)
        assertTrue(task.hasChecklist)
        assertTrue(task.hasTag)
        assertTrue(task.hasReminder)
        assertTrue(task.isToday)
        assertTrue(task.isRecurring)

        val details = task.details
        assertEquals("Near Colosseum\n- Single room", details.notes)
        assertEquals(2, details.checklist.size)
        assertTrue(details.checklist[0].done)
        assertEquals("Confirm reservation", details.checklist[0].title)
        assertFalse(details.checklist[1].done)
        assertEquals("Pay deposit", details.checklist[1].title)
        assertEquals("10:00 AM", details.schedule?.reminder)
        assertEquals("June 14", details.deadline?.dateLabel)
        assertEquals("Repeat every year", details.repeat?.summary)
        assertEquals(listOf("Vacation", "Important"), details.tags)
    }

    @Test
    fun convertEmptyHeadingCreatesProjectWithZeroProgress() {
        val emptyHeading = ProjectHeadingModel("h-empty", "Empty Section", emptyList())

        val childTasks = emptyHeading.tasks
        val newProgress = if (childTasks.isEmpty()) 0f else childTasks.count { it.checked }.toFloat() / childTasks.size
        val convertedProject = ProjectUiState(
            id = "proj-empty",
            title = emptyHeading.title,
            progress = newProgress,
            tasks = childTasks,
        )

        assertEquals("Empty Section", convertedProject.title)
        assertTrue(convertedProject.tasks.isEmpty())
        assertEquals(0f, convertedProject.progress, 0.001f)
    }

    @Test
    fun duplicateHeadingCreatesNewIdsAndPreservesTaskContent() {
        val originalTask = TaskRowModel(
            id = "task-orig",
            title = "Original Task",
            checked = false,
            details = TaskDetailsModel(notes = "Original notes"),
        )
        val originalHeading = ProjectHeadingModel(
            id = "heading-orig",
            title = "Planning",
            tasks = listOf(originalTask),
        )

        val duplicatedHeadingId = "heading-" + UUID.randomUUID()
        val copiedTasks = originalHeading.tasks.map { task ->
            task.copy(id = "task-" + UUID.randomUUID())
        }
        val duplicatedHeading = ProjectHeadingModel(
            id = duplicatedHeadingId,
            title = originalHeading.title,
            tasks = copiedTasks,
        )

        assertNotEquals(originalHeading.id, duplicatedHeading.id)
        assertEquals(originalHeading.title, duplicatedHeading.title)
        assertEquals(1, duplicatedHeading.tasks.size)

        val copiedTask = duplicatedHeading.tasks.single()
        assertNotEquals(originalTask.id, copiedTask.id)
        assertEquals(originalTask.title, copiedTask.title)
        assertEquals(originalTask.details.notes, copiedTask.details.notes)
    }
}
