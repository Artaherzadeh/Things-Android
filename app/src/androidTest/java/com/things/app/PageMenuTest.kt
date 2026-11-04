package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.area.AreaScreen
import com.things.app.ui.area.sampleAreaState
import com.things.app.ui.components.AreaMenuActions
import com.things.app.ui.components.DefaultMenuActions
import com.things.app.ui.inbox.InboxScreen
import com.things.app.ui.inbox.sampleInboxState
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.today.TodayScreen
import com.things.app.ui.today.sampleTodayState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageMenuTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun moreButtonOpensTheMenuAndFilterByTagIsDisabled() {
        rule.setContent { ThingsTheme { InboxScreen(sampleInboxState()) } }

        rule.onNodeWithContentDescription("More").performClick()

        DefaultMenuActions.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        rule.onNodeWithText("Filter by Tag").assertIsNotEnabled()
        rule.onNodeWithText("Select").assertIsEnabled()
    }

    @Test
    fun choosingAnActionClosesTheMenu() {
        rule.setContent { ThingsTheme { InboxScreen(sampleInboxState()) } }

        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Share").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithText("Share").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun todayPageHasTheSameMenu() {
        rule.setContent { ThingsTheme { TodayScreen(sampleTodayState()) } }

        rule.onNodeWithContentDescription("More").performClick()

        DefaultMenuActions.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
    }

    @Test
    fun areaPageHasAddTagsDeleteAreaAndShare() {
        var deleted = 0
        rule.setContent { ThingsTheme { AreaScreen(sampleAreaState(), onDeleteArea = { deleted++ }) } }

        rule.onNodeWithContentDescription("More").performClick()

        AreaMenuActions.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        rule.onNodeWithText("Select").assertDoesNotExist()

        rule.onNodeWithText("Delete Area").performClick()
        assertEquals(1, deleted)
    }
}
