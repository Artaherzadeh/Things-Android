package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskProjectFieldsTest {
    @Test
    fun conversionCarriesAllProjectDetailsAndReminder() {
        val details = TaskDetailsModel(
            notes = "Keep Markdown  \n\n    code\t",
            schedule = ScheduleModel(ScheduleKind.Date, "Wed, Nov 18", "12:00"),
            deadline = DeadlineModel("Wed, Nov 18", "11 days left"),
            tags = listOf("Wishlist", "Next"),
            repeat = RepeatInfoModel("Every week on Wednesday", "Next: Wed, Nov 18"),
            checklist = listOf(ChecklistItemModel("a", "First", true), ChecklistItemModel("b", "Second")),
        )
        val source = TaskRowModel(id = "task", title = "Project", details = details)
        val project = source.toProjectState("project")
        assertEquals(details.notes, project.notes)
        assertEquals(details.schedule, project.schedule)
        assertEquals(details.deadline, project.deadline)
        assertEquals(details.tags, project.tags)
        assertEquals(details.repeat, project.repeat)
        assertEquals(listOf("First", "Second"), project.tasks.map { it.title })
        assertEquals(listOf(true, false), project.tasks.map { it.checked })
        assertTrue(source.unsupportedProjectFields().isEmpty())
        assertEquals(details, source.details)
    }

    @Test
    fun absentDetailsAreNotInvented() {
        val project = TaskRowModel(id = "t", title = "P").toProjectState("p")
        assertNull(project.schedule)
        assertNull(project.deadline)
        assertNull(project.repeat)
        assertTrue(project.tags.isEmpty())
    }
}
