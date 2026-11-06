package com.things.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests that every Share goes through the same rules: only the content and the level change. */
class ProjectExportTest {

    private fun md(vararg lines: String) = lines.joinToString("\n") + "\n"

    private fun task(id: String, title: String, checked: Boolean = false) =
        TaskRowModel(id = id, title = title, checked = checked)

    private val full = TaskRowModel(
        id = "party",
        title = "Buy party decorations",
        subtitle = "Birthday party",
        checked = true,
        isToday = true,
        chip = "Waiting",
        details = TaskDetailsModel(
            notes = "Robert might be able to bring a helium tank.",
            checklist = listOf(
                ChecklistItemModel("balloons", "Balloons", done = true),
                ChecklistItemModel("hats", "Party hats"),
            ),
            schedule = ScheduleModel(ScheduleKind.Today, "Today", reminder = "9:00 AM"),
            deadline = DeadlineModel("Mon, Nov 16", "12 days left"),
            repeat = RepeatInfoModel(title = "A copy", summary = "Repeat every week"),
            tags = listOf("Errand", "Next"),
        ),
    )

    @Test
    fun aTaskIsWrittenTheSameEverywhere() {
        val block = TaskMarkdown.export(listOf(full)).trimEnd('\n')

        val outputs = listOf(
            exportProject(ExportProject("P", tasks = listOf(full))),
            exportProject(ExportProject("P", headings = listOf(ExportHeading("H", listOf(full))))),
            exportProject(ExportProject("P", later = listOf(full))),
            exportArea(ExportArea("A", listOf(ExportProject("P", tasks = listOf(full))))),
            markdownSection("P", 1, listOf(full)),
            TaskExpansion(null).markdownForSelection(listOf(full), setOf("party")),
        )

        outputs.forEach { out -> assertTrue(out, out.contains(block)) }
    }

    @Test
    fun aProjectIsWrittenWithEveryPartInOrder() {
        val project = ExportProject(
            title = "Trip",
            notes = "Trip notes\r\n",
            tasks = listOf(task("l", "Loose")),
            headings = listOf(ExportHeading("Planning", listOf(task("a", "A")))),
            later = listOf(task("x", "L")),
            logged = listOf(LoggedExport("Done", "11/2")),
        )

        assertEquals(
            md(
                "# Trip",
                "",
                "Trip notes",
                "",
                "- [ ] Loose",
                "",
                "## Planning",
                "",
                "- [ ] A",
                "",
                "## Later",
                "",
                "- [ ] L",
                "",
                "## Logbook",
                "",
                "- [x] Done",
                "  - Completed: 11/2",
            ),
            exportProject(project),
        )
    }

    @Test
    fun anAreaPutsItsProjectsOneLevelDown() {
        val area = ExportArea(
            title = "Home",
            projects = listOf(
                ExportProject("P1", headings = listOf(ExportHeading("H", listOf(task("a", "A"))))),
                ExportProject("P2"),
            ),
        )

        assertEquals(
            md("# Home", "", "## P1", "", "### H", "", "- [ ] A", "", "## P2"),
            exportArea(area),
        )
    }

    @Test
    fun aProjectInsideAnAreaKeepsItsOwnContent() {
        val project = ExportProject("P", tasks = listOf(task("a", "A")))

        assertEquals(md("## P", "", "- [ ] A"), exportProject(project, level = 2))
    }

    @Test
    fun anEmptyProjectIsOnlyItsTitle() {
        assertEquals(md("# Empty"), exportProject(ExportProject("Empty")))
        assertEquals(md("# Untitled"), exportProject(ExportProject("  ")))
    }

    @Test
    fun titlesAreEscapedLikeEveryOtherText() {
        assertEquals(md("# Q&A \\*draft\\*"), exportProject(ExportProject("Q&A *draft*")))
    }

    @Test
    fun projectNotesKeepTheirMarkdown() {
        val out = exportProject(ExportProject("P", notes = "Line 1\n\n- a\n"))

        assertEquals(md("# P", "", "Line 1", "", "- a"), out)
    }

    @Test
    fun loggedTasksUseTheSameWriterAsEveryOtherTask() {
        val out = exportProject(ExportProject("P", logged = listOf(LoggedExport("Shower", "10/29", isRecurring = true))))

        assertEquals(
            md(
                "# P",
                "",
                "## Logbook",
                "",
                "- [x] Shower",
                "  - Completed: 10/29",
                "  - Repeat: copy of a repeating to-do",
            ),
            out,
        )
    }

    @Test
    fun markdownSectionIsOnlyAShortWayToExportAProject() {
        val tasks = listOf(task("a", "A"))

        assertEquals(
            exportProject(ExportProject("T", notes = "n", tasks = tasks), 1).trimEnd('\n'),
            markdownSection("T", 1, tasks, "n"),
        )
    }
}
