package com.things.app.ui.components

import android.content.Context
import android.content.Intent

/**
 * The one place that turns to-dos into Markdown text. Every Share of the app (the select bar, the
 * project page, the area page) goes through it: only the content and the heading level change, the
 * rules are the same everywhere. Pure Kotlin: no Compose, no Android.
 *
 * Every to-do is a task-list item. What the to-do knows about itself goes under the title as a
 * nested list (In, Label, When, Reminder, Completed, Deadline, Repeat, Tags, Checklist), and the
 * notes come last, indented under the item, exactly as typed. To-dos are separated by a blank line:
 *
 *     - [x] Buy party decorations
 *       - In: Birthday party
 *       - When: Today
 *       - Checklist:
 *         - [x] Balloons
 *         - [ ] Party hats
 *
 *       Notes:
 *
 *       Robert might be able to bring a helium tank.
 *
 * Rules that keep the result valid:
 *  - Titles, checklist lines and the other fields are plain text in the app, so the characters that
 *    Markdown would read as formatting are escaped with a backslash, and so is a title that starts
 *    like a heading or a list. Heading texts (area, project, heading) are escaped the same way.
 *  - Notes are Markdown already and are never reformatted: every line is only moved two spaces to
 *    the right so it stays inside its item. Empty lines stay empty (no trailing spaces are added),
 *    spaces at the end of a line are kept, and so is the indentation of nested lists and code. Only
 *    the empty lines at the very end are dropped. Notes that are only blank are left out.
 *  - A blank line separates the notes from the list before them, and when such a list exists a
 *    "Notes:" line comes between, so a list at the start of the notes can never merge with it.
 *
 * Projects and areas: [exportProject] and [exportArea] write the headings (# area, ## project, ###
 * heading; a lone project is # with ## headings) and put every to-do through the same writer.
 */
object TaskMarkdown {
    private const val Indent = "  "
    private const val Untitled = "Untitled"

    /** The characters of a plain text that Markdown would read as formatting. */
    private const val InlineSpecials = "\\`*_[]<>~"

    /** A text that would start a heading or a list when it is the first thing of a line. */
    private val LeadingBlock = Regex("^(#{1,6}(?=\\s|\$)|[+\\-](?=\\s|\$)|\\d{1,9}(?=[.)](?:\\s|\$)))")

    private val LineBreaks = Regex("\\s*[\\r\\n]+\\s*")

    /** The Markdown of [tasks], in the given order, or an empty text when there is no to-do. */
    fun export(tasks: List<TaskRowModel>): String {
        if (tasks.isEmpty()) return ""
        return tasks.joinToString(separator = "\n\n", postfix = "\n") { block(it).joinToString("\n") }
    }

    /**
     * The lines of one to-do, with no line break at the end. [completedOn] is the day a logged to-do
     * was completed; it is written as a field like the others.
     */
    internal fun block(task: TaskRowModel, completedOn: String? = null): List<String> {
        val title = oneLine(task.title).ifEmpty { Untitled }
        val lines = mutableListOf("- ${checkbox(task.checked)} ${escape(title, atLineStart = true)}")

        val facts = facts(task, completedOn)
        lines += facts

        val notes = task.details.notes
        if (notes.isNotBlank()) {
            lines += ""
            if (facts.isNotEmpty()) {
                lines += "${Indent}Notes:"
                lines += ""
            }
            lines += indentNotes(notes)
        }
        return lines
    }

    /** The text of an area, project or heading title, safe to put after the # marks. */
    internal fun headingText(title: String): String = plain(title).ifEmpty { Untitled }

    /** The logged to-dos as to-do blocks, separated by a blank line, with no line break at the end. */
    internal fun exportLogged(entries: List<LoggedExport>): String =
        entries.joinToString(separator = "\n\n") { entry ->
            val task = TaskRowModel(
                id = "logged",
                title = entry.title,
                checked = true,
                isRecurring = entry.isRecurring,
            )
            block(task, completedOn = entry.dateLabel).joinToString("\n")
        }

    private fun facts(task: TaskRowModel, completedOn: String?): List<String> {
        val details = task.details
        val out = mutableListOf<String>()

        fun fact(label: String, value: String) {
            out += "$Indent- $label: $value"
        }

        task.subtitle?.let { plain(it) }?.takeIf { it.isNotEmpty() }?.let { fact("In", it) }
        task.chip?.let { plain(it) }?.takeIf { it.isNotEmpty() }?.let { fact("Label", it) }

        val schedule = details.schedule
        val whenText = schedule?.label?.let { plain(it) }?.takeIf { it.isNotEmpty() }
            ?: if (task.isToday) "Today" else null
        whenText?.let { fact("When", it) }

        val reminder = schedule?.reminder?.let { plain(it) }?.takeIf { it.isNotEmpty() }
        if (reminder != null) {
            fact("Reminder", reminder)
        } else if (task.hasReminder) {
            fact("Reminder", "set")
        }

        completedOn?.let { plain(it) }?.takeIf { it.isNotEmpty() }?.let { fact("Completed", it) }

        details.deadline?.let { deadline ->
            val date = plain(deadline.dateLabel)
            val left = deadline.remainingLabel?.let { plain(it) }?.takeIf { it.isNotEmpty() }
            if (date.isNotEmpty()) fact("Deadline", if (left != null) "$date ($left)" else date)
        }

        val repeat = details.repeat
        val repeatSummary = repeat?.summary?.let { plain(it) }.orEmpty()
        val repeatTitle = repeat?.title?.let { plain(it) }.orEmpty()
        when {
            repeatSummary.isNotEmpty() && repeatTitle.isNotEmpty() -> fact("Repeat", "$repeatSummary ($repeatTitle)")
            repeatSummary.isNotEmpty() -> fact("Repeat", repeatSummary)
            repeatTitle.isNotEmpty() -> fact("Repeat", repeatTitle)
            task.isRepeatingTemplate -> fact("Repeat", "repeating template")
            task.isRecurring -> fact("Repeat", "copy of a repeating to-do")
        }

        val tags = details.tags.map { plain(it) }.filter { it.isNotEmpty() }
        if (tags.isNotEmpty()) fact("Tags", tags.joinToString(", "))

        if (details.checklist.isNotEmpty()) {
            out += "$Indent- Checklist:"
            details.checklist.forEach { item ->
                val title = oneLine(item.title).ifEmpty { Untitled }
                out += "$Indent$Indent- ${checkbox(item.done)} ${escape(title, atLineStart = true)}"
            }
        }
        return out
    }

    /**
     * The notes moved two spaces to the right, line by line. Nothing else changes: not the spaces at
     * the end of a line, not the indentation, not the empty lines in the middle.
     */
    private fun indentNotes(notes: String): List<String> {
        val lines = notes.replace("\r\n", "\n").replace('\r', '\n').split('\n').dropLastWhile { it.isEmpty() }
        return lines.map { if (it.isEmpty()) it else Indent + it }
    }

    private fun checkbox(done: Boolean) = if (done) "[x]" else "[ ]"

    /** A text on one line: line breaks become one space and the ends are trimmed. */
    private fun oneLine(text: String): String = text.replace(LineBreaks, " ").trim()

    /** A field of the to-do as it is written after its label. */
    private fun plain(text: String): String = escape(oneLine(text), atLineStart = false)

    private fun escape(text: String, atLineStart: Boolean): String {
        val inline = buildString {
            for (c in text) {
                if (c in InlineSpecials) append('\\')
                append(c)
            }
        }
        if (!atLineStart) return inline
        val match = LeadingBlock.find(inline) ?: return inline
        val cut = if (match.value[0].isDigit()) match.value.length else 0
        return inline.substring(0, cut) + "\\" + inline.substring(cut)
    }
}

/** A heading of a project and its to-dos, for [exportProject]. */
data class ExportHeading(val title: String, val tasks: List<TaskRowModel>)

/** A logged (completed) to-do: what the app keeps of it is its title, the day and whether it repeats. */
data class LoggedExport(val title: String, val dateLabel: String, val isRecurring: Boolean = false)

/**
 * Everything of a project that is exported, in the order it is written: the notes, the to-dos in no
 * heading, each heading with its to-dos, the later items and the logged ones. The lists must already
 * hold the current data (edits applied, deleted to-dos left out) in the order the page shows them.
 */
data class ExportProject(
    val title: String,
    val notes: String = "",
    val tasks: List<TaskRowModel> = emptyList(),
    val headings: List<ExportHeading> = emptyList(),
    val later: List<TaskRowModel> = emptyList(),
    val logged: List<LoggedExport> = emptyList(),
)

/** An area and its projects, for [exportArea]. */
data class ExportArea(val title: String, val projects: List<ExportProject>)

private const val MaxHeadingLevel = 6

private fun headingLine(level: Int, title: String): String =
    "#".repeat(level.coerceIn(1, MaxHeadingLevel)) + " " + TaskMarkdown.headingText(title)

/** Project notes as they were typed: only the line ends are made the same and the empty end is cut. */
private fun notesText(notes: String): String? {
    val text = notes.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n', ' ', '\t')
    return text.takeIf { it.isNotBlank() }
}

private fun projectParts(project: ExportProject, level: Int): List<String> {
    val parts = mutableListOf(headingLine(level, project.title))
    notesText(project.notes)?.let { parts += it }
    if (project.tasks.isNotEmpty()) parts += TaskMarkdown.export(project.tasks).trimEnd('\n')
    project.headings.forEach { heading ->
        parts += headingLine(level + 1, heading.title)
        if (heading.tasks.isNotEmpty()) parts += TaskMarkdown.export(heading.tasks).trimEnd('\n')
    }
    if (project.later.isNotEmpty()) {
        parts += headingLine(level + 1, "Later")
        parts += TaskMarkdown.export(project.later).trimEnd('\n')
    }
    if (project.logged.isNotEmpty()) {
        parts += headingLine(level + 1, "Logbook")
        parts += TaskMarkdown.exportLogged(project.logged)
    }
    return parts
}

/**
 * A project as Markdown: "# title", its notes, its to-dos, then "## heading" with the to-dos of each
 * heading, "## Later" and "## Logbook". [level] is the level of the project title (1 for a project on
 * its own, 2 inside an area). Ends with one line break.
 */
fun exportProject(project: ExportProject, level: Int = 1): String =
    projectParts(project, level).joinToString("\n\n") + "\n"

/**
 * An area as Markdown: "# area", then each project at level 2 (its headings at level 3), in the order
 * given. Ends with one line break.
 */
fun exportArea(area: ExportArea): String {
    val parts = mutableListOf(headingLine(1, area.title))
    area.projects.forEach { parts += projectParts(it, 2) }
    return parts.joinToString("\n\n") + "\n"
}

/**
 * The to-dos in [selected], with the edits made on the page, in the order of [ordered].
 *
 * [ordered] is every to-do of the page in the order the page shows them, whether it is on screen or
 * not: only the page knows that order (lists are lazy and can be reordered), so it is never taken
 * from the order rows were drawn in. A copy made by Duplicate comes right after the to-do it was
 * made from, like on screen. To-dos deleted on the page, and the copies under them, are left out.
 * The result holds the current data of each to-do: typed edits and the changes of the select bar.
 */
fun TaskExpansion.selectedInDisplayOrder(ordered: List<TaskRowModel>, selected: Set<String>): List<TaskRowModel> {
    val out = mutableListOf<TaskRowModel>()
    val seen = mutableSetOf<String>()

    fun visit(task: TaskRowModel) {
        if (isDeleted(task.id) || !seen.add(task.id)) return
        if (task.id in selected) out += resolve(task)
        duplicatesOf(task.id).forEach { visit(it) }
    }

    ordered.forEach { visit(it) }
    return out
}

/** The Markdown of the selected to-dos, see [selectedInDisplayOrder] and [TaskMarkdown]. */
fun TaskExpansion.markdownForSelection(ordered: List<TaskRowModel>, selected: Set<String>): String =
    TaskMarkdown.export(selectedInDisplayOrder(ordered, selected))

/**
 * One to-do as Markdown: the same lines [TaskMarkdown.export] writes for it, so the whole app has
 * one exporter. Kept for callers that write a single to-do.
 */
fun TaskRowModel.toMarkdown(): String = TaskMarkdown.block(this).joinToString("\n")

/**
 * A Markdown heading of [level] (1 is #, 2 is ##) with the [title], then the [notes] if there are
 * any, then the [tasks] written by [TaskMarkdown]. Kept for callers that write one section; it is
 * [exportProject] without the headings, later and logged parts and without the final line break.
 */
fun markdownSection(title: String, level: Int, tasks: List<TaskRowModel>, notes: String = ""): String =
    exportProject(ExportProject(title = title, notes = notes, tasks = tasks), level).trimEnd('\n')

/** Opens the Android share sheet with [markdown] as plain text. Nothing happens for blank text. */
fun shareMarkdown(context: Context, markdown: String) {
    if (markdown.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
