package com.things.app.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Instrumented tests for the add menu. Run on a device or emulator. */
class AddMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var toDoCalls = 0
    private var projectCalls = 0
    private var areaCalls = 0

    @Before
    fun setUp() {
        toDoCalls = 0
        projectCalls = 0
        areaCalls = 0
        rule.setContent {
            ThingsTheme {
                HomeScreen(
                    onNewToDo = { toDoCalls++ },
                    onNewProject = { projectCalls++ },
                    onNewArea = { areaCalls++ },
                )
            }
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription("Add").performClick()
        rule.waitForIdle()
    }

    @Test
    fun menuIsHiddenUntilAddIsTapped() {
        rule.onNodeWithText("New To-Do").assertDoesNotExist()
        openMenu()
        rule.onNodeWithText("New To-Do").assertIsDisplayed()
        rule.onNodeWithText("Quickly add a to-do to your inbox.").assertIsDisplayed()
        rule.onNodeWithText("New Project").assertIsDisplayed()
        rule.onNodeWithText("New Area").assertIsDisplayed()
    }

    @Test
    fun outsideTapClosesMenuWithoutTriggeringActions() {
        openMenu()
        rule.onNodeWithContentDescription("Close menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("New To-Do").assertDoesNotExist()
        assertEquals(0, toDoCalls + projectCalls + areaCalls)
    }

    @Test
    fun backClosesMenu() {
        openMenu()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("New To-Do").assertDoesNotExist()
        assertEquals(0, toDoCalls + projectCalls + areaCalls)
    }

    @Test
    fun newToDoInvokesItsCallbackOnce() {
        openMenu()
        rule.onNodeWithText("New To-Do").performClick()
        rule.waitForIdle()
        assertEquals(1, toDoCalls)
        assertEquals(0, projectCalls + areaCalls)
        rule.onNodeWithText("New To-Do").assertDoesNotExist()
    }

    @Test
    fun newProjectInvokesItsCallbackOnce() {
        openMenu()
        rule.onNodeWithText("New Project").performClick()
        rule.waitForIdle()
        assertEquals(1, projectCalls)
        assertEquals(0, toDoCalls + areaCalls)
    }

    @Test
    fun newAreaInvokesItsCallbackOnce() {
        openMenu()
        rule.onNodeWithText("New Area").performClick()
        rule.waitForIdle()
        assertEquals(1, areaCalls)
        assertEquals(0, toDoCalls + projectCalls)
    }

    @Test
    fun reopeningAfterCloseWorks() {
        openMenu()
        rule.onNodeWithContentDescription("Close menu").performClick()
        rule.waitForIdle()
        openMenu()
        rule.onNodeWithText("New Area").assertIsDisplayed()
    }
}
