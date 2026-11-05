package com.things.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.FabShadowColor
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType
import kotlin.math.roundToInt

/** Where the add button was let go after being dragged. */
sealed interface AddTarget {
    /** Above everything: before every project and heading. */
    object Top : AddTarget

    /**
     * Next to a to-do: under it when [after], above it otherwise. [groupId] is the project or
     * heading it is in, null for a to-do that is in none. With no [anchorTaskId] the target is the
     * start of that project or heading, right under its title.
     */
    data class At(val groupId: String?, val anchorTaskId: String?, val after: Boolean) : AddTarget

    /** At the left edge of the screen. [atKey] is the key of the list row under the finger, if any. */
    data class Heading(val atKey: Any?) : AddTarget
}

/** Dropping the add button closer than this to the left edge of the screen adds a heading. */
private val HeadingEdge = 72.dp
private val GhostShape = RoundedCornerShape(20.dp)
private val GhostHeight = 48.dp
private val GhostFingerOffset = 16.dp

/** The grey of the cross that stays where the add button was, and the red when it cancels. */
private val DragGray = Color(0xFF8E8E93)
private val CancelRed = Color(0xFFFF3B30)

private fun shareText(context: Context, lines: List<String>) {
    if (lines.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Shared frame of every list page (Upcoming, Anytime, Inbox, ...): the top bar with the back button
 * and the three-dots menu, the icon plus large title, the list, and the floating add button, 20dp
 * from the right and 20dp above the bottom edge.
 * [content] adds the rows.
 *
 * The title does not scroll. It stays under the top bar and the rows go under it as the list
 * moves: just below the title a fade (the page color becoming see-through) hides them little by
 * little, so they melt away instead of being cut by a hard edge. At rest the first row starts
 * [titleBottomPadding] under the title.
 *
 * A long title goes on to the next lines; the icon stays centered on the first line.
 * [titleTrailing] is an optional control drawn right after the title (the project menu).
 * Pass [onTitleChange] to make the title editable (a project's name).
 * [menuActions] are the entries of the three-dots menu, [enabledMenuActions] says which of them can
 * be chosen and [onMenuAction] reports the chosen one. While that menu is open the page behind it
 * is dimmed.
 * Pass [expansion] when the page has to-dos that open in place: while one is open, the first touch
 * anywhere outside its card only closes the card and goes no further.
 * While the keyboard is open a button at the top right hides it; when the keyboard closes by any
 * means the text cursor is removed from the field.
 *
 * The add button has up to three behaviours. A tap calls [onAddClick]. When [onAddAt] is given the
 * button can also be dragged: a small card, only as wide as its text, follows the finger over the
 * whole screen and the list opens a gap where the to-do would land (the rows below slide down); let
 * go to add it there. Near the left edge the card turns into a heading (blue title and a line, no
 * card) and the gap opens where the heading would go. While it is dragged the button's place shows
 * a grey cross at three quarters of its size; bring the finger back onto it and the cross grows to
 * full size and turns red: letting go there cancels. [groupForKey] turns the key of a list row into
 * the id of its project or heading and [taskIdForKey] into the id of its to-do.
 * Pass allowHeading = false on a page without headings (the Area page): the left edge then does
 * nothing special. [addLabel] is the text of the dragged card ("New Project" on the Area page).
 * Pass your own [listState] when the page needs to read or drive the list (reordering).
 *
 * Select mode works on every page that has the Select entry in its menu: Select turns it on, a Done
 * button replaces the top bar, the add button gives way to a bar with When, Move, Delete and more,
 * and [onSelectionAction] receives the chosen action with the ids of the ticked to-dos. Rows that
 * are ExpandableTaskRow pick the mode up on their own. When and Set Tags open their sheet here and
 * are applied to every ticked to-do through [expansion], as are Delete, the status change and
 * Share. Duplicate is applied through [expansion] unless the page passes its own [selection].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ListPageScaffold(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleTag: String = "page_title",
    titleTrailing: (@Composable () -> Unit)? = null,
    titleBottomPadding: Dp = 24.dp,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onMenuAction: (PageMenuAction) -> Unit = {},
    enabledMenuActions: Set<PageMenuAction> = DefaultEnabledMenuActions,
    expansion: TaskExpansion? = null,
    onAddClick: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onAddAt: ((AddTarget) -> Unit)? = null,
    allowHeading: Boolean = true,
    addLabel: String = "New To-Do",
    groupForKey: (Any) -> String? = { null },
    taskIdForKey: (Any) -> String? = { null },
    selection: SelectionState? = null,
    onSelectionAction: (SelectionAction, Set<String>) -> Unit = { _, _ -> },
    onTitleChange: ((String) -> Unit)? = null,
    menuActions: List<PageMenuAction> = DefaultMenuActions,
    content: LazyListScope.() -> Unit,
) {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val ownSelection = selection ?: rememberSelectionState()
    val selecting = ownSelection.active

    val currentOnAddAt by rememberUpdatedState(onAddAt)
    val currentGroupForKey by rememberUpdatedState(groupForKey)
    val currentTaskIdForKey by rememberUpdatedState(taskIdForKey)
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listTop by remember { mutableFloatStateOf(0f) }
    var titleHeightPx by remember { mutableIntStateOf(0) }
    var fabCenter by remember { mutableStateOf(Offset.Zero) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var leftFab by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var dropSlot by remember { mutableStateOf(DropSlot.None) }
    var dragTarget by remember { mutableStateOf<AddTarget?>(null) }
    var pageMenuOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkRequest?>(null) }
    val scrimAlpha by animateFloatAsState(
        if (pageMenuOpen || selectionMenuOpen) 0.4f else 0f,
        label = "menuScrim",
    )
    val edgePx = with(density) { HeadingEdge.toPx() }
    val gapPx = with(density) { DropGapHeight.toPx() }
    val fabRadiusPx = with(density) { ThingsSpacing.FabSize.toPx() / 2f }
    val titleHeight = with(density) { titleHeightPx.toDp() }
    val fadeHeight = titleBottomPadding.coerceIn(12.dp, 24.dp)
    val headerBottomPx = titleHeightPx + with(density) { titleBottomPadding.toPx() }

    // Closing the keyboard (back gesture, hide button, anything) also ends the editing: no cursor
    // keeps blinking in a field that cannot be typed into.
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }

    // The top bar that reports the menu closing leaves the screen when the select mode starts, so
    // the dimming has to be cleared here or the page stays grey.
    LaunchedEffect(selecting) {
        if (selecting) pageMenuOpen = false else selectionMenuOpen = false
    }

    BackHandler(enabled = selecting) { ownSelection.finish() }

    fun targetAt(p: Offset): AddTarget {
        // Align the drop gap with the ghost box so the ghost box is centered inside the gap
        val fingerToGapTop = with(density) {
            (GhostFingerOffset + GhostHeight / 2 + DropGapHeight / 2).toPx()
        }
        val y = (p.y - fingerToGapTop - listTop).toInt()
        // Over the title: before everything.
        if (y < headerBottomPx) return if (allowHeading && p.x < edgePx) AddTarget.Heading(null) else AddTarget.Top
        val items = listState.layoutInfo.visibleItemsInfo
        val info = items.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: items.lastOrNull()
        val key = info?.key
        if (allowHeading && p.x < edgePx) return AddTarget.Heading(key)
        if (info == null || key == null) return AddTarget.Top
        val taskId = currentTaskIdForKey(key)
        val group = currentGroupForKey(key)
        if (taskId == null) return if (group != null) AddTarget.At(group, null, true) else AddTarget.Top
        // Measure the row without the gap that is open in it, so the gap does not move the target.
        val top = info.offset + if (dropSlot.beforeTaskId == taskId) gapPx else 0f
        val bottom = info.offset + info.size - if (dropSlot.afterTaskId == taskId) gapPx else 0f
        return AddTarget.At(group, taskId, after = y >= (top + bottom) / 2f)
    }

    /** Where the list has to make room for the target. A heading goes above the row under the finger. */
    fun slotFor(target: AddTarget): DropSlot = when (target) {
        is AddTarget.Top -> DropSlot(atTop = true)
        is AddTarget.At -> when {
            target.anchorTaskId == null -> DropSlot(afterGroupId = target.groupId)
            target.after -> DropSlot(afterTaskId = target.anchorTaskId)
            else -> DropSlot(beforeTaskId = target.anchorTaskId)
        }
        is AddTarget.Heading -> {
            val key = target.atKey
            val taskId = key?.let { currentTaskIdForKey(it) }
            val group = key?.let { currentGroupForKey(it) }
            when {
                taskId != null -> DropSlot(beforeTaskId = taskId)
                group != null -> DropSlot(beforeGroupId = group)
                else -> DropSlot.None
            }
        }
    }

    fun updateDrag(p: Offset?) {
        dragPos = p
        if (p == null) {
            leftFab = false
            cancelling = false
            dragTarget = null
            dropSlot = DropSlot.None
            return
        }
        // Cancelling needs the finger to have left the button first, then come back onto it.
        val distance = (p - fabCenter).getDistance()
        if (distance > fabRadiusPx * 1.3f) leftFab = true
        cancelling = leftFab && distance <= fabRadiusPx
        val target = if (cancelling) null else targetAt(p)
        dragTarget = target
        dropSlot = target?.let { slotFor(it) } ?: DropSlot.None
    }

    CompositionLocalProvider(
        LocalSelection provides ownSelection,
        LocalDropSlot provides dropSlot,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(ThingsColors.Background)
                .onGloballyPositioned { rootOrigin = it.positionInRoot() }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (expansion != null) {
                            // While a card is open the first touch outside it only closes it.
                            Modifier.pointerInput(expansion) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    val open = expansion.expandedId != null
                                    val inside = expansion.cardBounds?.contains(down.position + rootOrigin) == true
                                    if (open && !inside) {
                                        down.consume()
                                        waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
                                        expansion.collapse()
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (selecting) {
                    SelectTopBar(onDone = { ownSelection.finish() })
                } else {
                    PageTopBar(
                        onBack = onBack,
                        onMoreClick = onMoreClick,
                        onMenuAction = { action ->
                            if (action == PageMenuAction.Select) {
                                pageMenuOpen = false
                                expansion?.collapse()
                                ownSelection.start()
                            } else {
                                onMenuAction(action)
                            }
                        },
                        enabledActions = enabledMenuActions,
                        onMenuOpenChange = { pageMenuOpen = it },
                        actions = menuActions,
                    )
                }
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { listTop = it.positionInRoot().y }
                            .imePadding(),
                        contentPadding = PaddingValues(
                            top = titleHeight + titleBottomPadding,
                            bottom = 140.dp,
                        ),
                    ) {
                        // Room that opens under the title while the add button is dragged over it.
                        item(key = "drop-gap-top") {
                            Spacer(Modifier.height(if (dropSlot.atTop) DropGapHeight else 0.dp))
                        }
                        content()
                    }

                    // The title and the fade under it, drawn over the list.
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(ThingsColors.Background)
                                .onSizeChanged { titleHeightPx = it.height }
                        ) {
                            TitleRow(
                                title = title,
                                icon = icon,
                                titleTag = titleTag,
                                trailing = titleTrailing,
                                onTitleChange = onTitleChange,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(fadeHeight)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            ThingsColors.Background,
                                            ThingsColors.Background.copy(alpha = 0f),
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            if (imeVisible) {
                KeyboardDismissButton(
                    onClick = { focusManager.clearFocus() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = ThingsSpacing.PageMargin),
                )
            } else if (selecting) {
                SelectionBottomBar(
                    enabled = ownSelection.selected.isNotEmpty(),
                    completeLabel = expansion?.completionLabel(ownSelection.selected) ?: "Mark as Completed",
                    onAction = { action ->
                        val ids = ownSelection.selected
                        when (action) {
                            SelectionAction.Delete -> expansion?.deleteAll(ids)
                            SelectionAction.MarkCompleted -> expansion?.toggleCompleted(ids)
                            SelectionAction.Duplicate -> if (selection == null) expansion?.duplicate(ids)
                            SelectionAction.Share -> if (expansion != null) {
                                shareText(context, ids.mapNotNull { expansion.titleOf(it) })
                            }
                            SelectionAction.When, SelectionAction.SetTags -> bulk = BulkRequest(action, ids)
                            else -> Unit
                        }
                        onSelectionAction(action, ids)
                        ownSelection.finish()
                    },
                    onMenuOpenChange = { selectionMenuOpen = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = ThingsSpacing.FabMargin, end = ThingsSpacing.FabMargin, bottom = ThingsSpacing.FabMargin)
                        .fillMaxWidth(),
                )
            } else {
                AddFab(
                    onClick = onAddClick,
                    dragEnabled = onAddAt != null,
                    dragging = dragPos != null,
                    cancelling = cancelling,
                    onDragStart = { updateDrag(fabCenter) },
                    onDrag = { delta -> updateDrag(dragPos?.plus(delta)) },
                    onDragEnd = {
                        val target = dragTarget
                        updateDrag(null)
                        if (target != null) currentOnAddAt?.invoke(target)
                    },
                    onDragCancel = { updateDrag(null) },
                    onCenterChanged = { fabCenter = it },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = ThingsSpacing.FabMargin, bottom = ThingsSpacing.FabMargin),
                )
            }

            // Dims the page while a menu is open. The menu itself is drawn above it.
            if (scrimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )
            }

            val p = dragPos
            if (p != null && !cancelling) {
                val local = p - rootOrigin
                val heading = dragTarget is AddTarget.Heading
                val top = (local.y - with(density) { (GhostHeight + GhostFingerOffset).toPx() }).roundToInt()

                // The preview is only as wide as its text. It is centered on the finger and kept on the
                // screen, so the finger can take it to any part of the page.
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
                    if (heading) {
                        // Like a heading in the list: blue bold title and a thin line, no card.
                        Column(Modifier.width(IntrinsicSize.Max)) {
                            Box(
                                Modifier
                                    .height(GhostHeight)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "New Heading",
                                    style = ThingsType.RowTitleBold.copy(color = ThingsColors.ProjectBlue),
                                )
                            }
                            HorizontalDivider(thickness = 1.dp, color = ThingsColors.Divider)
                        }
                    } else {
                        // A small card like an open to-do: the same space left and right of the text.
                        Box(
                            Modifier
                                .shadow(
                                    elevation = 10.dp,
                                    shape = GhostShape,
                                    ambientColor = Color.Black.copy(alpha = 0.7f),
                                    spotColor = Color.Black.copy(alpha = 0.45f),
                                )
                                .background(ThingsColors.Background, GhostShape)
                                .widthIn(min = 112.dp)
                                .height(GhostHeight)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                addLabel,
                                style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary),
                            )
                        }
                    }
                }
            }
        }

        bulk?.let { request ->
            BulkSheets(request = request, expansion = expansion, onClose = { bulk = null })
        }
    }
}

/** The top bar of the select mode: only the blue Done pill, at the end. */
@Composable
private fun SelectTopBar(onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ThingsColors.Fab)
                .clickable(onClickLabel = "Done", role = Role.Button, onClick = onDone)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", style = ThingsType.RowTitleBold.copy(color = Color.White))
        }
    }
}

/**
 * The icon and the title, 12dp under the top bar. A long title goes on to the next lines; the icon
 * sits in a box as high as the first line of the title, so it stays centered on that line.
 */
@Composable
private fun TitleRow(
    title: String,
    icon: @Composable () -> Unit,
    titleTag: String,
    trailing: (@Composable () -> Unit)?,
    onTitleChange: ((String) -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var firstLine by remember { mutableStateOf(28.dp) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ThingsSpacing.PageMargin,
                end = ThingsSpacing.PageMargin,
                top = 12.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(width = 28.dp, height = maxOf(28.dp, firstLine)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.width(12.dp))
        if (onTitleChange != null) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle = ThingsType.PageTitle,
                cursorBrush = SolidColor(ThingsColors.Fab),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onTextLayout = { layout ->
                    if (layout.lineCount > 0) {
                        firstLine = with(density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp() }
                    }
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() }
                    .testTag(titleTag),
            )
        } else {
            Text(
                title,
                style = ThingsType.PageTitle,
                onTextLayout = { layout ->
                    if (layout.lineCount > 0) {
                        firstLine = with(density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp() }
                    }
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() }
                    .testTag(titleTag),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/**
 * The round add button. The touch area is always the full 52dp. While it is dragged the visible
 * circle shrinks to three quarters, turns grey and shows a cross; when [cancelling] it grows back
 * to full size and turns red.
 */
@Composable
private fun AddFab(
    onClick: () -> Unit,
    dragEnabled: Boolean,
    dragging: Boolean,
    cancelling: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onCenterChanged: (Offset) -> Unit,
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

    Box(
        modifier
            .size(ThingsSpacing.FabSize)
            .onGloballyPositioned {
                onCenterChanged(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
            }
            .then(
                if (dragEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { currentStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                currentDrag(amount)
                            },
                            onDragEnd = { currentEnd() },
                            onDragCancel = { currentCancel() },
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
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
                .clickable(onClickLabel = "Add", role = Role.Button, onClick = onClick),
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
                PlusGlyph(Modifier.size(24.dp))
            }
        }
    }
}

/** The blue pill at the top right that hides the keyboard. */
@Composable
private fun KeyboardDismissButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 56.dp, height = 40.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = FabShadowColor,
                spotColor = FabShadowColor,
            )
            .clip(CircleShape)
            .background(ThingsColors.Fab)
            .clickable(onClickLabel = "Hide keyboard", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.KeyboardHide,
            contentDescription = "Hide keyboard",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
