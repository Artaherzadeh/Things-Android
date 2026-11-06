package com.things.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests of the Markdown that Share builds: the exporter itself and the choice of to-dos (which are
 * ticked, what was edited, what was deleted, in which order). Plain JVM tests: nothing here needs a
 * device or a screen.
 */
class TaskMarkdownTest {

    private fun md(vararg lines: String) = lines.joinToString("\n") + "\n"

    private fun task(id: String, title: String, checked: Boolean = false) =
        TaskRowModel(id = id, title = title, checked = checked)

    private fun titlesOf(markdown: String) =
        markdown.lines().filter { it.startsWith("- [") }

    // ---------------------------------------------------------------- the exporter

    @Test
    fun fullyPopulatedTaskExportsEveryField() {
        val party = TaskRowModel(
            id = "party",
            title = "Buy party decorations",
            subtitle = "Birthday party",
            checked = true,
            isRecurring = true,
            hasReminder = true,
            hasTag = true,
            hasNote = true,
            hasChecklist = true,
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
                repeat = RepeatInfoModel(
                    title = "This is a copy of a repeating to-do",
                    summary = "Repeat every week on Friday",
                ),
                tags = listOf("Errand", "Next"),
            ),
        )

        assertEquals(
            md(
                "- [x] Buy party decorations",
                "  - In: Birthday party",
                "  - Label: Waiting",
                "  - When: Today",
                "  - Reminder: 9:00 AM",
                "  - Deadline: Mon, Nov 16 (12 days left)",
                "  - Repeat: Repeat every week on Friday (This is a copy of a repeating to-do)",
                "  - Tags: Errand, Next",
                "  - Checklist:",
                "    - [x] Balloons",
                "    - [ ] Party hats",
                "",
                "  Notes:",
                "",
                "  Robert might be able to bring a helium tank.",
            ),
            TaskMarkdown.export(listOf(party)),
        )
    }

    @Test
    fun multipleTasksAreSeparatedByABlankLine() {
        val a = task("a", "A")
        val b = TaskRowModel(
            id = "b",
            title = "B",
            checked = true,
            details = TaskDetailsModel(notes = "Read it before the weekend."),
        )

        assertEquals(
            md(
                "- [ ] A",
                "",
                "- [x] B",
                "",
                "  Read it before the weekend.",
            ),
            TaskMarkdown.export(listOf(a, b)),
        )
    }

    @Test
    fun noTasksExportNothing() {
        assertEquals("", TaskMarkdown.export(emptyList()))
    }

    @Test
    fun multilineMarkdownNotesAreOnlyIndented() {
        val notes = "Campgrounds:\n\n1. *Lakeview*\n   - Boat rentals\n\n```\ncode  \n  indented\n```\nend  \n"
        val trip = TaskRowModel("trip", "Trip", details = TaskDetailsModel(notes = notes))

        assertEquals(
            md(
                "- [ ] Trip",
                "",
                "  Campgrounds:",
                "",
                "  1. *Lakeview*",
                "     - Boat rentals",
                "",
                "  ```",
                "  code  ",
                "    indented",
                "  ```",
                "  end  ",
            ),
            TaskMarkdown.export(listOf(trip)),
        )
    }

    @Test
    fun notesThatStartWithAListAreKeptApartFromTheFieldsBeforeThem() {
        val t = TaskRowModel(
            "t",
            "T",
            details = TaskDetailsModel(notes = "- one\n- two", tags = listOf("X")),
        )

        assertEquals(
            md(
                "- [ ] T",
                "  - Tags: X",
                "",
                "  Notes:",
                "",
                "  - one",
                "  - two",
            ),
            TaskMarkdown.export(listOf(t)),
        )
    }

    @Test
    fun windowsLineEndingsInNotesBecomeNewLines() {
        val t = TaskRowModel("t", "T", details = TaskDetailsModel(notes = "a\r\n\r\nb"))

        assertEquals(md("- [ ] T", "", "  a", "", "  b"), TaskMarkdown.export(listOf(t)))
    }

    @Test
    fun blankNotesAreLeftOut() {
        val x = TaskRowModel("t", "X", details = TaskDetailsModel(notes = "  \n \n"))

        assertEquals(md("- [ ] X"), TaskMarkdown.export(listOf(x)))
    }

    @Test
    fun plainTextFieldsAreEscapedSoTheyCannotChangeTheStructure() {
        fun exported(title: String) = TaskMarkdown.export(listOf(task("t", title)))

        assertEquals(md("- [ ] \\*Urgent\\* call \\[Bob\\] #1"), exported("*Urgent* call [Bob] #1"))
        assertEquals(md("- [ ] \\# Plan"), exported("# Plan"))
        assertEquals(md("- [ ] 1\\. Start"), exported("1. Start"))
        assertEquals(md("- [ ] \\- dash"), exported("- dash"))
        assertEquals(md("- [ ] snake\\_case \\<b\\>"), exported("snake_case <b>"))
        assertEquals(md("- [ ] Line one line two"), exported("Line one\nline two"))
        assertEquals(md("- [ ] Untitled"), exported("  "))
    }

    @Test
    fun marksThatHaveNoDetailsStillShowTheirMeaning() {
        val cleaning = TaskRowModel(
            id = "r",
            title = "Cleaning House",
            hasReminder = true,
            isToday = true,
            chip = "Fri",
            isRepeatingTemplate = true,
        )

        assertEquals(
            md(
                "- [ ] Cleaning House",
                "  - Label: Fri",
                "  - When: Today",
                "  - Reminder: set",
                "  - Repeat: repeating template",
            ),
            TaskMarkdown.export(listOf(cleaning)),
        )
        assertEquals(
            md("- [ ] Shower", "  - Repeat: copy of a repeating to-do"),
            TaskMarkdown.export(listOf(TaskRowModel("s", "Shower", isRecurring = true))),
        )
    }

    // ---------------------------------------------------------------- choosing the to-dos

    @Test
    fun editedDataAndSelectBarChangesAreExported() {
        val base = task("e1", "Old title")
        val expansion = TaskExpansion(null)
        expansion.edit(base) {
            it.copy(title = "New title", details = it.details.copy(notes = "Edited note"))
        }
        expansion.patchAll(setOf("e1")) { it.withSchedule(ScheduleModel(ScheduleKind.Today, "Today")) }
        expansion.patchAll(setOf("e1")) { it.withTags(listOf("Errand")) }
        expansion.patchAll(setOf("e1")) { it.copy(checked = true) }

        assertEquals(
            md(
                "- [x] New title",
                "  - When: Today",
                "  - Tags: Errand",
                "",
                "  Notes:",
                "",
                "  Edited note",
            ),
            expansion.markdownForSelection(listOf(base), setOf("e1")),
        )
    }

    @Test
    fun deletedTasksAreLeftOut() {
        val a = task("a", "A")
        val b = task("b", "B")
        val c = task("c", "C")
        val expansion = TaskExpansion(null)
        expansion.delete("b")

        assertEquals(
            md("- [ ] A", "", "- [ ] C"),
            expansion.markdownForSelection(listOf(a, b, c), setOf("a", "b", "c")),
        )
    }

    @Test
    fun copiesUnderADeletedTaskAreLeftOutToo() {
        val b = task("b", "B")
        val expansion = TaskExpansion(null)
        expansion.register(b)
        expansion.duplicate(setOf("b"))
        val copyId = expansion.duplicatesOf("b").single().id
        expansion.delete("b")

        assertEquals("", expansion.markdownForSelection(listOf(b), setOf("b", copyId)))
    }

    @Test
    fun aCopyComesRightAfterItsOriginal() {
        val a = task("a", "A")
        val b = task("b", "B")
        val expansion = TaskExpansion(null)
        expansion.register(a)
        expansion.duplicate(setOf("a"))
        val copy = expansion.duplicatesOf("a").single()
        expansion.edit(copy) { it.copy(title = "A copy") }

        assertEquals(
            md("- [ ] A", "", "- [ ] A copy", "", "- [ ] B"),
            expansion.markdownForSelection(listOf(a, b), setOf("a", "b", copy.id)),
        )
    }

    @Test
    fun theOrderOfThePageWinsOverTheOrderRowsWereDrawnIn() {
        val a = task("a", "A")
        val b = task("b", "B")
        val c = task("c", "C")
        val expansion = TaskExpansion(null)
        // The rows were drawn (registered) as A, B, C, but the page shows C, A, B after a reorder.
        listOf(a, b, c).forEach { expansion.register(it) }
        val display = listOf(c, a, b)

        assertEquals(
            md("- [ ] C", "", "- [ ] A", "", "- [ ] B"),
            expansion.markdownForSelection(display, setOf("a", "b", "c")),
        )
        assertEquals(
            md("- [ ] C", "", "- [ ] B"),
            expansion.markdownForSelection(display, setOf("b", "c")),
        )
    }

    @Test
    fun selectedTasksThatAreOffScreenAreExportedAndTheOthersAreNot() {
        val all = (0 until 50).map { task("t$it", "Task $it") }
        val expansion = TaskExpansion(null)
        // Only the first rows were ever drawn; the lazy list never composed the rest.
        all.take(5).forEach { expansion.register(it) }
        expansion.edit(all[47]) { it.copy(title = "Edited far away") }

        val markdown = expansion.markdownForSelection(all, setOf("t47", "t3", "t12"))

        assertEquals(
            md("- [ ] Task 3", "", "- [ ] Task 12", "", "- [ ] Edited far away"),
            markdown,
        )
        assertEquals(3, titlesOf(markdown).size)
    }

    @Test
    fun aTaskListedTwiceIsExportedOnce() {
        val a = task("a", "A")
        val expansion = TaskExpansion(null)

        assertEquals(md("- [ ] A"), expansion.markdownForSelection(listOf(a, a), setOf("a")))
    }

    @Test
    fun nothingSelectedExportsNothing() {
        val expansion = TaskExpansion(null)

        assertEquals("", expansion.markdownForSelection(listOf(task("a", "A")), emptySet()))
    }
}
