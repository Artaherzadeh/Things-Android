package com.things.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Which to-do of a page is open, and the edits made to to-dos on that page. Edits are kept here the
 * moment they are typed (nothing to save), so closing and reopening a card shows the same text.
 * Only the open to-do id survives rotation; the edits live as long as the page is on screen.
 */
@Stable
class TaskExpansion internal constructor(initialId: String?) {
    var expandedId: String? by mutableStateOf(initialId)
        private set

    private val drafts = mutableStateMapOf<String, TaskRowModel>()

    fun isExpanded(id: String): Boolean = expandedId == id

    fun expand(id: String) {
        expandedId = id
    }

    fun collapse() {
        expandedId = null
    }

    /** The to-do with the edits made to it so far. */
    fun resolve(task: TaskRowModel): TaskRowModel = drafts[task.id] ?: task

    fun edit(base: TaskRowModel, change: (TaskRowModel) -> TaskRowModel) {
        drafts[base.id] = change(drafts[base.id] ?: base)
    }

    companion object {
        val Saver: Saver<TaskExpansion, String> = Saver(
            save = { it.expandedId.orEmpty() },
            restore = { TaskExpansion(it.ifEmpty { null }) },
        )
    }
}

@Composable
fun rememberTaskExpansion(): TaskExpansion =
    rememberSaveable(saver = TaskExpansion.Saver) { TaskExpansion(null) }

/**
 * A to-do that opens in place. Tapping the row opens it as a [TaskCard] with an animated change of
 * height; opening another to-do, tapping outside (see ListPageScaffold) or going back closes it.
 * Pass [onDelete] to let the closed row be swiped to the left to reveal a red delete button.
 */
@Composable
fun ExpandableTaskRow(
    task: TaskRowModel,
    expansion: TaskExpansion,
    modifier: Modifier = Modifier,
    recurringStyle: RecurringStyle = RecurringStyle.TrailingIcon,
    onOpen: () -> Unit = {},
    onToggle: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val current = expansion.resolve(task)
    val toggle = {
        expansion.edit(task) { it.copy(checked = !it.checked) }
        onToggle()
    }

    Box(modifier.animateContentSize()) {
        if (expansion.isExpanded(task.id)) {
            BackHandler { expansion.collapse() }
            TaskCard(
                task = current,
                onEdit = { change -> expansion.edit(task, change) },
                onToggle = toggle,
            )
        } else {
            val row: @Composable () -> Unit = {
                TaskRow(
                    task = current,
                    recurringStyle = recurringStyle,
                    onClick = {
                        expansion.expand(task.id)
                        onOpen()
                    },
                    onToggle = toggle,
                )
            }
            if (onDelete != null) {
                SwipeToDeleteRow(onDelete = onDelete, content = row)
            } else {
                row()
            }
        }
    }
}
