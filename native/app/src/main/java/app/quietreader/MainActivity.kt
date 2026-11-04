package app.quietreader

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.quietreader.ui.AppState
import app.quietreader.ui.Haptics
import app.quietreader.ui.LocalColors
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val settings = QuietSettings(this)
        val haptics = Haptics(this) { settings.haptics }
        setContent {
            val state = remember { AppState() }
            QuietTheme(settings, haptics) {
                val dark = LocalColors.current.isDark
                // Light or dark status-bar icons to match the theme, drawn edge to edge.
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                LaunchedEffect(settings.rotation) {
                    requestedOrientation = when (settings.rotation) {
                        Rotation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        Rotation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                        Rotation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    }
                }
                QuietApp(settings, state)
            }
        }
    }
}
