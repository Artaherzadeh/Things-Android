package com.things.app.ui.components

/**
 * The to-dos of [list] as they are now: deleted ones are left out and every one holds the edits made
 * on the page (typed text, ticks, dates, tags, checklist, notes, repeat). With [includeCopies] the
 * copies made by Duplicate in the select bar come right after the to-do they were made from, like
 * on screen.
 */
fun TaskExpansion.liveTasks(list: List<TaskRowModel>, includeCopies: Boolean = true): List<TaskRowModel> {
    val out = mutableListOf<TaskRowModel>()

    fun visit(task: TaskRowModel) {
        if (isDeleted(task.id)) return
        out += resolve(task)
        if (includeCopies) duplicatesOf(task.id).forEach { visit(it) }
    }

    list.forEach { visit(it) }
    return out
}
