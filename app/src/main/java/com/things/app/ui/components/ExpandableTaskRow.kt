package com.things.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Which to-do of a page is open, and the edits made to to-dos on that page. Edits are kept here the
 * moment they are typed (nothing to save), so closing and reopening a card shows the same text.
 * Only the open to-do id survives rotation; the edits live as long as the page is on screen.
 *
 * It also remembers which to-dos were deleted from the page (swipe, select mode), the changes the
 * select bar made to several to-dos at once, and which to-dos were just created: a new to-do that
 * is closed with an empty title is deleted, not saved.
 */
@Stable
class TaskExpansion internal constructor(initialId: String?) {
    var expandedId: String? by mutableStateOf(initialId)
        private set

    private val drafts = mutableStateMapOf<String, TaskRowModel>()
    private val deletedIds = mutableStateMapOf<String, Boolean>()
    private val patches = mutableStateMapOf<String, List<(TaskRowModel) -> TaskRowModel>>()
    private val newIds = mutableSetOf<String>()

    fun isExpanded(id: String): Boolean = expandedId == id

    fun isNew(id: String): Boolean = id in newIds

    fun isDeleted(id: String): Boolean = deletedIds[id] == true

    /** Opens [id]. A new to-do that is still empty and was open before is discarded. */
    fun expand(id: String) {
        if (expandedId != null && expandedId != id) collapse()
        expandedId = id
    }

    /** Opens a to-do that was just created, so it is deleted again if it is closed while empty. */
    fun expandNew(id: String) {
        newIds.add(id)
        expand(id)
    }

    fun collapse() {
        val id = expandedId
        expandedId = null
        if (id != null && id in newIds && drafts[id]?.title.isNullOrBlank()) delete(id)
    }

    fun delete(id: String) {
        deletedIds[id] = true
        if (expandedId == id) expandedId = null
    }

    fun deleteAll(ids: Set<String>) = ids.forEach { delete(it) }

    /** Applies [change] to every to-do in [ids], for example a date or tags from the select bar. */
    fun patchAll(ids: Set<String>, change: (TaskRowModel) -> TaskRowModel) {
        ids.forEach { id -> patches[id] = (patches[id] ?: emptyList()) + change }
    }

    fun markCompleted(ids: Set<String>) = patchAll(ids) { it.copy(checked = true) }

    /** The to-do with the edits made to it so far. */
    fun resolve(task: TaskRowModel): TaskRowModel {
        val base = drafts[task.id] ?: task
        val list = patches[task.id] ?: return base
        return list.fold(base) { acc, change -> change(acc) }
    }

    fun edit(base: TaskRowModel, change: (TaskRowModel) -> TaskRowModel) {
        drafts[base.id] = change(resolve(base))
        patches.remove(base.id)
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
 *
 * - Swipe the closed row to the left: it slides and a red cross appears; tapping it deletes the
 *   to-do. [onDelete] is told so the page can drop it from its own list.
 * - In select mode (the page's [selection], or the one ListPageScaffold provides) a tap ticks or
 *   unticks the row instead of opening it.
 * - A new to-do opens with the keyboard on its title. If the keyboard is closed or the card is
 *   closed while the title is empty, the to-do is deleted.
 * - The calendar button and the date line of the card open the When sheet; the tag button and the
 *   tag chips open the Tags sheet. What is chosen is shown on the card and in the closed row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableTaskRow(
    task: TaskRowModel,
    expansion: TaskExpansion,
    modifier: Modifier = Modifier,
    recurringStyle: RecurringStyle = RecurringStyle.TrailingIcon,
    selection: SelectionState? = null,
    onOpen: () -> Unit = {},
    onToggle: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    if (expansion.isDeleted(task.id)) return

    val sel = selection ?: LocalSelection.current
    val current = expansion.resolve(task)
    var sheet by remember { mutableStateOf<TaskSheet?>(null) }
    val toggle = {
        expansion.edit(task) { it.copy(checked = !current.checked) }
        onToggle()
    }
    val delete = {
        onDelete?.invoke()
        expansion.delete(task.id)
    }

    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible &&
            expansion.isExpanded(task.id) && expansion.isNew(task.id) &&
            expansion.resolve(task).title.isBlank()
        ) {
            expansion.collapse()
        }
        imeWasVisible = imeVisible
    }

    Box(modifier.animateContentSize()) {
        if (sel != null && sel.active) {
            TaskRow(
                task = current,
                recurringStyle = recurringStyle,
                selecting = true,
                selected = sel.isSelected(task.id),
                onClick = { sel.toggle(task.id) },
                onToggle = { sel.toggle(task.id) },
            )
        } else if (expansion.isExpanded(task.id)) {
            BackHandler { expansion.collapse() }
            TaskCard(
                task = current,
                onEdit = { change -> expansion.edit(task, change) },
                onToggle = toggle,
                autoFocus = expansion.isNew(task.id) && current.title.isEmpty(),
                onScheduleClick = { sheet = TaskSheet.When },
                onTagsClick = { sheet = TaskSheet.Tags },
            )
        } else {
            SwipeToDeleteRow(onDelete = delete) {
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
        }
    }

    when (sheet) {
        TaskSheet.When -> WhenSheet(
            current = current.details.schedule,
            onDismiss = { sheet = null },
            onPick = { schedule -> expansion.edit(task) { it.withSchedule(schedule) } },
        )
        TaskSheet.Tags -> TagsSheet(
            selected = current.details.tags,
            onDismiss = { sheet = null },
            onDone = { tags -> expansion.edit(task) { it.withTags(tags) } },
        )
        null -> Unit
    }
}
