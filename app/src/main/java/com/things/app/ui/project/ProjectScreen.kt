package com.things.app.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.components.CheckMark
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.MenuDots
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.TitleMenuButton
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private val ToggleGap = 22.dp
private val HeadingGap = 48.dp

/**
 * Static project page: ring and title with the project menu, notes, the to-dos that are not under
 * a heading, then each heading with its to-dos, then two toggles that reveal later items (future
 * start dates, repeating templates) and logged items. Later and logged items are hidden until
 * revealed. Active and later to-dos open in place when tapped. Every action is a callback.
 */
@Composable
fun ProjectScreen(
    state: ProjectUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTitleMenuClick: () -> Unit = {},
    onHeadingMenuClick: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
) {
    var showLater by rememberSaveable { mutableStateOf(false) }
    var showLogged by rememberSaveable { mutableStateOf(false) }
    val expansion = rememberTaskExpansion()

    ListPageScaffold(
        title = state.title,
        icon = {
            ProgressIcon(
                progress = state.progress,
                modifier = Modifier.size(28.dp),
                color = ThingsColors.ProjectBlue,
            )
        },
        modifier = modifier,
        titleTag = "project_title",
        titleTrailing = { TitleMenuButton("Project menu", onTitleMenuClick) },
        titleBottomPadding = 8.dp,
        onBack = onBack,
        onMoreClick = onMoreClick,
        expansion = expansion,
        onAddClick = onAddClick,
    ) {
        item(key = "notes") { NotesLine(state.notes) }

        items(state.tasks, key = { "task-${it.id}" }) { task ->
            ExpandableTaskRow(
                task = task,
                expansion = expansion,
                onOpen = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }

        state.headings.forEachIndexed { index, heading ->
            item(key = "heading-${heading.id}") {
                HeadingRow(
                    title = heading.title,
                    topGap = if (index == 0 && state.tasks.isEmpty()) 0.dp else HeadingGap,
                    onMenuClick = { onHeadingMenuClick(heading.id) },
                )
            }
            items(heading.tasks, key = { "task-${it.id}" }) { task ->
                ExpandableTaskRow(
                    task = task,
                    expansion = expansion,
                    onOpen = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
        }

        if (showLater) {
            items(state.laterTasks, key = { "later-${it.id}" }) { task ->
                ExpandableTaskRow(
                    task = task,
                    expansion = expansion,
                    onOpen = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
        }

        val hasLater = state.laterTasks.isNotEmpty()
        val hasLogged = state.loggedTasks.isNotEmpty()

        if (hasLater) {
            item(key = "later-toggle") {
                ToggleRow(
                    text = if (showLater) "Hide later items" else "Show ${state.laterTasks.size} later items",
                    topGap = ToggleGap,
                    onClick = { showLater = !showLater },
                )
            }
        }
        if (hasLogged) {
            item(key = "logged-toggle") {
                ToggleRow(
                    text = if (showLogged) "Hide logged items" else "Show ${state.loggedTasks.size} logged items",
                    topGap = if (hasLater) 0.dp else ToggleGap,
                    onClick = { showLogged = !showLogged },
                )
            }
        }
        if (hasLogged && showLogged) {
            item(key = "logged-gap") { Spacer(Modifier.height(ToggleGap)) }
            items(state.loggedTasks, key = { "logged-${it.id}" }) { task ->
                LoggedRow(task, onClick = { onTaskClick(task.id) })
            }
        }
    }
}

@Composable
private fun NotesLine(notes: String) {
    val isEmpty = notes.isBlank()
    Column(Modifier.padding(start = 18.dp, end = 18.dp)) {
        Text(
            text = if (isEmpty) "Notes" else notes,
            style = ThingsType.RowTitle.copy(
                color = if (isEmpty) ThingsColors.TextSecondary else ThingsColors.TextPrimary,
            ),
        )
        Spacer(Modifier.height(44.dp))
    }
}

/**
 * A heading: blue bold title, menu dots at the end of the line, and a thin divider under it.
 * [topGap] is the space above the heading.
 */
@Composable
private fun HeadingRow(title: String, topGap: Dp, onMenuClick: () -> Unit) {
    Column {
        Spacer(Modifier.height(topGap))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = ThingsType.RowTitleBold.copy(color = ThingsColors.ProjectBlue),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.semantics { contentDescription = "Heading menu: $title" },
            ) {
                MenuDots(ThingsColors.ProjectBlue)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 18.dp),
            thickness = 1.dp,
            color = ThingsColors.Divider,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ToggleRow(text: String, topGap: Dp, onClick: () -> Unit) {
    Column {
        Spacer(Modifier.height(topGap))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = ThingsType.Count)
        }
    }
}

/** A completed to-do: filled checkbox, blue date, grey title and grey markers. */
@Composable
private fun LoggedRow(task: LoggedTaskModel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 18.dp)
            .semantics(mergeDescendants = true) { stateDescription = "Completed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThingsColors.ProjectBlue),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            task.dateLabel,
            style = ThingsType.Count.copy(fontSize = 16.sp, color = ThingsColors.ProjectBlue),
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
        Text(
            task.title,
            style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (task.isRecurring) {
            Spacer(Modifier.width(8.dp))
            RepeatIcon(Modifier.size(16.dp))
        }
        if (task.hasChecklist) {
            Spacer(Modifier.width(8.dp))
            ChecklistIcon(Modifier.size(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Project")
@Composable
private fun ProjectPreview() {
    ThingsTheme { ProjectScreen(sampleProjectState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Project - headings")
@Composable
private fun ProjectWithHeadingsPreview() {
    ThingsTheme { ProjectScreen(sampleVacationProjectState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Project - empty")
@Composable
private fun EmptyProjectPreview() {
    ThingsTheme {
        ProjectScreen(ProjectUiState(id = "empty", title = "New Project", progress = 0f))
    }
}
