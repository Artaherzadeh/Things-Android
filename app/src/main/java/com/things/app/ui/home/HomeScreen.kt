package com.things.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import com.things.app.ui.components.SwipeToDeleteRow
import com.things.app.ui.components.rememberReorderState
import com.things.app.ui.components.reorderable
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType
import kotlin.math.roundToInt

// Every value is a multiple of 4dp (see ThingsSpacing). Icons and checkboxes sit at x = 20dp and
// the text after them starts at x = 52dp, like the to-dos on the list pages.
// Home rows are 48dp tall, 4dp taller than the list rows, like the original design.
private val ScreenPadding = ThingsSpacing.PageMargin
private val RowHeight = 48.dp
private val IconSlot = 24.dp
private val IconTextGap = 8.dp
private val GroupGap = 12.dp

private val QuickFindHeight = 40.dp

/**
 * Space between the status bar and the search box. The Quick Find panel puts its search field
 * 32dp under the status bar (16dp of margin and 16dp of padding), so the box here starts at the same
 * height: when the panel opens, its field comes up exactly where this box was.
 */
private val ListTopPadding = 32.dp

/** How far the list has to be pulled down from the top before Quick Find opens. */
private val PullToSearchDistance = 192.dp

/** The grey of the cross that stays where the + was, and the red when it cancels. */
private val DragGray = Color(0xFF8E8E93)
private val CancelRed = Color(0xFFFF3B30)

private const val ProjectPrefix = "project-"
private const val AreaPrefix = "area-"
private const val SettingsKey = "settings"

/**
 * The home screen. It lists no to-dos: only the system lists, projects and areas. Settings is the
 * last row of the list, so it scrolls with the rest instead of staying on screen.
 *
 * - Pulling the list down while it is at the top opens Quick Find, like tapping the search field.
 *   While Quick Find is open ([searchHidden]) the search box is hidden, as the panel has its own.
 * - The + button opens the create menu when tapped. Dragging it creates a new project where it is
 *   let go: next to the project or on the area under the finger, anywhere else it becomes the first
 *   loose project. [onCreateProjectAt] gets the area id (null for no area) and the position. A
 *   small card, only as wide as its text, follows the finger over the whole screen. While it is
 *   dragged the button's place shows a grey cross at three quarters of its size; bring the finger
 *   back onto it and the cross grows to full size and turns red: letting go there cancels and
 *   nothing is created.
 * - A project or area that was just created is named in place: the row with [editingId] is a text
 *   field with the keyboard open. [onTitleChange] reports each edit and [onEditDone] tells the page
 *   the cursor left the row, so it can drop the row if it is still empty.
 * - Hold a project or an area and move it to change the order. A held area folds first, with all
 *   its projects, and moves as one row. A project can move inside its area, to another area or out
 *   of any area. [onMoveProject] and [onMoveArea] get the held row and the row it passed.
 * - Swipe a project or an area to the left for a red cross that deletes it ([onDeleteProject],
 *   [onDeleteArea]; an area goes with its projects).
 * - A long name goes on to the next lines; the icon stays level with the first line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState = sampleHomeState(),
    onSearchClick: () -> Unit = {},
    onSystemListClick: (SystemList) -> Unit = {},
    onAreaClick: (Area) -> Unit = {},
    onProjectClick: (Project) -> Unit = {},
    onNewToDo: () -> Unit = {},
    onNewProject: () -> Unit = {},
    onNewArea: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCreateProjectAt: (areaId: String?, index: Int) -> Unit = { _, _ -> },
    editingId: String? = null,
    onTitleChange: (id: String, title: String) -> Unit = { _, _ -> },
    onEditDone: (id: String) -> Unit = {},
    onMoveProject: (fromId: String, toId: String) -> Unit = { _, _ -> },
    onMoveArea: (fromId: String, toId: String) -> Unit = { _, _ -> },
    onDeleteProject: (id: String) -> Unit = {},
    onDeleteArea: (id: String) -> Unit = {},
    searchHidden: Boolean = false,
) {
    var collapsedAreas by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var holdingArea by remember { mutableStateOf<String?>(null) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    val currentOnSearch by rememberUpdatedState(onSearchClick)
    val currentOnCreateProjectAt by rememberUpdatedState(onCreateProjectAt)
    val pullPx = with(density) { PullToSearchDistance.toPx() }
    val pullToSearch = remember(pullPx) {
        object : NestedScrollConnection {
            private var pulled = 0f
            private var fired = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !listState.canScrollBackward) {
                    pulled += available.y
                    if (!fired && pulled >= pullPx) {
                        fired = true
                        currentOnSearch()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                fired = false
                return Velocity.Zero
            }
        }
    }

    // The areas that show no projects: the ones the person folded, and the held one.
    val hiddenAreas = holdingArea?.let { collapsedAreas + it } ?: collapsedAreas

    // Closing the keyboard ends the naming: the cursor leaves the row and the page is told.
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }

    // Show the row that is being named.
    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        val key = if (state.areas.any { it.id == id }) "$AreaPrefix$id" else "$ProjectPrefix$id"
        val keys = buildList {
            add("quick-find")
            add("quick-find-gap")
            add("inbox")
            add("lists")
            add("logbook")
            state.looseProjects.forEach { add("$ProjectPrefix${it.id}") }
            state.areas.forEach { area ->
                add("$AreaPrefix${area.id}")
                if (area.id !in collapsedAreas) area.projects.forEach { add("$ProjectPrefix${it.id}") }
            }
            add(SettingsKey)
        }
        val index = keys.indexOf(key)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // Add menu: one boolean drives a single animated progress value, so rapid taps can't stack overlays.
    var menuExpanded by remember { mutableStateOf(false) }
    val menuProgress = animateFloatAsState(
        targetValue = if (menuExpanded) 1f else 0f,
        animationSpec = addMenuSpec(opening = menuExpanded),
        label = "addMenuProgress",
    )
    // True from the moment the menu opens until its close animation has finished.
    val menuOnScreen by remember { derivedStateOf { menuExpanded || menuProgress.value > 0f } }
    var fabBounds by remember { mutableStateOf<Rect?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listTop by remember { mutableFloatStateOf(0f) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var leftFab by remember { mutableStateOf(false) }
    val fabRadiusPx = with(density) { ThingsSpacing.FabSize.toPx() / 2f }

    // Cancelling needs the finger to have left the button first, then come back onto it.
    val cancelling = dragPos?.let { p ->
        val center = fabBounds?.center
        leftFab && center != null && (p - center).getDistance() <= fabRadiusPx
    } == true

    fun selectCreation(type: CreationType) {
        if (!menuExpanded) return // ignore repeat taps while closing
        menuExpanded = false
        when (type) {
            CreationType.ToDo -> onNewToDo()
            CreationType.Project -> onNewProject()
            CreationType.Area -> onNewArea()
        }
    }

    /** Where a project dropped at this window position would go: the area (or null) and the index. */
    fun dropTarget(p: Offset): Pair<String?, Int> {
        val y = p.y - listTop
        val items = listState.layoutInfo.visibleItemsInfo
        val info = items.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: items.lastOrNull()
        val key = info?.key as? String
        if (info == null || key == null) return null to 0
        val after = y >= info.offset + info.size / 2f
        if (key.startsWith(ProjectPrefix)) {
            val id = key.removePrefix(ProjectPrefix)
            val loose = state.looseProjects.indexOfFirst { it.id == id }
            if (loose >= 0) return null to (loose + if (after) 1 else 0)
            val area = state.areas.firstOrNull { a -> a.projects.any { it.id == id } }
            if (area != null) return area.id to (area.projects.indexOfFirst { it.id == id } + if (after) 1 else 0)
        }
        if (key.startsWith(AreaPrefix)) {
            val area = state.areas.firstOrNull { it.id == key.removePrefix(AreaPrefix) }
            if (area != null) return area.id to if (area.id in collapsedAreas) area.projects.size else 0
        }
        return null to if (key == SettingsKey) state.looseProjects.size else 0
    }

    // Hold-and-move ordering of projects and areas.
    val reorder = rememberReorderState(listState)
    reorder.canMove = { from, to ->
        val f = from as? String
        val t = to as? String
        when {
            f == null || t == null -> false
            f.startsWith(AreaPrefix) -> t.startsWith(AreaPrefix)
            f.startsWith(ProjectPrefix) -> t.startsWith(ProjectPrefix)
            else -> false
        }
    }
    reorder.onStart = { key ->
        val s = key as? String
        if (s != null && s.startsWith(AreaPrefix)) holdingArea = s.removePrefix(AreaPrefix)
    }
    reorder.onEnd = { holdingArea = null }
    reorder.onMove = { from, to ->
        val f = from as String
        val t = to as String
        if (f.startsWith(AreaPrefix)) {
            onMoveArea(f.removePrefix(AreaPrefix), t.removePrefix(AreaPrefix))
        } else {
            onMoveProject(f.removePrefix(ProjectPrefix), t.removePrefix(ProjectPrefix))
        }
    }

    fun rowModifier(key: String, id: String): Modifier =
        if (editingId == id) Modifier else Modifier.reorderable(reorder, key)

    BackHandler(enabled = menuExpanded) { menuExpanded = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(ThingsColors.Background)
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { listTop = it.positionInWindow().y }
                .nestedScroll(pullToSearch)
                .imePadding(),
            contentPadding = PaddingValues(top = statusBarTop + ListTopPadding, bottom = 140.dp),
        ) {
            item(key = "quick-find") { QuickFind(hidden = searchHidden, onClick = onSearchClick) }

            item(key = "quick-find-gap") { Spacer(Modifier.height(28.dp)) }

            // Inbox
            item(key = "inbox") {
                SystemRow(SystemList.Inbox, count = state.inboxCount) { onSystemListClick(SystemList.Inbox) }
                Spacer(Modifier.height(GroupGap))
            }

            // Today / Upcoming / Anytime / Someday
            item(key = "lists") {
                SystemRow(SystemList.Today, count = state.todayCount, badge = state.todayBadge) {
                    onSystemListClick(SystemList.Today)
                }
                SystemRow(SystemList.Upcoming) { onSystemListClick(SystemList.Upcoming) }
                SystemRow(SystemList.Anytime) { onSystemListClick(SystemList.Anytime) }
                SystemRow(SystemList.Someday) { onSystemListClick(SystemList.Someday) }
                Spacer(Modifier.height(GroupGap))
            }

            // Logbook
            item(key = "logbook") {
                SystemRow(SystemList.Logbook) { onSystemListClick(SystemList.Logbook) }
                Spacer(Modifier.height(16.dp))
                SectionDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Projects without an area, one row each so each can be moved
            items(state.looseProjects, key = { "$ProjectPrefix${it.id}" }) { project ->
                SwipeToDeleteRow(
                    onDelete = { onDeleteProject(project.id) },
                    modifier = rowModifier("$ProjectPrefix${project.id}", project.id),
                ) {
                    ProjectRow(
                        project = project,
                        editing = editingId == project.id,
                        onTitleChange = { onTitleChange(project.id, it) },
                        onEditDone = { onEditDone(project.id) },
                        onClick = { onProjectClick(project) },
                    )
                }
            }

            // Areas and their projects
            state.areas.forEachIndexed { index, area ->
                item(key = "$AreaPrefix${area.id}") {
                    val expanded = area.id !in hiddenAreas
                    Column(rowModifier("$AreaPrefix${area.id}", area.id)) {
                        if (index > 0 || state.looseProjects.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            SectionDivider()
                            Spacer(Modifier.height(12.dp))
                        }
                        SwipeToDeleteRow(onDelete = { onDeleteArea(area.id) }) {
                            AreaRow(
                                area = area,
                                expanded = expanded,
                                editing = editingId == area.id,
                                onTitleChange = { onTitleChange(area.id, it) },
                                onEditDone = { onEditDone(area.id) },
                                onClick = { onAreaClick(area) },
                                onToggle = {
                                    collapsedAreas = if (area.id in collapsedAreas) {
                                        collapsedAreas - area.id
                                    } else {
                                        collapsedAreas + area.id
                                    }
                                },
                            )
                        }
                    }
                }
                if (area.id !in hiddenAreas) {
                    items(area.projects, key = { "$ProjectPrefix${it.id}" }) { project ->
                        SwipeToDeleteRow(
                            onDelete = { onDeleteProject(project.id) },
                            modifier = rowModifier("$ProjectPrefix${project.id}", project.id),
                        ) {
                            ProjectRow(
                                project = project,
                                editing = editingId == project.id,
                                onTitleChange = { onTitleChange(project.id, it) },
                                onEditDone = { onEditDone(project.id) },
                                onClick = { onProjectClick(project) },
                            )
                        }
                    }
                }
            }

            // Settings: the last row of the list. Scroll to the end to see it.
            item(key = SettingsKey) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onSettingsClick)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = ThingsColors.TextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Settings", style = ThingsType.Settings)
                    }
                }
            }
        }

        // Floating add button, 20dp from the right edge and 20dp above the bottom edge.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ThingsSpacing.FabMargin)
                .height(ThingsSpacing.FabSize)
        ) {
            AddButton(
                onClick = { menuExpanded = true },
                hidden = menuOnScreen,
                dragging = dragPos != null,
                cancelling = cancelling,
                onBoundsChanged = { fabBounds = it },
                onDragStart = {
                    leftFab = false
                    dragPos = fabBounds?.center
                },
                onDrag = { delta ->
                    val next = dragPos?.plus(delta)
                    dragPos = next
                    val center = fabBounds?.center
                    if (next != null && center != null && (next - center).getDistance() > fabRadiusPx * 1.3f) {
                        leftFab = true
                    }
                },
                onDragEnd = {
                    val p = dragPos
                    val wasCancelling = cancelling
                    dragPos = null
                    leftFab = false
                    if (p != null && !wasCancelling) {
                        val (areaId, index) = dropTarget(p)
                        currentOnCreateProjectAt(areaId, index)
                    }
                },
                onDragCancel = {
                    dragPos = null
                    leftFab = false
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = ThingsSpacing.FabMargin),
            )
        }

        // The new project under the finger while the + is dragged (not while it would cancel). It is
        // only as wide as its text, centered on the finger and kept on the screen.
        val p = dragPos
        if (p != null && !cancelling) {
            val local = p - rootOrigin
            val top = (local.y - with(density) { RowHeight.toPx() } / 2f).roundToInt()
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val x = (local.x - placeable.width / 2f).roundToInt()
                                .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                            placeable.place(x, top)
                        }
                    }
            ) {
                Row(
                    Modifier
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color.Black.copy(alpha = 0.7f),
                            spotColor = Color.Black.copy(alpha = 0.45f),
                        )
                        .background(ThingsColors.Background, RoundedCornerShape(20.dp))
                        .height(RowHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
                        ProgressIcon(0f, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(IconTextGap))
                    Text("New Project", style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
                }
            }
        }

        // Scrim + morphing menu, drawn above everything else while open or animating.
        val anchor = fabBounds?.translate(-rootOrigin)
        if (menuOnScreen && anchor != null) {
            AddMenuOverlay(
                progress = menuProgress,
                expanded = menuExpanded,
                anchor = anchor,
                onDismiss = { menuExpanded = false },
                onSelect = { selectCreation(it) },
            )
        }
    }
}

/** The search box. While [hidden] it keeps its room but cannot be seen or tapped. */
@Composable
private fun QuickFind(hidden: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .height(QuickFindHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(ThingsColors.SearchBackground)
            .clickable(enabled = !hidden, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = ThingsColors.SearchContent,
                modifier = Modifier.size(24.dp),
            )
            Text("Quick Find", style = ThingsType.Search)
        }
    }
}

/** The space above and below a title line so that one line of it sits in the middle of a 48dp row. */
private fun titlePadding(lineHeight: Dp): Dp = ((RowHeight - lineHeight) / 2).coerceAtLeast(0.dp)

/**
 * One Home row, at least 48dp high. A long title goes on to the next lines; the icon and whatever is
 * at the end (the chevron, the count) stay in a 48dp box at the top, level with the first line.
 * [titleSlot] replaces the title text, for example with a text field while a new project or area is
 * being named; it has to keep the first line in the middle of the 48dp itself.
 */
@Composable
private fun HomeRow(
    title: String,
    titleStyle: TextStyle,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleSlot: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    var lineHeight by remember { mutableStateOf(24.dp) }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = IconSlot, height = RowHeight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.width(IconTextGap))
        if (titleSlot != null) {
            titleSlot()
        } else {
            Text(
                title,
                style = titleStyle,
                onTextLayout = { layout ->
                    if (layout.lineCount > 0) {
                        lineHeight = with(density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp() }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = titlePadding(lineHeight)),
            )
        }
        Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/**
 * A title that is typed in place: the keyboard opens at once and the capital letter comes first. A
 * long name goes on to the next lines; Enter does not add a line, it ends the naming.
 */
@Composable
private fun EditableTitle(
    title: String,
    style: TextStyle,
    placeholder: String,
    onTitleChange: (String) -> Unit,
    onEditDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var hadFocus by remember { mutableStateOf(false) }
    var lineHeight by remember { mutableStateOf(24.dp) }

    LaunchedEffect(Unit) { requester.requestFocus() }

    BasicTextField(
        value = title,
        onValueChange = { new ->
            if (new.contains('\n')) {
                onTitleChange(new.replace("\n", ""))
                focusManager.clearFocus()
            } else {
                onTitleChange(new)
            }
        },
        textStyle = style,
        cursorBrush = SolidColor(ThingsColors.Fab),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onTextLayout = { layout ->
            if (layout.lineCount > 0) {
                lineHeight = with(density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp() }
            }
        },
        decorationBox = { inner ->
            Box {
                if (title.isEmpty()) Text(placeholder, style = style.copy(color = ThingsColors.TextSecondary))
                inner()
            }
        },
        modifier = modifier
            .padding(vertical = titlePadding(lineHeight))
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onEditDone()
                }
            },
    )
}

@Composable
private fun SystemRow(
    list: SystemList,
    count: Int = 0,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    HomeRow(
        title = list.title,
        titleStyle = ThingsType.RowTitleBold,
        onClick = onClick,
        icon = {
            when (list) {
                SystemList.Inbox -> InboxIcon(Modifier.size(24.dp))
                SystemList.Today -> StarIcon(Modifier.size(24.dp))
                SystemList.Upcoming -> CalendarIcon(Modifier.size(24.dp))
                SystemList.Anytime -> LayersIcon(Modifier.size(24.dp))
                SystemList.Someday -> ArchiveIcon(Modifier.size(24.dp))
                SystemList.Logbook -> LogbookIcon(Modifier.size(24.dp))
            }
        },
        trailing = {
            if (badge > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ThingsColors.Badge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.toString(), style = ThingsType.Badge)
                }
                Spacer(Modifier.width(8.dp))
            }
            if (count > 0) {
                Text(count.toString(), style = ThingsType.Count)
            }
        },
    )
}

@Composable
private fun AreaRow(
    area: Area,
    expanded: Boolean,
    editing: Boolean,
    onTitleChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "areaChevron")
    HomeRow(
        title = area.title,
        titleStyle = ThingsType.RowTitleBold,
        onClick = onClick,
        icon = { AreaIcon(Modifier.size(24.dp)) },
        titleSlot = if (editing) {
            {
                EditableTitle(
                    title = area.title,
                    style = ThingsType.RowTitleBold,
                    placeholder = "New Area",
                    onTitleChange = onTitleChange,
                    onEditDone = onEditDone,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            null
        },
        trailing = {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = ThingsColors.TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }
        },
    )
}

@Composable
private fun ProjectRow(
    project: Project,
    editing: Boolean,
    onTitleChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeRow(
        title = project.title,
        titleStyle = ThingsType.RowTitle,
        onClick = onClick,
        icon = { ProgressIcon(project.progress, Modifier.size(24.dp)) },
        modifier = modifier,
        titleSlot = if (editing) {
            {
                EditableTitle(
                    title = project.title,
                    style = ThingsType.RowTitle,
                    placeholder = "New Project",
                    onTitleChange = onTitleChange,
                    onEditDone = onEditDone,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = ScreenPadding),
        thickness = 1.dp,
        color = ThingsColors.Divider,
    )
}

/**
 * The round + button. The touch area is always the full 52dp. While it is dragged the visible circle
 * shrinks to three quarters, turns grey and shows a cross; when [cancelling] it grows back to full
 * size and turns red.
 */
@Composable
private fun AddButton(
    onClick: () -> Unit,
    hidden: Boolean,
    dragging: Boolean,
    cancelling: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    val circleSize by animateDpAsState(
        when {
            cancelling -> ThingsSpacing.FabSize
            dragging -> ThingsSpacing.FabSize * 0.75f
            else -> ThingsSpacing.FabSize
        },
        label = "fabSize",
    )
    val circleColor by animateColorAsState(
        when {
            cancelling -> CancelRed
            dragging -> DragGray
            else -> ThingsColors.Fab
        },
        label = "fabColor",
    )

    // The wrapper always keeps its 52dp slot so the menu can read the button's real position.
    // While the menu is open the morphing surface draws the button, so nothing is drawn (or
    // focusable) here and there is never a duplicate + behind the panel.
    Box(
        modifier
            .size(ThingsSpacing.FabSize)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag(amount)
                    },
                    onDragEnd = { currentEnd() },
                    onDragCancel = { currentCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!hidden) {
            Box(
                Modifier
                    .size(circleSize)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = FabShadowColor,
                        spotColor = FabShadowColor,
                    )
                    .clip(CircleShape)
                    .background(circleColor)
                    .clickable(onClickLabel = "Open create menu", role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (dragging) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = if (cancelling) "Cancel" else null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    AddFabGlyph()
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Home - with areas")
@Composable
private fun HomeScreenPreview() {
    ThingsTheme { HomeScreen(sampleHomeState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Home - first launch")
@Composable
private fun HomeScreenEmptyPreview() {
    ThingsTheme { HomeScreen(emptyHomeState()) }
}
