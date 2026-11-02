package com.neonblocks.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.neonblocks.game.ui.NeonBlocksApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 24)
        setContent {
            val scheme = darkColorScheme(
                primary = Color(0xFF42E8FF),
                secondary = Color(0xFFA56CFF),
                background = Color(0xFF080D18),
                surface = Color(0xFF111B2D),
                onPrimary = Color(0xFF06101C),
                onBackground = Color(0xFFEAF5FF),
                onSurface = Color(0xFFEAF5FF)
            )
            MaterialTheme(colorScheme = scheme) {
                NeonBlocksApp()
            }
        }
    }
}
