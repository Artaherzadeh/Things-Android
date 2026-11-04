package com.things.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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

/** The divider block above an area: 12dp, the 1dp line, 12dp. */
private val AreaDividerBlock = 25.dp

/** How far the list has to be pulled down from the top before Quick Find opens. */
private val PullToSearchDistance = 96.dp

private const val LooseProjectsKey = "loose-projects"
private const val SettingsKey = "settings"

/**
 * The home screen. It lists no to-dos: only the system lists, projects and areas. Settings is the
 * last row of the list, so it scrolls with the rest instead of staying on screen.
 *
 * - Pulling the list down while it is at the top opens Quick Find, like tapping the search field.
 * - The + button opens the create menu when tapped. Dragging it creates a new project where it is
 *   let go: over an area it goes into that area (at the row nearest the finger), over the loose
 *   projects it goes among them, anywhere else it becomes the first loose project.
 *   [onCreateProjectAt] gets the area id (null for no area) and the position in that list.
 */
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
) {
    var collapsedAreas by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
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
        val key = info?.key
        val rowPx = with(density) { RowHeight.toPx() }
        val looseCount = state.looseProjects.size
        if (info != null && key == LooseProjectsKey) {
            val index = (((y - info.offset) / rowPx) + 0.5f).toInt().coerceIn(0, looseCount)
            return null to index
        }
        val area = state.areas.firstOrNull { it.id == key }
        if (info != null && area != null) {
            val dividerPx = if (state.areas.indexOf(area) > 0 || looseCount > 0) {
                with(density) { AreaDividerBlock.toPx() }
            } else {
                0f
            }
            val count = area.projects.size
            val index = if (area.id in collapsedAreas) {
                count
            } else {
                (((y - info.offset - dividerPx - rowPx) / rowPx) + 0.5f).toInt().coerceIn(0, count)
            }
            return area.id to index
        }
        return null to if (key == SettingsKey) looseCount else 0
    }

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
                .nestedScroll(pullToSearch),
            contentPadding = PaddingValues(top = statusBarTop + 12.dp, bottom = 140.dp),
        ) {
            item(key = "quick-find") { QuickFind(onClick = onSearchClick) }

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

            // Projects without an area
            if (state.looseProjects.isNotEmpty()) {
                item(key = LooseProjectsKey) {
                    state.looseProjects.forEach { project ->
                        ProjectRow(project) { onProjectClick(project) }
                    }
                }
            }

            // Areas and their projects
            state.areas.forEachIndexed { index, area ->
                item(key = area.id) {
                    val expanded = area.id !in collapsedAreas
                    Column {
                        if (index > 0 || state.looseProjects.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            SectionDivider()
                            Spacer(Modifier.height(12.dp))
                        }
                        AreaRow(
                            area = area,
                            expanded = expanded,
                            onClick = { onAreaClick(area) },
                            onToggle = {
                                collapsedAreas = if (expanded) collapsedAreas + area.id else collapsedAreas - area.id
                            },
                        )
                        AnimatedVisibility(visible = expanded) {
                            Column(Modifier.animateContentSize()) {
                                area.projects.forEach { project ->
                                    ProjectRow(project) { onProjectClick(project) }
                                }
                            }
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
                onBoundsChanged = { fabBounds = it },
                onDragStart = { dragPos = fabBounds?.center },
                onDrag = { delta -> dragPos = dragPos?.plus(delta) },
                onDragEnd = {
                    val p = dragPos
                    dragPos = null
                    if (p != null) {
                        val (areaId, index) = dropTarget(p)
                        currentOnCreateProjectAt(areaId, index)
                    }
                },
                onDragCancel = { dragPos = null },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = ThingsSpacing.FabMargin),
            )
        }

        // The new project under the finger while the + is dragged.
        val p = dragPos
        if (p != null) {
            val local = p - rootOrigin
            val half = with(density) { (RowHeight / 2).toPx() }
            Row(
                Modifier
                    .offset { IntOffset(0, (local.y - half).roundToInt()) }
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(RowHeight)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.05f),
                    )
                    .background(ThingsColors.Background, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
                    ProgressIcon(0f, Modifier.size(24.dp))
                }
                Spacer(Modifier.width(IconTextGap))
                Text("New Project", style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
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

@Composable
private fun QuickFind(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = ScreenPadding)
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ThingsColors.SearchBackground)
            .clickable(onClick = onClick),
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

@Composable
private fun HomeRow(
    title: String,
    titleStyle: TextStyle,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(IconTextGap))
        Text(title, style = titleStyle, modifier = Modifier.weight(1f))
        trailing()
    }
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
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "areaChevron")
    HomeRow(
        title = area.title,
        titleStyle = ThingsType.RowTitleBold,
        onClick = onClick,
        icon = { AreaIcon(Modifier.size(24.dp)) },
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
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    HomeRow(
        title = project.title,
        titleStyle = ThingsType.RowTitle,
        onClick = onClick,
        icon = { ProgressIcon(project.progress, Modifier.size(24.dp)) },
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

@Composable
private fun AddButton(
    onClick: () -> Unit,
    hidden: Boolean,
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

    // The wrapper always keeps its 52dp slot so the menu can read the button's real position.
    // While the menu is open the morphing surface draws the button, so nothing is drawn (or
    // focusable) here and there is never a duplicate + behind the panel.
    Box(
        modifier
            .size(ThingsSpacing.FabSize)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
    ) {
        if (!hidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = FabShadowColor,
                        spotColor = FabShadowColor,
                    )
                    .clip(CircleShape)
                    .background(ThingsColors.Fab)
                    .clickable(onClickLabel = "Open create menu", role = Role.Button, onClick = onClick)
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
                AddFabGlyph()
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
