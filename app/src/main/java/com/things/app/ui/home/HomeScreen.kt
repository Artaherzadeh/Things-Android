package com.things.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import com.things.app.ui.components.TaskRow
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

// Layout metrics taken from the Figma frames (dp, 393dp wide reference).
private val ScreenPaddingStart = 20.dp
private val ScreenPaddingEnd = 24.dp
private val RowHeight = 46.dp
private val IconSlot = 24.dp
private val IconTextGap = 8.dp
private val GroupGap = 11.dp

@Composable
fun HomeScreen(
    state: HomeUiState = sampleHomeState(),
    onSearchClick: () -> Unit = {},
    onSystemListClick: (SystemList) -> Unit = {},
    onAreaClick: (Area) -> Unit = {},
    onProjectClick: (Project) -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onNewToDo: () -> Unit = {},
    onNewProject: () -> Unit = {},
    onNewArea: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    var collapsedAreas by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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

    fun selectCreation(type: CreationType) {
        if (!menuExpanded) return // ignore repeat taps while closing
        menuExpanded = false
        when (type) {
            CreationType.ToDo -> onNewToDo()
            CreationType.Project -> onNewProject()
            CreationType.Area -> onNewArea()
        }
    }

    BackHandler(enabled = menuExpanded) { menuExpanded = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(ThingsColors.Background)
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBarTop + 12.dp, bottom = 140.dp),
        ) {
            item { QuickFind(onClick = onSearchClick) }

            item { Spacer(Modifier.height(28.dp)) }

            // Inbox
            item {
                SystemRow(SystemList.Inbox, count = state.inboxCount) { onSystemListClick(SystemList.Inbox) }
                Spacer(Modifier.height(GroupGap))
            }

            // Today / Upcoming / Anytime / Someday
            item {
                SystemRow(SystemList.Today, count = state.todayCount, badge = state.todayBadge) {
                    onSystemListClick(SystemList.Today)
                }
                SystemRow(SystemList.Upcoming) { onSystemListClick(SystemList.Upcoming) }
                SystemRow(SystemList.Anytime) { onSystemListClick(SystemList.Anytime) }
                SystemRow(SystemList.Someday) { onSystemListClick(SystemList.Someday) }
                Spacer(Modifier.height(GroupGap))
            }

            // Logbook
            item {
                SystemRow(SystemList.Logbook) { onSystemListClick(SystemList.Logbook) }
                Spacer(Modifier.height(17.dp))
                SectionDivider()
                Spacer(Modifier.height(18.dp))
            }

            // To-dos that belong to no project and no area: on their own, outside every list
            if (state.looseTasks.isNotEmpty()) {
                items(state.looseTasks, key = { "home-task-${it.id}" }) { task ->
                    TaskRow(
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onToggle = { onToggleTask(task.id) },
                    )
                }
                item(key = "home-tasks-gap") { Spacer(Modifier.height(14.dp)) }
            }

            // Projects without an area
            if (state.looseProjects.isNotEmpty()) {
                item {
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
                        if (index > 0 || state.looseProjects.isNotEmpty() || state.looseTasks.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            SectionDivider()
                            Spacer(Modifier.height(10.dp))
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
        }

        // Bottom bar: Settings (centered) + floating add button (right)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .height(52.dp)
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
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

            AddButton(
                onClick = { menuExpanded = true },
                hidden = menuOnScreen,
                onBoundsChanged = { fabBounds = it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
            )
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
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ThingsColors.SearchBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = ThingsColors.SearchContent,
                modifier = Modifier.size(22.dp),
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
            .padding(start = ScreenPaddingStart, end = ScreenPaddingEnd),
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
                SystemList.Inbox -> InboxIcon(Modifier.size(22.dp))
                SystemList.Today -> StarIcon(Modifier.size(24.dp))
                SystemList.Upcoming -> CalendarIcon(Modifier.size(22.dp))
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
                Spacer(Modifier.width(10.dp))
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
                        .size(26.dp)
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
        modifier = Modifier.padding(horizontal = ScreenPaddingStart),
        thickness = 1.dp,
        color = ThingsColors.Divider,
    )
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    hidden: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The wrapper always keeps its 52dp slot so the menu can read the button's real position.
    // While the menu is open the morphing surface draws the button, so nothing is drawn (or
    // focusable) here and there is never a duplicate + behind the panel.
    Box(
        modifier
            .size(52.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
    ) {
        if (!hidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = FabShadowColor,
                        spotColor = FabShadowColor,
                    )
                    .clip(CircleShape)
                    .background(ThingsColors.Fab)
                    .clickable(onClickLabel = "Open create menu", role = Role.Button, onClick = onClick),
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
