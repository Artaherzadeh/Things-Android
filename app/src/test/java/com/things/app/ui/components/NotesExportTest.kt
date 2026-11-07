package com.things.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests that notes keep their whitespace and that a project's own fields are exported everywhere. */
class NotesExportTest {

    @Test
    fun projectNotesKeepTrailingSpacesAndTabsOfALineWithText() {
        val out = exportProject(ExportProject("P", notes = "Line  \n\n  indented\t \n\n"))

        assertEquals("# P\n\nLine  \n\n  indented\t \n", out)
    }

    @Test
    fun projectNotesWithOnlyBlankTextAreLeftOut() {
        assertEquals("# P\n", exportProject(ExportProject("P", notes = " \n\t\n")))
    }

    @Test
    fun taskNotesKeepTrailingSpaces() {
        val task = TaskRowModel(id = "t", title = "T", details = TaskDetailsModel(notes = "a  \n"))

        assertEquals("- [ ] T\n\n  a  \n", TaskMarkdown.export(listOf(task)))
    }

    @Test
    fun theDeadlineOfAProjectIsWrittenAloneAndInsideAnArea() {
        val project = ExportProject("P", deadline = DeadlineModel("Mon, Nov 16", "12 days left"))

        assertEquals("# P\n\n- Deadline: Mon, Nov 16 (12 days left)\n", exportProject(project))
        assertEquals(
            "# A\n\n## P\n\n- Deadline: Mon, Nov 16 (12 days left)\n",
            exportArea(ExportArea("A", listOf(project))),
        )
    }
}
