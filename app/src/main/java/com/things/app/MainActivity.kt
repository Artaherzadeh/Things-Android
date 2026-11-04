package com.things.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.things.app.ui.navigation.ThingsNavHost
import com.things.app.ui.theme.ThingsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThingsTheme {
                ThingsNavHost()
            }
        }
    }
}
