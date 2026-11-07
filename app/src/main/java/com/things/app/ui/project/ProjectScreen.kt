package com.things.app.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.Dialog
import com.things.app.ui.components.AddTarget
import com.things.app.ui.components.CheckMark
import com.things.app.ui.components.ChecklistIcon
import com.things.app.ui.components.DeadlineSheet
import com.things.app.ui.components.DropGapHeight
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.ExportHeading
import com.things.app.ui.components.ExportProject
import com.things.app.ui.components.HeadingMenu
import com.things.app.ui.components.HeadingMenuAction
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.LocalDropSlot
import com.things.app.ui.components.LocalSelection
import com.things.app.ui.components.MenuDots
import com.things.app.ui.components.MenuScrimHost
import com.things.app.ui.components.NotesEditor
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.components.ProjectEnabledMenuActions
import com.things.app.ui.components.ProjectMenuAction
import com.things.app.ui.components.ProjectMenuActions
import com.things.app.ui.components.ProjectTitleMenu
import com.things.app.ui.components.RepeatIcon
import com.things.app.ui.components.RepeatSheet
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TagsSheet
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.WhenSheet
import com.things.app.ui.components.exportProject
import com.things.app.ui.components.insertedAt
import com.things.app.ui.components.liveTasks
import com.things.app.ui.components.rememberReorderState
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.components.reorderable
import com.things.app.ui.components.shareMarkdown
import com.things.app.ui.components.toInfo
import com.things.app.ui.home.ProgressIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType
import java.util.UUID

private val ToggleGap = 24.dp
private val HeadingGap = 48.dp
private const val TaskPrefix = "task-"
private const val HeadingPrefix = "heading-"
private const val TopLevel = ""

/** The show/hide lines are 40dp high with a 15sp label, so two of them under each other sit close. */
private val ToggleHeight = 40.dp
private val ToggleFontSize = 15.sp

/** The red of destructive choices. */
private val DestructiveRed = Color(0xFFFF3B30)

/** Which sheet of the project's own When, tags, deadline or repeat is open. */
private enum class ProjectSheet { When, Tags, Deadline, Repeat }

// The Later items and the Logged items open and close with the very same motion: they unfold from
// the top edge downwards while fading in, quick and smooth, and fold back up the same way. Opening
// takes a little longer than closing.
private const val RevealOpenMillis = 320
private const val RevealCloseMillis = 260
private val RevealEnter =
    expandVertically(tween(RevealOpenMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
        fadeIn(tween(RevealOpenMillis))
private val RevealExit =
    shrinkVertically(tween(RevealCloseMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
        fadeOut(tween(RevealCloseMillis))

private fun <T> List<T>.move(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) {
        this
    } else {
        toMutableList().also { it.add(to, it.removeAt(from)) }
    }

/**
 * Project page: ring and title with the project menu, what the project holds itself (tags, When,
 * deadline, repeat), notes, the to-dos that are not under a heading, then each heading with its
 * to-dos, then two toggles that reveal later items (future start dates, repeating templates) and
 * logged items. Later and logged items are hidden until revealed; both unfold from the top and fold
 * back with the same animation. Active and later to-dos open in place when tapped. Every action is
 * a callback.
 *
 * The ring next to the title shows how much of the project is done: the completed to-dos (the
 * logged ones and the ones that are ticked) over all of them. Five of ten is half full; untick one
 * of five of ten and it drops to forty percent. The ring moves smoothly to its new value.
 * [onProgressChange] reports every change of it so Home can draw the same ring. Later items and
 * deleted to-dos do not count. A new ring value does not reset what was edited on the page: the
 * to-dos and headings of the page are only taken from [state] again when its own id, to-dos or
 * headings change.
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
 *
 * The menu next to the title (see [ProjectTitleMenu]) has Complete Project, When, Add Tags, Add
 * Deadline and Repeat; Move, Duplicate and Delete Project; Share. When, Add Tags, Add Deadline and
 * Repeat open the sheet of the to-do card; what is chosen is kept on the page and shown under the
 * title (see [ProjectDetailRows]). Delete Project calls [onDeleteProject] and the others, these
 * four included, report through [onProjectMenuAction]. The menu at the top right
 * has Filter by Tag, New Heading (a new empty heading at the end, named in place), Select and Paste,
 * then Share in a section of its own. Share, in both menus, sends the whole project as Markdown
 * through the shared exporter (exportProject): the title, what the project holds itself, the notes,
 * the to-dos with everything they hold, each heading with its to-dos, the later items and the
 * logged ones. Other entries of the page menu are reported through [onMenuAction]. Every dropdown
 * menu of the page (the top right menu, the title menu and the heading menus) looks the same and
 * dims the page while it is open. [modifier] is applied to the root of the page.
 *
 * Delete in a heading's menu asks first when the heading has to-dos: delete the heading and its
 * to-dos, or delete only the heading and keep its to-dos at the top of the page, outside every
 * heading. A heading with no to-dos is deleted at once.
 *
 * Convert to a Project in a heading's menu does not change this page itself: it reports the id of
 * the heading and the current state of the whole page (every edit applied) through
 * [onConvertHeadingToProject], and the owner of the data builds the new project and the page
 * without the heading in one step.
 */
@Composable
fun ProjectScreen(
    state: ProjectUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTitleMenuClick: () -> Unit = {},
    onHeadingMenuClick: (String) -> Unit = {},
    onConvertHeadingToProject: (headingId: String, source: ProjectUiState) -> Unit = { _, _ -> },
    onAddClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onProgressChange: (Float) -> Unit = {},
    onMenuAction: (PageMenuAction) -> Unit = {},
    onProjectMenuAction: (ProjectMenuAction) -> Unit = {},
    onDeleteProject: () -> Unit = {},
) {
    // The title menu and the heading menus dim the page through this host; the top right menu is
    // dimmed by the scaffold itself. The host is the root of the page, so it takes the caller's
    // modifier and the page inside gets none.
    MenuScrimHost(modifier.fillMaxSize()) {
        ProjectScreenBody(
            state = state,
            onBack = onBack,
            onMoreClick = onMoreClick,
            onTitleMenuClick = onTitleMenuClick,
            onHeadingMenuClick = onHeadingMenuClick,
            onConvertHeadingToProject = onConvertHeadingToProject,
            onAddClick = onAddClick,
            onTaskClick = onTaskClick,
            onToggleTask = onToggleTask,
            onProgressChange = onProgressChange,
            onMenuAction = onMenuAction,
            onProjectMenuAction = onProjectMenuAction,
            onDeleteProject = onDeleteProject,
        )
    }
}

@Composable
private fun ProjectScreenBody(
    state: ProjectUiState,
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    onTitleMenuClick: () -> Unit,
    onHeadingMenuClick: (String) -> Unit,
    onConvertHeadingToProject: (headingId: String, source: ProjectUiState) -> Unit,
    onAddClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onProgressChange: (Float) -> Unit,
    onMenuAction: (PageMenuAction) -> Unit,
    onProjectMenuAction: (ProjectMenuAction) -> Unit,
    onDeleteProject: () -> Unit,
) {
    var showLater by rememberSaveable { mutableStateOf(false) }
    var showLogged by rememberSaveable { mutableStateOf(false) }
    val expansion = rememberTaskExpansion()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var title by rememberSaveable(state.id) { mutableStateOf(state.title) }
    // Keyed by what the data owner can change (id, to-dos, headings) and not by the whole state: the
    // ring (progress) is reported by this page and comes back in the state, and that must not throw
    // away a heading that was deleted or a list that was reordered here.
    var tasks by remember(state.id, state.tasks, state.headings) { mutableStateOf(state.tasks) }
    var headings by remember(state.id, state.tasks, state.headings) { mutableStateOf(state.headings) }
    var editingHeadingId by remember { mutableStateOf<String?>(null) }
    var holdingHeading by remember { mutableStateOf<String?>(null) }
    var pendingDeleteHeadingId by remember { mutableStateOf<String?>(null) }
    var notes by rememberSaveable(state.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.notes))
    }

    // What the project holds itself, and the sheet that edits it.
    var projectTags by remember(state.id) { mutableStateOf(state.tags) }
    var projectSchedule by remember(state.id) { mutableStateOf(state.schedule) }
    var projectDeadline by remember(state.id) { mutableStateOf(state.deadline) }
    var projectRepeat by remember(state.id) { mutableStateOf(state.repeat) }
    var sheet by remember { mutableStateOf<ProjectSheet?>(null) }

    // How much of the project is done: logged to-dos count as done, ticked ones too.
    val open = (tasks + headings.flatMap { it.tasks }).filter { !expansion.isDeleted(it.id) }
    val total = open.size + state.loggedTasks.size
    val done = open.count { expansion.resolve(it).checked } + state.loggedTasks.size
    val progress = if (total == 0) 0f else done.toFloat() / total
    LaunchedEffect(progress) { onProgressChange(progress) }

    /**
     * The whole project as Markdown, written by the shared exporter: what the project holds itself,
     * every to-do with everything it holds (edits applied, deleted ones left out, copies after their
     * original), by heading, then the later items and the logged ones.
     */
    fun projectMarkdown(): String = exportProject(
        ExportProject(
            title = title,
            notes = notes.text,
            tasks = expansion.liveTasks(tasks),
            headings = headings.map { ExportHeading(it.title, expansion.liveTasks(it.tasks)) },
            later = expansion.liveTasks(state.laterTasks),
            logged = state.loggedTasks.map { it.toLoggedExport() },
            tags = projectTags,
            schedule = projectSchedule,
            deadline = projectDeadline,
            repeat = projectRepeat,
        ),
    )

    fun containerOf(key: Any): String? {
        val s = key as? String ?: return null
        if (!s.startsWith(TaskPrefix)) return null
        val id = s.removePrefix(TaskPrefix)
        if (tasks.any { it.id == id }) return TopLevel
        return headings.firstOrNull { h -> h.tasks.any { it.id == id } }?.id
    }

    /** The page as it is now: title, notes, own fields, order and every to-do with the edits made to it. */
    fun currentState(): ProjectUiState = state.copy(
        title = title,
        notes = notes.text,
        tasks = expansion.liveTasks(tasks, includeCopies = false),
        headings = headings.map { it.copy(tasks = expansion.liveTasks(it.tasks, includeCopies = false)) },
        laterTasks = expansion.liveTasks(state.laterTasks, includeCopies = false),
        tags = projectTags,
        schedule = projectSchedule,
        deadline = projectDeadline,
        repeat = projectRepeat,
    )

    fun duplicateHeading(headingId: String) {
        val index = headings.indexOfFirst { it.id == headingId }
        if (index < 0) return
        val original = headings[index]
        val duplicatedHeading = copyHeading(
            heading = original.copy(tasks = expansion.liveTasks(original.tasks)),
            newHeadingId = "heading-" + UUID.randomUUID(),
            newTaskId = { "task-" + UUID.randomUUID() },
        )
        headings = headings.toMutableList().also {
            it.add(index + 1, duplicatedHeading)
        }
    }

    fun deleteHeading(headingId: String) {
        val heading = headings.firstOrNull { it.id == headingId } ?: return
        heading.tasks.forEach { task ->
            expansion.delete(task.id)
        }
        headings = headings.filter { it.id != headingId }
    }

    /** Deletes only the heading: its to-dos go to the top of the page, outside every heading. */
    fun deleteHeadingKeepingTasks(headingId: String) {
        val removal = removeHeadingKeepingTasks(tasks, headings, headingId) ?: return
        tasks = removal.tasks
        headings = removal.headings
    }

    /** A heading with to-dos asks what to do with them; one without is deleted at once. */
    fun requestDeleteHeading(headingId: String) {
        val heading = headings.firstOrNull { it.id == headingId } ?: return
        if (expansion.liveTasks(heading.tasks).isEmpty()) {
            deleteHeading(headingId)
        } else {
            pendingDeleteHeadingId = headingId
        }
    }

    // Nothing is removed here: the owner builds the new project and the page without the heading in
    // one step, and this page is drawn again from that.
    fun convertHeadingToProject(headingId: String) {
        val heading = headings.firstOrNull { it.id == headingId } ?: return
        val snapshot = currentState()
        val moved = heading.copy(tasks = expansion.liveTasks(heading.tasks))
        onConvertHeadingToProject(
            headingId,
            snapshot.copy(headings = snapshot.headings.map { if (it.id == headingId) moved else it }),
        )
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

    ListPageScaffold(
        title = title,
        icon = {
            ProgressIcon(
                progress = progress,
                modifier = Modifier.size(28.dp),
                color = ThingsColors.ProjectBlue,
            )
        },
        titleTag = "project_title",
        titleTrailing = {
            ProjectTitleMenu(onOpen = onTitleMenuClick) { action ->
                when (action) {
                    ProjectMenuAction.DeleteProject -> onDeleteProject()
                    ProjectMenuAction.Share -> shareMarkdown(context, projectMarkdown())
                    else -> {
                        when (action) {
                            ProjectMenuAction.When -> sheet = ProjectSheet.When
                            ProjectMenuAction.AddTags -> sheet = ProjectSheet.Tags
                            ProjectMenuAction.AddDeadline -> sheet = ProjectSheet.Deadline
                            ProjectMenuAction.Repeat -> sheet = ProjectSheet.Repeat
                            else -> Unit
                        }
                        onProjectMenuAction(action)
                    }
                }
            }
        },
        titleBottomPadding = 8.dp,
        onBack = onBack,
        onMoreClick = onMoreClick,
        menuActions = ProjectMenuActions,
        enabledMenuActions = ProjectEnabledMenuActions,
        onMenuAction = { action ->
            when (action) {
                PageMenuAction.NewHeading -> addTask(AddTarget.Heading(null))
                PageMenuAction.Share -> shareMarkdown(context, projectMarkdown())
                else -> onMenuAction(action)
            }
        },
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
        // Display order: to-dos outside headings, each heading's to-dos, then the later items.
        shareableTasks = { tasks + headings.flatMap { it.tasks } + state.laterTasks },
    ) {
        item(key = "details") {
            ProjectDetailRows(
                tags = projectTags,
                schedule = projectSchedule,
                deadline = projectDeadline,
                repeat = projectRepeat,
                onTags = { sheet = ProjectSheet.Tags },
                onSchedule = { sheet = ProjectSheet.When },
                onDeadline = { sheet = ProjectSheet.Deadline },
            )
        }

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
                    onMenuAction = { action ->
                        when (action) {
                            HeadingMenuAction.Duplicate -> duplicateHeading(heading.id)
                            HeadingMenuAction.Delete -> requestDeleteHeading(heading.id)
                            HeadingMenuAction.ConvertToProject -> convertHeadingToProject(heading.id)
                            HeadingMenuAction.Archive, HeadingMenuAction.Move -> {
                                onHeadingMenuClick(heading.id)
                            }
                        }
                    },
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

        val hasLater = state.laterTasks.isNotEmpty()
        val hasLogged = state.loggedTasks.isNotEmpty()

        if (hasLater) {
            item(key = "later-group") {
                AnimatedVisibility(visible = showLater, enter = RevealEnter, exit = RevealExit) {
                    Column {
                        state.laterTasks.forEach { task ->
                            key(task.id) {
                                ExpandableTaskRow(
                                    task = task,
                                    expansion = expansion,
                                    onOpen = { onTaskClick(task.id) },
                                    onToggle = { onToggleTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }
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
            item(key = "logged-group") {
                AnimatedVisibility(visible = showLogged, enter = RevealEnter, exit = RevealExit) {
                    Column {
                        Spacer(Modifier.height(ToggleGap))
                        state.loggedTasks.forEach { task ->
                            key(task.id) { LoggedRow(task, onClick = { onTaskClick(task.id) }) }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteHeadingId?.let { id ->
        val headingTitle = headings.firstOrNull { it.id == id }?.title.orEmpty()
        DeleteHeadingDialog(
            headingTitle = headingTitle,
            onDeleteAll = {
                pendingDeleteHeadingId = null
                deleteHeading(id)
            },
            onKeepTasks = {
                pendingDeleteHeadingId = null
                deleteHeadingKeepingTasks(id)
            },
            onCancel = { pendingDeleteHeadingId = null },
        )
    }

    // The sheets of the project's own When, tags, deadline and repeat: the ones of the to-do card.
    when (sheet) {
        ProjectSheet.When -> WhenSheet(
            current = projectSchedule,
            onDismiss = { sheet = null },
            onPick = { projectSchedule = it },
        )
        ProjectSheet.Deadline -> DeadlineSheet(
            current = projectDeadline,
            onDismiss = { sheet = null },
            onPick = { projectDeadline = it },
        )
        ProjectSheet.Tags -> TagsSheet(
            selected = projectTags,
            onDismiss = { sheet = null },
            onDone = { projectTags = it },
        )
        ProjectSheet.Repeat -> RepeatSheet(
            onDismiss = { sheet = null },
            onPick = { rule ->
                projectRepeat = rule.toInfo()
                projectSchedule = ScheduleModel(ScheduleKind.Date, rule.next, projectSchedule?.reminder)
            },
        )
        null -> Unit
    }
}

/**
 * Asks what to do with the to-dos of a heading that is being deleted: delete them too, or keep them
 * at the top of the page outside every heading. Cancel (or a touch outside) changes nothing.
 */
@Composable
private fun DeleteHeadingDialog(
    headingTitle: String,
    onDeleteAll: () -> Unit,
    onKeepTasks: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ThingsColors.Background)
                .padding(vertical = 8.dp),
        ) {
            Text(
                "Delete heading?",
                style = ThingsType.RowTitleBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                if (headingTitle.isBlank()) {
                    "This heading has to-dos. Delete them too, or keep them at the top of the page without a heading?"
                } else {
                    "\"$headingTitle\" has to-dos. Delete them too, or keep them at the top of the page without a heading?"
                },
                style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = ThingsColors.Divider,
            )
            DialogOption("Delete Heading and Tasks", DestructiveRed, onDeleteAll)
            DialogOption("Delete Heading Only", ThingsColors.TextPrimary, onKeepTasks)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = ThingsColors.Divider,
            )
            DialogOption("Cancel", ThingsColors.TextSecondary, onCancel)
        }
    }
}

@Composable
private fun DialogOption(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = ThingsType.RowTitle.copy(color = color))
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
    onMenuAction: (HeadingMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleStyle = ThingsType.RowTitleBold.copy(color = ThingsColors.ProjectBlue)
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
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
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "Heading menu: $title" },
                ) {
                    MenuDots(ThingsColors.ProjectBlue)
                }
                HeadingMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onAction = { action ->
                        menuOpen = false
                        onMenuAction(action)
                    },
                )
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
                .height(ToggleHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ThingsSpacing.PageMargin),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = ThingsType.Count.copy(fontSize = ToggleFontSize))
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
