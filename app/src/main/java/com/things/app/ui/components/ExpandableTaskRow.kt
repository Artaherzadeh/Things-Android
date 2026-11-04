package com.things.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * Which to-do of a page is open, and the edits made to to-dos on that page. Edits are kept here the
 * moment they are typed (nothing to save), so closing and reopening a card shows the same text.
 * Only the open to-do id survives rotation; the edits live as long as the page is on screen.
 *
 * It also remembers which to-dos were deleted from the page (swipe, select mode), the changes the
 * select bar made to several to-dos at once, the copies it made, and which to-dos were just created:
 * a new to-do that is closed with an empty title is deleted, not saved.
 */
@Stable
class TaskExpansion internal constructor(initialId: String?) {
    var expandedId: String? by mutableStateOf(initialId)
        private set

    /** Where the open card is on screen, so a touch outside it can close it first. */
    var cardBounds: Rect? = null

    private val drafts = mutableStateMapOf<String, TaskRowModel>()
    private val deletedIds = mutableStateMapOf<String, Boolean>()
    private val patches = mutableStateMapOf<String, List<(TaskRowModel) -> TaskRowModel>>()
    private val duplicates = mutableStateMapOf<String, List<TaskRowModel>>()
    private val known = mutableMapOf<String, TaskRowModel>()
    private val newIds = mutableSetOf<String>()

    fun isExpanded(id: String): Boolean = expandedId == id

    fun isNew(id: String): Boolean = id in newIds

    fun isDeleted(id: String): Boolean = deletedIds[id] == true

    /** Rows tell the page state about the to-dos they draw, so the select bar can act on ids. */
    fun register(task: TaskRowModel) {
        known[task.id] = task
    }

    fun titleOf(id: String): String? = known[id]?.let { resolve(it).title }

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

    private fun checkedStates(ids: Set<String>): List<Boolean> =
        ids.mapNotNull { known[it] }.map { resolve(it).checked }

    /**
     * What the status entry of the select bar is called: Mark as Completed when none of the ticked
     * to-dos is done, Undo when all are, Switch Status when it is mixed.
     */
    fun completionLabel(ids: Set<String>): String {
        val states = checkedStates(ids)
        return when {
            states.isEmpty() || states.none { it } -> "Mark as Completed"
            states.all { it } -> "Undo"
            else -> "Switch Status"
        }
    }

    /** Completes them all, un-completes them all, or flips each one when they are mixed. */
    fun toggleCompleted(ids: Set<String>) {
        val tasks = ids.mapNotNull { known[it] }
        val states = tasks.map { resolve(it).checked }
        tasks.forEach { task ->
            val target = when {
                states.none { it } -> true
                states.all { it } -> false
                else -> !resolve(task).checked
            }
            patchAll(setOf(task.id)) { it.copy(checked = target) }
        }
    }

    /** Adds a copy under each to-do in [ids]. */
    fun duplicate(ids: Set<String>) {
        ids.forEach { id ->
            val task = known[id] ?: return@forEach
            val copy = resolve(task).copy(id = "copy-" + UUID.randomUUID())
            duplicates[id] = (duplicates[id] ?: emptyList()) + copy
        }
    }

    fun duplicatesOf(id: String): List<TaskRowModel> = duplicates[id] ?: emptyList()

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
 * height, a little slower than the default (a softer spring); opening another to-do, touching
 * anything outside the card (see ListPageScaffold) or going back closes it.
 *
 * - Swipe the closed row to the left: it slides and a red cross appears; tapping it deletes the
 *   to-do. The row folds up from the bottom, the rows under it slide up, then [onDelete] is told so
 *   the page can drop it from its own list.
 * - In select mode (the page's [selection], or the one ListPageScaffold provides) a tap ticks or
 *   unticks the row instead of opening it.
 * - A new to-do opens with the keyboard on its title. If the keyboard is closed or the card is
 *   closed while the title is empty, the to-do is deleted.
 * - The calendar button and the date line of the card open the When sheet; the tag button and the
 *   tag chips open the Tags sheet. What is chosen is shown on the card and in the closed row.
 * - When the card closes, the note, checklist and tag marks of the closed row follow what the card
 *   holds: a mark stays only if there is something behind it.
 * - While the add button is dragged over the page the row makes room for the new to-do (see
 *   [DropSlot]).
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
    val sel = selection ?: LocalSelection.current
    val slot = LocalDropSlot.current
    val current = expansion.resolve(task)
    val deleted = expansion.isDeleted(task.id)
    val expanded = expansion.isExpanded(task.id)
    var sheet by remember { mutableStateOf<TaskSheet?>(null) }

    SideEffect { expansion.register(task) }

    val toggle = {
        expansion.edit(task) { it.copy(checked = !current.checked) }
        onToggle()
    }

    // The row folds up first; the page drops it from its list when the animation is over.
    LaunchedEffect(deleted) {
        if (deleted) {
            delay(300)
            onDelete?.invoke()
        }
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

    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (wasExpanded && !expanded && !expansion.isDeleted(task.id)) {
            expansion.edit(task) { it.settled() }
        }
        wasExpanded = expanded
    }

    AnimatedVisibility(
        visible = !deleted,
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Column(
            modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = OpenStiffness,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
            )
        ) {
            if (slot.beforeTaskId == task.id) Spacer(Modifier.height(DropGapHeight))

            Box {
                if (sel != null && sel.active) {
                    TaskRow(
                        task = current,
                        recurringStyle = recurringStyle,
                        selecting = true,
                        selected = sel.isSelected(task.id),
                        onClick = { sel.toggle(task.id) },
                        onToggle = { sel.toggle(task.id) },
                    )
                } else if (expanded) {
                    BackHandler { expansion.collapse() }
                    Box(Modifier.onGloballyPositioned { expansion.cardBounds = it.boundsInRoot() }) {
                        TaskCard(
                            task = current,
                            onEdit = { change -> expansion.edit(task, change) },
                            onToggle = toggle,
                            autoFocus = expansion.isNew(task.id) && current.title.isEmpty(),
                            onScheduleClick = { sheet = TaskSheet.When },
                            onTagsClick = { sheet = TaskSheet.Tags },
                        )
                    }
                } else {
                    SwipeToDeleteRow(onDelete = { expansion.delete(task.id) }) {
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

            if (slot.afterTaskId == task.id) Spacer(Modifier.height(DropGapHeight))

            expansion.duplicatesOf(task.id).forEach { copy ->
                key(copy.id) {
                    ExpandableTaskRow(
                        task = copy,
                        expansion = expansion,
                        recurringStyle = recurringStyle,
                        selection = selection,
                    )
                }
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

/**
 * Stiffness of the spring that opens and closes the card. The default is 400; a lower number is a
 * slower animation, and 300 takes about 15 percent longer.
 */
private const val OpenStiffness = 300f
