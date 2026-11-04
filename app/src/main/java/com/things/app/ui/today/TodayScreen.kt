package com.things.app.ui.today

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.AddTarget
import com.things.app.ui.components.DefaultEnabledMenuActions
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.GroupGap
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.components.ProjectGroupHeader
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.rememberReorderState
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.components.reorderable
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsTheme
import java.util.UUID

private const val LooseContainer = ""
private const val TodoPrefix = "todo-"
private const val GroupPrefix = "group-"

private fun <T> List<T>.move(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) {
        this
    } else {
        toMutableList().also { it.add(to, it.removeAt(from)) }
    }

/**
 * Today page. Everything comes from [state]; edits (reordering, deleting, adding, collapsing) are
 * kept in memory while the page is on screen and every action is also reported through a callback
 * that defaults to a no-op.
 *
 * Order: to-dos with no project come first, flat and without a header; then one block per
 * project (header with progress ring, name and chevron, a divider, then that project's to-dos).
 * - Tap a project name: opens that project's page.
 * - Tap the chevron: hides or shows the project's to-dos.
 * - Tap a to-do: opens it in place. Swipe it to the left for a red delete button.
 * - Hold a to-do or a project and move it: the row lifts and follows the finger. To-dos move inside
 *   their own block; projects move among projects, with every block folded while one is held.
 * - Add button: tap adds a to-do at the top; drag it over a project to add there.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onMenuAction: (PageMenuAction) -> Unit = {},
    enabledMenuActions: Set<PageMenuAction> = DefaultEnabledMenuActions,
    onProjectClick: (String) -> Unit = {},
    onItemClick: (String) -> Unit = {},
    onToggleItem: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
) {
    val expansion = rememberTaskExpansion()
    val listState = rememberLazyListState()
    var looseItems by remember(state) { mutableStateOf(state.looseItems) }
    var groups by remember(state) { mutableStateOf(state.groups) }
    var collapsed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var holdingProject by remember { mutableStateOf(false) }

    val hidden = if (holdingProject) groups.map { it.projectId }.toSet() else collapsed

    fun containerOf(key: Any): String? {
        val s = key as? String ?: return null
        if (!s.startsWith(TodoPrefix)) return null
        val id = s.removePrefix(TodoPrefix)
        if (looseItems.any { it.id == id }) return LooseContainer
        return groups.firstOrNull { g -> g.items.any { it.id == id } }?.projectId
    }

    fun deleteTask(id: String) {
        looseItems = looseItems.filter { it.id != id }
        groups = groups.map { g -> g.copy(items = g.items.filter { it.id != id }) }
    }

    fun addTask(target: AddTarget) {
        val task = TaskRowModel(id = "new-" + UUID.randomUUID(), title = "")
        when (target) {
            is AddTarget.Top -> looseItems = listOf(task) + looseItems
            is AddTarget.InGroup -> {
                groups = groups.map { g ->
                    if (g.projectId == target.groupId) g.copy(items = listOf(task) + g.items) else g
                }
                collapsed = collapsed - target.groupId
            }
            is AddTarget.Heading -> return // Today has no headings
        }
        expansion.expand(task.id)
    }

    val reorder = rememberReorderState(listState)
    reorder.canMove = { from, to ->
        val f = from as? String
        val t = to as? String
        when {
            f == null || t == null -> false
            f.startsWith(GroupPrefix) -> t.startsWith(GroupPrefix)
            f.startsWith(TodoPrefix) -> t.startsWith(TodoPrefix) &&
                containerOf(f) != null && containerOf(f) == containerOf(t)
            else -> false
        }
    }
    reorder.onStart = { key ->
        if ((key as? String)?.startsWith(TodoPrefix) == true) expansion.collapse() else holdingProject = true
    }
    reorder.onEnd = { holdingProject = false }
    reorder.onMove = { from, to ->
        val f = from as String
        val t = to as String
        if (f.startsWith(GroupPrefix)) {
            val fromId = f.removePrefix(GroupPrefix)
            val toId = t.removePrefix(GroupPrefix)
            groups = groups.move(
                groups.indexOfFirst { it.projectId == fromId },
                groups.indexOfFirst { it.projectId == toId },
            )
        } else {
            val fromId = f.removePrefix(TodoPrefix)
            val toId = t.removePrefix(TodoPrefix)
            when (val container = containerOf(f)) {
                null -> Unit
                LooseContainer -> looseItems = looseItems.move(
                    looseItems.indexOfFirst { it.id == fromId },
                    looseItems.indexOfFirst { it.id == toId },
                )
                else -> groups = groups.map { g ->
                    if (g.projectId == container) {
                        g.copy(
                            items = g.items.move(
                                g.items.indexOfFirst { it.id == fromId },
                                g.items.indexOfFirst { it.id == toId },
                            )
                        )
                    } else {
                        g
                    }
                }
            }
        }
    }

    ListPageScaffold(
        title = "Today",
        icon = { StarIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "today_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        onMenuAction = onMenuAction,
        enabledMenuActions = enabledMenuActions,
        expansion = expansion,
        listState = listState,
        onAddClick = {
            addTask(AddTarget.Top)
            onAddClick()
        },
        onAddAt = { target -> addTask(target) },
        groupForKey = { key ->
            val s = key as? String
            when {
                s == null -> null
                s.startsWith(GroupPrefix) -> s.removePrefix(GroupPrefix)
                s.startsWith(TodoPrefix) -> containerOf(s)?.takeIf { it != LooseContainer }
                else -> null
            }
        },
    ) {
        items(looseItems, key = { "$TodoPrefix${it.id}" }) { item ->
            ExpandableTaskRow(
                task = item,
                expansion = expansion,
                modifier = Modifier.reorderable(reorder, "$TodoPrefix${item.id}"),
                onOpen = { onItemClick(item.id) },
                onToggle = { onToggleItem(item.id) },
                onDelete = { deleteTask(item.id) },
            )
        }

        groups.forEachIndexed { index, group ->
            item(key = "$GroupPrefix${group.projectId}") {
                ProjectGroupHeader(
                    title = group.projectTitle,
                    progress = group.projectProgress,
                    modifier = Modifier.reorderable(reorder, "$GroupPrefix${group.projectId}"),
                    topGap = if (index == 0 && looseItems.isEmpty()) 0.dp else GroupGap,
                    onClick = { onProjectClick(group.projectId) },
                    expanded = group.projectId !in hidden,
                    onToggleExpanded = {
                        collapsed = if (group.projectId in collapsed) {
                            collapsed - group.projectId
                        } else {
                            collapsed + group.projectId
                        }
                    },
                )
            }
            if (group.projectId !in hidden) {
                items(group.items, key = { "$TodoPrefix${it.id}" }) { item ->
                    ExpandableTaskRow(
                        task = item,
                        expansion = expansion,
                        modifier = Modifier.reorderable(reorder, "$TodoPrefix${item.id}"),
                        onOpen = { onItemClick(item.id) },
                        onToggle = { onToggleItem(item.id) },
                        onDelete = { deleteTask(item.id) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Today - sample")
@Composable
private fun TodayScreenPreview() {
    ThingsTheme { TodayScreen(sampleTodayState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Today - projects only")
@Composable
private fun TodayScreenProjectsOnlyPreview() {
    ThingsTheme { TodayScreen(sampleTodayState().copy(looseItems = emptyList())) }
}
