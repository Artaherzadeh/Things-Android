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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.components.AddTarget
import com.things.app.ui.components.CheckMark
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.DropGapHeight
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.LocalDropSlot
import com.things.app.ui.components.LocalSelection
import com.things.app.ui.components.MenuDots
import com.things.app.ui.components.NotesEditor
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.TitleMenuButton
import com.things.app.ui.components.insertedAt
import com.things.app.ui.components.rememberReorderState
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.components.reorderable
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType
import androidx.compose.ui.text.style.TextOverflow as Overflow
import java.util.UUID

private val ToggleGap = 24.dp
private val HeadingGap = 48.dp
private const val TaskPrefix = "task-"
private const val HeadingPrefix = "heading-"
private const val TopLevel = ""

private fun <T> List<T>.move(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) {
        this
    } else {
        toMutableList().also { it.add(to, it.removeAt(from)) }
    }

/**
 * Project page: ring and title with the project menu, notes, the to-dos that are not under a
 * heading, then each heading with its to-dos, then two toggles that reveal later items (future
 * start dates, repeating templates) and logged items. Later and logged items are hidden until
 * revealed. Active and later to-dos open in place when tapped. Every action is a callback.
 *
 * The project title, the heading titles and the notes under the title can be edited in place; with
 * no notes the line shows a grey "Notes". Select mode and swipe-to-delete come from the shared
 * scaffold and rows.
 *
 * Hold a to-do and move it to change the order inside its heading (or among the to-dos that are in
 * no heading). Hold a heading: its to-dos fold away first and it moves as one row among the other
 * headings.
 *
 * Add button: a tap adds a to-do at the top, outside every heading; drag it over the list and the
 * rows make room where the to-do will land (in a heading's area it goes into that heading); drag it
 * to the left edge and it turns into a heading, with the room opening where the heading will go.
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
    val listState = rememberLazyListState()

    var title by rememberSaveable(state.id) { mutableStateOf(state.title) }
    var tasks by remember(state) { mutableStateOf(state.tasks) }
    var headings by remember(state) { mutableStateOf(state.headings) }
    var editingHeadingId by remember { mutableStateOf<String?>(null) }
    var holdingHeading by remember { mutableStateOf<String?>(null) }
    var notes by rememberSaveable(state.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.notes))
    }

    fun containerOf(key: Any): String? {
        val s = key as? String ?: return null
        if (!s.startsWith(TaskPrefix)) return null
        val id = s.removePrefix(TaskPrefix)
        if (tasks.any { it.id == id }) return TopLevel
        return headings.firstOrNull { h -> h.tasks.any { it.id == id } }?.id
    }

    fun insertHeading(atKey: Any?) {
        val id = "new-" + UUID.randomUUID()
        val key = atKey as? String
        val taskIndex = if (key != null && key.startsWith(TaskPrefix)) {
            tasks.indexOfFirst { "$TaskPrefix${it.id}" == key }
        } else {
            -1
        }
        when {
            taskIndex >= 0 -> {
                headings = listOf(ProjectHeadingModel(id, "", tasks.drop(taskIndex))) + headings
                tasks = tasks.take(taskIndex)
            }
            key != null && key.startsWith(HeadingPrefix) -> {
                val at = headings.indexOfFirst { "$HeadingPrefix${it.id}" == key }.coerceAtLeast(0)
                headings = headings.toMutableList().also { it.add(at, ProjectHeadingModel(id, "", emptyList())) }
            }
            key != null && key.startsWith(TaskPrefix) -> {
                val hi = headings.indexOfFirst { h -> h.tasks.any { "$TaskPrefix${it.id}" == key } }
                if (hi >= 0) {
                    val h = headings[hi]
                    val ti = h.tasks.indexOfFirst { "$TaskPrefix${it.id}" == key }
                    headings = headings.toMutableList().also {
                        it[hi] = h.copy(tasks = h.tasks.take(ti))
                        it.add(hi + 1, ProjectHeadingModel(id, "", h.tasks.drop(ti)))
                    }
                } else {
                    headings = headings + ProjectHeadingModel(id, "", emptyList())
                }
            }
            else -> headings = headings + ProjectHeadingModel(id, "", emptyList())
        }
        editingHeadingId = id
    }

    fun addTask(target: AddTarget) {
        val task = TaskRowModel(id = "new-" + UUID.randomUUID(), title = "")
        when (target) {
            is AddTarget.Top -> tasks = listOf(task) + tasks
            is AddTarget.At -> {
                val groupId = target.groupId
                if (groupId == null) {
                    tasks = tasks.insertedAt(task, target.anchorTaskId, target.after)
                } else {
                    headings = headings.map { h ->
                        if (h.id == groupId) h.copy(tasks = h.tasks.insertedAt(task, target.anchorTaskId, target.after)) else h
                    }
                }
            }
            is AddTarget.Heading -> {
                insertHeading(target.atKey)
                return
            }
        }
        expansion.expandNew(task.id)
    }

    val reorder = rememberReorderState(listState)
    reorder.canMove = { from, to ->
        val f = from as? String
        val t = to as? String
        when {
            f == null || t == null -> false
            f.startsWith(HeadingPrefix) -> t.startsWith(HeadingPrefix)
            f.startsWith(TaskPrefix) -> t.startsWith(TaskPrefix) &&
                containerOf(f) != null && containerOf(f) == containerOf(t)
            else -> false
        }
    }
    reorder.onStart = { key ->
        val s = key as? String
        if (s != null && s.startsWith(HeadingPrefix)) {
            holdingHeading = s.removePrefix(HeadingPrefix)
        } else {
            expansion.collapse()
        }
    }
    reorder.onEnd = { holdingHeading = null }
    reorder.onMove = { from, to ->
        val f = from as String
        val t = to as String
        if (f.startsWith(HeadingPrefix)) {
            val fromId = f.removePrefix(HeadingPrefix)
            val toId = t.removePrefix(HeadingPrefix)
            headings = headings.move(
                headings.indexOfFirst { it.id == fromId },
                headings.indexOfFirst { it.id == toId },
            )
        } else {
            val fromId = f.removePrefix(TaskPrefix)
            val toId = t.removePrefix(TaskPrefix)
            val container = containerOf(f)
            if (container == TopLevel) {
                tasks = tasks.move(
                    tasks.indexOfFirst { it.id == fromId },
                    tasks.indexOfFirst { it.id == toId },
                )
            } else if (container != null) {
                headings = headings.map { h ->
                    if (h.id == container) {
                        h.copy(
                            tasks = h.tasks.move(
                                h.tasks.indexOfFirst { it.id == fromId },
                                h.tasks.indexOfFirst { it.id == toId },
                            )
                        )
                    } else {
                        h
                    }
                }
            }
        }
    }
    val selecting = LocalSelection.current?.active == true

    ListPageScaffold(
        title = title,
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
                s.startsWith(HeadingPrefix) -> s.removePrefix(HeadingPrefix)
                s.startsWith(TaskPrefix) -> headings.firstOrNull { h -> h.tasks.any { "$TaskPrefix${it.id}" == s } }?.id
                else -> null
            }
        },
        taskIdForKey = { key ->
            (key as? String)?.takeIf { it.startsWith(TaskPrefix) }?.removePrefix(TaskPrefix)
        },
        onTitleChange = { title = it },
    ) {
        item(key = "notes") {
            Column(Modifier.padding(horizontal = ThingsSpacing.PageMargin)) {
                NotesEditor(value = notes, onValueChange = { notes = it }, placeholder = "Notes")
                Spacer(Modifier.height(44.dp))
            }
        }

        items(tasks, key = { "$TaskPrefix${it.id}" }) { task ->
            val active = LocalSelection.current?.active == true
            ExpandableTaskRow(
                task = task,
                expansion = expansion,
                modifier = if (active) Modifier else Modifier.reorderable(reorder, "$TaskPrefix${task.id}"),
                onOpen = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }

        headings.forEachIndexed { index, heading ->
            item(key = "$HeadingPrefix${heading.id}") {
                val active = LocalSelection.current?.active == true
                HeadingRow(
                    id = heading.id,
                    title = heading.title,
                    topGap = if (index == 0 && tasks.isEmpty()) 0.dp else HeadingGap,
                    autoFocus = editingHeadingId == heading.id,
                    onTitleChange = { text ->
                        headings = headings.map { if (it.id == heading.id) it.copy(title = text) else it }
                    },
                    onEditDone = {
                        if (editingHeadingId == heading.id) editingHeadingId = null
                        if (heading.title.isBlank()) {
                            headings = headings.map {
                                if (it.id == heading.id) it.copy(title = "New Heading") else it
                            }
                        }
                    },
                    onMenuClick = { onHeadingMenuClick(heading.id) },
                    modifier = if (active || editingHeadingId == heading.id) {
                        Modifier
                    } else {
                        Modifier.reorderable(reorder, "$HeadingPrefix${heading.id}")
                    },
                )
            }
            if (heading.id != holdingHeading) {
                items(heading.tasks, key = { "$TaskPrefix${it.id}" }) { task ->
                    val active = LocalSelection.current?.active == true
                    ExpandableTaskRow(
                        task = task,
                        expansion = expansion,
                        modifier = if (active) Modifier else Modifier.reorderable(reorder, "$TaskPrefix${task.id}"),
                        onOpen = { onTaskClick(task.id) },
                        onToggle = { onToggleTask(task.id) },
                    )
                }
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

/**
 * A heading: blue bold title, menu dots at the end of the line, and a thin divider under it.
 * [topGap] is the space above the heading. The dots end 20dp from the screen edge.
 * The title is a text field: tap it to edit. With [autoFocus] it takes the keyboard as soon as it
 * appears (a heading that was just added). [onEditDone] is called when the cursor leaves the title.
 * While the add button is dragged over the top of this heading a gap opens under it; while it is
 * dragged near the left edge over this heading, a gap opens above it.
 */
@Composable
private fun HeadingRow(
    id: String,
    title: String,
    topGap: Dp,
    autoFocus: Boolean,
    onTitleChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleStyle = ThingsType.RowTitleBold.copy(color = ThingsColors.ProjectBlue)
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val slot = LocalDropSlot.current
    val gapBelow = slot.afterGroupId == id
    val gapAbove = slot.beforeGroupId == id

    LaunchedEffect(autoFocus) {
        if (autoFocus) requester.requestFocus()
    }

    Column(modifier) {
        if (gapAbove) Spacer(Modifier.height(DropGapHeight))
        Spacer(Modifier.height(topGap))
        Row(
            Modifier
                .fillMaxWidth()
                .height(ThingsSpacing.RowHeight)
                .padding(start = ThingsSpacing.PageMargin, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = titleStyle,
                cursorBrush = SolidColor(ThingsColors.ProjectBlue),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text("Heading", style = titleStyle.copy(color = ThingsColors.TextSecondary))
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(requester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            hadFocus = false
                            onEditDone()
                        }
                    }
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
            modifier = Modifier.padding(horizontal = ThingsSpacing.PageMargin),
            thickness = 1.dp,
            color = ThingsColors.Divider,
        )
        Spacer(Modifier.height(8.dp))
        if (gapBelow) Spacer(Modifier.height(DropGapHeight))
    }
}

@Composable
private fun ToggleRow(text: String, topGap: Dp, onClick: () -> Unit) {
    Column {
        Spacer(Modifier.height(topGap))
        Box(
            Modifier
                .fillMaxWidth()
                .height(ThingsSpacing.RowHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ThingsSpacing.PageMargin),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = ThingsType.Count)
        }
    }
}

/** A completed to-do: filled checkbox at x = 20dp, blue date, grey title and grey markers. */
@Composable
private fun LoggedRow(task: LoggedTaskModel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = ThingsSpacing.PageMargin)
            .semantics(mergeDescendants = true) { stateDescription = "Completed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThingsColors.ProjectBlue),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            task.dateLabel,
            style = ThingsType.Count.copy(fontSize = 16.sp, color = ThingsColors.ProjectBlue),
            maxLines = 1,
            modifier = Modifier.width(48.dp),
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
