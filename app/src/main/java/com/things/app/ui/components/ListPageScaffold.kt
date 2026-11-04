package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.FabShadowColor
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/**
 * Shared frame of every list page (Upcoming, Anytime, Inbox, ...): the top bar with the back button
 * and the three-dots menu, an icon plus large title that scroll with the list, and the floating add
 * button. [content] adds the rows.
 *
 * [titleTrailing] is an optional control drawn right after the title (the project menu).
 * [titleBottomPadding] is the space between the title and the first row.
 * [onMenuAction] reports the action chosen in the three-dots menu; [enabledMenuActions] says which
 * of them can be chosen.
 */
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
    onAddClick: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(ThingsColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            PageTopBar(
                onBack = onBack,
                onMoreClick = onMoreClick,
                onMenuAction = onMenuAction,
                enabledActions = enabledMenuActions,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item(key = "page-title") {
                    TitleRow(title, icon, titleTag, titleTrailing, titleBottomPadding)
                }
                content()
            }
        }

        AddFab(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 16.dp),
        )
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
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
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
            .size(52.dp)
            .shadow(
                elevation = 6.dp,
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
