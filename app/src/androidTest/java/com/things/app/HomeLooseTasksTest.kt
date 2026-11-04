package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.home.HomeScreen
import com.things.app.ui.home.sampleHomeState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLooseTasksTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun looseTaskIsShownAboveProjectsAndAreas() {
        val state = sampleHomeState()
        rule.setContent { ThingsTheme { HomeScreen(state) } }

        val task = state.looseTasks.first()
        rule.onNodeWithText(task.title).assertIsDisplayed()

        val taskY = rule.onNodeWithText(task.title).fetchSemanticsNode().positionInRoot.y
        val areaY = rule.onNodeWithText(state.areas.first().title).fetchSemanticsNode().positionInRoot.y
        assertTrue("Loose to-dos must be drawn above the first area", taskY < areaY)
    }
}
