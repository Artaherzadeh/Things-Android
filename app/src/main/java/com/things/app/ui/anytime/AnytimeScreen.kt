package com.things.app.ui.anytime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.GroupGap
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.ProjectGroupHeader
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.LayersIcon
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

/** Each project block shows this many to-dos; the rest sit behind "Show N more". */
private const val CollapsedTaskLimit = 3

/**
 * Static Anytime page: to-dos without a date, grouped by project. To-dos with no project come
 * first with no header. A long project block collapses to its first few to-dos. Every to-do opens
 * in place when tapped.
 */
@Composable
fun AnytimeScreen(
    state: AnytimeUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
) {
    var expandedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val expansion = rememberTaskExpansion()

    ListPageScaffold(
        title = "Anytime",
        icon = { LayersIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "anytime_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        expansion = expansion,
        onAddClick = onAddClick,
        // Display order: the to-dos without a project, then each project's to-dos, also the ones
        // hidden behind "Show N more".
        shareableTasks = { state.looseTasks + state.groups.flatMap { it.items } },
    ) {
        items(state.looseTasks, key = { "task-${it.id}" }) { task ->
            ExpandableTaskRow(
                task = task,
                expansion = expansion,
                onOpen = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }

        state.groups.forEachIndexed { index, group ->
            val expanded = group.projectId in expandedGroups
            val visible = if (expanded) group.items else group.items.take(CollapsedTaskLimit)
            val hiddenCount = group.items.size - visible.size

            item(key = "group-${group.projectId}") {
                ProjectGroupHeader(
                    title = group.projectTitle,
                    progress = group.projectProgress,
                    topGap = if (index == 0 && state.looseTasks.isEmpty()) 0.dp else GroupGap,
                    onClick = { onProjectClick(group.projectId) },
                )
            }
            items(visible, key = { "task-${it.id}" }) { task ->
                ExpandableTaskRow(
                    task = task,
                    expansion = expansion,
                    onOpen = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
            if (hiddenCount > 0) {
                item(key = "more-${group.projectId}") {
                    ShowMoreRow(hiddenCount) { expandedGroups = expandedGroups + group.projectId }
                }
            }
        }
    }
}

/** "Show N more" starts at x = 52dp, in line with the to-do titles above it. */
@Composable
private fun ShowMoreRow(count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ThingsSpacing.RowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ThingsSpacing.TextStart, end = ThingsSpacing.PageMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Show $count more", style = ThingsType.Count)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Anytime - sample")
@Composable
private fun AnytimeScreenPreview() {
    ThingsTheme { AnytimeScreen(sampleAnytimeState()) }
}
