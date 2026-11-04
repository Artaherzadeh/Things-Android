package com.things.app.ui.today

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.DefaultEnabledMenuActions
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.GroupGap
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.components.ProjectGroupHeader
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsTheme

/**
 * Static Today page. Stateless: everything comes from [state] and every action is a callback
 * that defaults to a no-op.
 *
 * Order: to-dos with no project come first, flat and without a header; then one block per
 * project (header with progress ring, name and chevron, a divider, then that project's to-dos).
 * Every to-do opens in place when tapped.
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
        onAddClick = onAddClick,
    ) {
        items(state.looseItems, key = { "todo-${it.id}" }) { item ->
            ExpandableTaskRow(
                task = item,
                expansion = expansion,
                onOpen = { onItemClick(item.id) },
                onToggle = { onToggleItem(item.id) },
            )
        }

        state.groups.forEachIndexed { index, group ->
            item(key = "group-${group.projectId}") {
                ProjectGroupHeader(
                    title = group.projectTitle,
                    progress = group.projectProgress,
                    topGap = if (index == 0 && state.looseItems.isEmpty()) 0.dp else GroupGap,
                    onClick = { onProjectClick(group.projectId) },
                )
            }
            items(group.items, key = { "todo-${it.id}" }) { item ->
                ExpandableTaskRow(
                    task = item,
                    expansion = expansion,
                    onOpen = { onItemClick(item.id) },
                    onToggle = { onToggleItem(item.id) },
                )
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
