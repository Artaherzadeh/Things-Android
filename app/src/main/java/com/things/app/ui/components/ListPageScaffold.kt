package com.things.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.FabShadowColor
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType

/**
 * Shared frame of every list page (Upcoming, Anytime, Inbox, ...): the top bar with the back button
 * and the three-dots menu, an icon plus large title that scroll with the list, and the floating add
 * button, 20dp from the right and 20dp above the bottom edge.
 * [content] adds the rows.
 *
 * [titleTrailing] is an optional control drawn right after the title (the project menu).
 * [titleBottomPadding] is the space between the title and the first row.
 * [onMenuAction] reports the action chosen in the three-dots menu; [enabledMenuActions] says which
 * of them can be chosen.
 * Pass [expansion] when the page has to-dos that open in place: tapping anywhere that is not a
 * button or the open card then closes the open to-do.
 * While the keyboard is open a button at the top right hides it.
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
    content: LazyListScope.() -> Unit,
) {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    Box(
        modifier
            .fillMaxSize()
            .background(ThingsColors.Background)
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
            PageTopBar(
                onBack = onBack,
                onMoreClick = onMoreClick,
                onMenuAction = onMenuAction,
                enabledActions = enabledMenuActions,
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item(key = "page-title") {
                    TitleRow(title, icon, titleTag, titleTrailing, titleBottomPadding)
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
        } else {
            AddFab(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = ThingsSpacing.FabMargin, bottom = ThingsSpacing.FabMargin),
            )
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
) {
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
        Text(
            title,
            style = ThingsType.PageTitle,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() }
                .testTag(titleTag),
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(ThingsSpacing.FabSize)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = FabShadowColor,
                spotColor = FabShadowColor,
            )
            .clip(CircleShape)
            .background(ThingsColors.Fab)
            .clickable(onClickLabel = "Add", role = Role.Button, onClick = onClick),
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
