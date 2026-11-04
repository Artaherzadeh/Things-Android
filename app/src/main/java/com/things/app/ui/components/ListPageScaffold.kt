package com.things.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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

    /** On the area of one project (or heading): from its title down to the next one. */
    data class InGroup(val groupId: String) : AddTarget

    /** At the left edge of the screen. [atKey] is the key of the list row under the finger, if any. */
    data class Heading(val atKey: Any?) : AddTarget
}

/** Dropping the add button closer than this to the left edge of the screen adds a heading. */
private val HeadingEdge = 72.dp

/**
 * Shared frame of every list page (Upcoming, Anytime, Inbox, ...): the top bar with the back button
 * and the three-dots menu, an icon plus large title that scroll with the list, and the floating add
 * button, 20dp from the right and 20dp above the bottom edge.
 * [content] adds the rows.
 *
 * [titleTrailing] is an optional control drawn right after the title (the project menu).
 * [titleBottomPadding] is the space between the title and the first row.
 * Pass [onTitleChange] to make the title editable (a project's name).
 * [onMenuAction] reports the action chosen in the three-dots menu; [enabledMenuActions] says which
 * of them can be chosen. While that menu is open the page behind it is dimmed.
 * Pass [expansion] when the page has to-dos that open in place: tapping anywhere that is not a
 * button or the open card then closes the open to-do.
 * While the keyboard is open a button at the top right hides it; when the keyboard closes by any
 * means the text cursor is removed from the field.
 *
 * The add button has up to three behaviours. A tap calls [onAddClick]. When [onAddAt] is given the
 * button can also be dragged: let go over a project or heading area (found with [groupForKey],
 * which turns the key of a list row into the id of its project or heading) to add inside it, or
 * near the left edge to add a heading.
 * Pass your own [listState] when the page needs to read or drive the list (reordering).
 *
 * Select mode works on every page: the menu entry Select turns it on, a Done button replaces the
 * top bar, the add button gives way to a bar with When, Move, Delete and more, and
 * [onSelectionAction] receives the chosen action with the ids of the ticked to-dos. Rows that are
 * ExpandableTaskRow pick the mode up on their own. When and Set Tags open their sheet here and are
 * applied to every ticked to-do through [expansion]. A page that passes its own [selection] handles
 * Delete and Mark as Completed itself; otherwise they are applied through [expansion] too.
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
    groupForKey: (Any) -> String? = { null },
    selection: SelectionState? = null,
    onSelectionAction: (SelectionAction, Set<String>) -> Unit = { _, _ -> },
    onTitleChange: ((String) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val ownSelection = selection ?: rememberSelectionState()
    val selecting = ownSelection.active

    val currentOnAddAt by rememberUpdatedState(onAddAt)
    val currentGroupForKey by rememberUpdatedState(groupForKey)
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var listTop by remember { mutableFloatStateOf(0f) }
    var fabCenter by remember { mutableStateOf(Offset.Zero) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var pageMenuOpen by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkRequest?>(null) }
    val scrimAlpha by animateFloatAsState(if (pageMenuOpen) 0.4f else 0f, label = "pageMenuScrim")
    val edgePx = with(density) { HeadingEdge.toPx() }

    // Closing the keyboard (back gesture, hide button, anything) also ends the editing: no cursor
    // keeps blinking in a field that cannot be typed into.
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }

    BackHandler(enabled = selecting) { ownSelection.finish() }

    fun targetAt(p: Offset): AddTarget {
        val y = (p.y - listTop).toInt()
        val key = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y >= it.offset && y < it.offset + it.size }
            ?.key
        if (p.x < edgePx) return AddTarget.Heading(key)
        val group = key?.let { currentGroupForKey(it) }
        return if (group != null) AddTarget.InGroup(group) else AddTarget.Top
    }

    CompositionLocalProvider(LocalSelection provides ownSelection) {
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
                            Modifier.pointerInput(expansion) { detectTapGestures { expansion.collapse() } }
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
                                expansion?.collapse()
                                ownSelection.start()
                            } else {
                                onMenuAction(action)
                            }
                        },
                        enabledActions = enabledMenuActions,
                        onMenuOpenChange = { pageMenuOpen = it },
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { listTop = it.positionInRoot().y }
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    item(key = "page-title") {
                        TitleRow(title, icon, titleTag, titleTrailing, titleBottomPadding, onTitleChange)
                    }
                    content()
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
                    onAction = { action ->
                        val ids = ownSelection.selected
                        if (selection == null) {
                            when (action) {
                                SelectionAction.Delete -> expansion?.deleteAll(ids)
                                SelectionAction.MarkCompleted -> expansion?.markCompleted(ids)
                                else -> Unit
                            }
                        }
                        if (action == SelectionAction.When || action == SelectionAction.SetTags) {
                            bulk = BulkRequest(action, ids)
                        }
                        onSelectionAction(action, ids)
                        ownSelection.finish()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = ThingsSpacing.FabMargin),
                )
            } else {
                AddFab(
                    onClick = onAddClick,
                    dragEnabled = onAddAt != null,
                    onDragStart = { dragPos = fabCenter },
                    onDrag = { delta -> dragPos = dragPos?.plus(delta) },
                    onDragEnd = {
                        val p = dragPos
                        dragPos = null
                        if (p != null) currentOnAddAt?.invoke(targetAt(p))
                    },
                    onDragCancel = { dragPos = null },
                    onCenterChanged = { fabCenter = it },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = ThingsSpacing.FabMargin, bottom = ThingsSpacing.FabMargin),
                )
            }

            // Dims the page while the three-dots menu is open. The menu itself is drawn above it.
            if (scrimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )
            }

            val p = dragPos
            if (p != null) {
                val target = targetAt(p)
                val half = with(density) { ThingsSpacing.FabSize.toPx() / 2f }
                val local = p - rootOrigin

                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(6.dp)
                        .fillMaxHeight(0.5f)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(ThingsColors.Fab.copy(alpha = if (target is AddTarget.Heading) 1f else 0.3f))
                )
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (local.x - 60.dp.toPx()).roundToInt().coerceAtLeast(8),
                                (local.y - half - 56.dp.toPx()).roundToInt(),
                            )
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(ThingsColors.MenuSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        when (target) {
                            is AddTarget.Top -> "New to-do at the top"
                            is AddTarget.InGroup -> "New to-do here"
                            is AddTarget.Heading -> "New heading"
                        },
                        style = ThingsType.Count,
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset((local.x - half).roundToInt(), (local.y - half).roundToInt()) }
                        .size(ThingsSpacing.FabSize)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = FabShadowColor,
                            spotColor = FabShadowColor,
                        )
                        .clip(CircleShape)
                        .background(ThingsColors.Fab),
                    contentAlignment = Alignment.Center,
                ) {
                    PlusGlyph(Modifier.size(24.dp))
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

@Composable
private fun TitleRow(
    title: String,
    icon: @Composable () -> Unit,
    titleTag: String,
    trailing: (@Composable () -> Unit)?,
    bottomPadding: Dp,
    onTitleChange: ((String) -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ThingsSpacing.PageMargin,
                end = ThingsSpacing.PageMargin,
                top = 12.dp,
                bottom = bottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
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
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() }
                    .testTag(titleTag),
            )
        } else {
            Text(
                title,
                style = ThingsType.PageTitle,
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

@Composable
private fun AddFab(
    onClick: () -> Unit,
    dragEnabled: Boolean,
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

    Box(
        modifier
            .size(ThingsSpacing.FabSize)
            .onGloballyPositioned {
                onCenterChanged(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f))
            }
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = FabShadowColor,
                spotColor = FabShadowColor,
            )
            .clip(CircleShape)
            .background(ThingsColors.Fab)
            .clickable(onClickLabel = "Add", role = Role.Button, onClick = onClick)
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
        PlusGlyph(Modifier.size(24.dp))
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
