package com.lamp.os.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.lamp.os.data.Apps
import com.lamp.os.shell.ShellState
import com.lamp.os.ui.LampTheme
import com.lamp.os.ui.WallpaperLayer

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Back does nothing on the home screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent { LampTheme { LauncherScreen() } }
    }
}

@Composable
private fun LauncherScreen() {
    val apps by Apps.apps.collectAsState()
    val locked by ShellState.locked.collectAsState()
    val screenOn by ShellState.screenOn.collectAsState()
    val life by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Video wallpaper (and its sound) only plays while you can actually see the home screen.
    val playing = life.isAtLeast(Lifecycle.State.RESUMED) && !locked && screenOn

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(playing = playing, lock = false)
        Desktop(apps, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
    }
}
