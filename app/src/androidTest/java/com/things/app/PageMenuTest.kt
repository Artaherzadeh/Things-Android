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
import com.things.app.ui.components.PageMenuAction
import com.things.app.ui.inbox.InboxScreen
import com.things.app.ui.inbox.sampleInboxState
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.today.TodayScreen
import com.things.app.ui.today.sampleTodayState
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

        PageMenuAction.entries.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
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

        PageMenuAction.entries.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
    }
}
