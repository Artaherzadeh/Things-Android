package com.things.app.ui.components

import android.content.Context
import android.content.Intent

/**
 * The one place that turns to-dos into Markdown text. The Share entry of the select bar uses it;
 * nothing else in the app writes a to-do as Markdown. Pure Kotlin: no Compose, no Android.
 *
 * Every to-do is a task-list item. What the to-do knows about itself goes under the title as a
 * nested list (In, Label, When, Reminder, Deadline, Repeat, Tags, Checklist), and the notes come
 * last, indented under the item, exactly as typed. To-dos are separated by a blank line:
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
 *    like a heading or a list.
 *  - Notes are Markdown already and are never reformatted: every line is only moved two spaces to
 *    the right so it stays inside its item. Empty lines stay empty (no trailing spaces are added),
 *    spaces at the end of a line are kept, and so is the indentation of nested lists and code. Only
 *    the empty lines at the very end are dropped. Notes that are only blank are left out.
 *  - A blank line separates the notes from the list before them, and when such a list exists a
 *    "Notes:" line comes between, so a list at the start of the notes can never merge with it.
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

    /** The lines of one to-do, with no line break at the end. */
    internal fun block(task: TaskRowModel): List<String> {
        val title = oneLine(task.title).ifEmpty { Untitled }
        val lines = mutableListOf("- ${checkbox(task.checked)} ${escape(title, atLineStart = true)}")

        val facts = facts(task)
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

    private fun facts(task: TaskRowModel): List<String> {
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
 * any, then the [tasks] written by [TaskMarkdown]. A project is level 1, its headings level 2;
 * inside an area a project is level 2 and its headings level 3.
 * The notes are not reformatted: only line endings are made the same and empty lines at the end
 * are dropped.
 */
fun markdownSection(title: String, level: Int, tasks: List<TaskRowModel>, notes: String = ""): String =
    buildString {
        append("#".repeat(level)).append(' ').append(title.ifBlank { "Untitled" })
        val body = notes.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n', ' ', '\t')
        if (body.isNotBlank()) append("\n\n").append(body)
        if (tasks.isNotEmpty()) append("\n\n").append(TaskMarkdown.export(tasks).trimEnd('\n'))
    }

/** Opens the Android share sheet with [markdown] as plain text. Nothing happens for blank text. */
fun shareMarkdown(context: Context, markdown: String) {
    if (markdown.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
