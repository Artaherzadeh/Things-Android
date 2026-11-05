package com.things.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.home.HomeScreen
import com.things.app.ui.home.sampleHomeState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLooseTasksTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun homeShowsNoToDos() {
        rule.setContent { ThingsTheme { HomeScreen(sampleHomeState()) } }

        rule.onNodeWithText("Buy a new notebook").assertDoesNotExist()
    }
}
