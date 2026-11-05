package com.things.app.ui.components

import android.content.Context
import android.content.Intent

/**
 * One to-do and everything it holds, as Markdown: a task-list line with the title, then indented
 * under it the notes, the When line, the deadline, the repeat, the tags and the checklist.
 * Static glue until the logic layer makes the texts.
 */
fun TaskRowModel.toMarkdown(): String {
    val out = StringBuilder()
    out.append(if (checked) "- [x] " else "- [ ] ").append(title.ifBlank { "Untitled" })

    fun line(text: String) {
        out.append("\n  ").append(text)
    }

    if (details.notes.isNotBlank()) {
        details.notes.trim().lines().forEach { if (it.isBlank()) out.append("\n") else line(it.trimEnd()) }
    }
    details.schedule?.let { s ->
        line("- **When:** " + s.label + (s.reminder?.let { ", $it" } ?: ""))
    }
    details.deadline?.let { d ->
        line("- **Deadline:** " + d.dateLabel + (d.remainingLabel?.let { " ($it)" } ?: ""))
    }
    details.repeat?.let { r -> line("- **Repeat:** ${r.title}. ${r.summary}") }
    if (details.tags.isNotEmpty()) line("- **Tags:** " + details.tags.joinToString(", "))
    details.checklist.forEach { item ->
        line((if (item.done) "- [x] " else "- [ ] ") + item.title)
    }
    return out.toString()
}

/**
 * A Markdown heading of [level] (1 is #, 2 is ##) with the [title], then the [notes] if there are
 * any, then the [tasks] as task-list lines. A project is level 1, its headings level 2; inside an
 * area a project is level 2 and its headings level 3.
 */
fun markdownSection(title: String, level: Int, tasks: List<TaskRowModel>, notes: String = ""): String =
    buildString {
        append("#".repeat(level)).append(' ').append(title.ifBlank { "Untitled" })
        if (notes.isNotBlank()) append("\n\n").append(notes.trim())
        if (tasks.isNotEmpty()) append("\n\n").append(tasks.joinToString("\n") { it.toMarkdown() })
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
