package com.things.app.ui.project

import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.exportProject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectExportMappingTest {

    private fun md(vararg lines: String) = lines.joinToString("\n") + "\n"

    @Test
    fun aProjectStateIsExportedWithEveryPart() {
        val state = ProjectUiState(
            id = "p",
            title = "Trip",
            progress = 0f,
            notes = "N",
            tasks = listOf(TaskRowModel("a", "A")),
            headings = listOf(ProjectHeadingModel("h", "H", listOf(TaskRowModel("b", "B")))),
            laterTasks = listOf(TaskRowModel("c", "C")),
            loggedTasks = listOf(LoggedTaskModel("l", "Done", "11/2", isRecurring = true)),
        )

        assertEquals(
            md(
                "# Trip",
                "",
                "N",
                "",
                "- [ ] A",
                "",
                "## H",
                "",
                "- [ ] B",
                "",
                "## Later",
                "",
                "- [ ] C",
                "",
                "## Logbook",
                "",
                "- [x] Done",
                "  - Completed: 11/2",
                "  - Repeat: copy of a repeating to-do",
            ),
            exportProject(state.toExportProject()),
        )
    }
}
