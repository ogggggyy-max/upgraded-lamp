package com.lamp.os.shell

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamp.os.data.Apps
import com.lamp.os.data.Prefs
import com.lamp.os.ui.WallpaperLayer
import com.lamp.os.ui.lampFrame
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val screenOn by ShellState.screenOn.collectAsState()
    val notifs by NotifService.items.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPin by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val hasPin = remember { Prefs.pinHash != null }
    val systemWallpaper = remember { Prefs.wallpaperType == "system" || Prefs.wallpaperUri == null }

    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    LaunchedEffect(screenOn) { if (!screenOn) showPin = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (systemWallpaper) Color.Black.copy(alpha = 0.2f) else Color.Black)
    ) {
        WallpaperLayer(playing = screenOn, lock = true)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.5f)))
            )
        )

        if (!showPin) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .offset { IntOffset(0, (drag.coerceAtMost(0f) / 3f).roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (drag < -150f) {
                                    if (hasPin) showPin = true else onUnlock()
                                }
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                            onVerticalDrag = { _, d -> drag += d },
                        )
                    }
            ) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val date = Date(now)
                    Text(DateFormat.getTimeFormat(ctx).format(date), color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Thin)
                    Text(DateFormat.format("EEEE, d MMMM", date).toString(), color = Color.White, fontSize = 18.sp)
                    if (notifs.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            notifs.take(3).forEach { n ->
                                Column(Modifier.lampFrame(14.dp, 1.5.dp, Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(Apps.label(ctx, n.pkg), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                                    Text(n.title.ifBlank { n.text }, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (notifs.size > 3) {
                                Text("+${notifs.size - 3} more", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White)
                    Text("Swipe up to unlock", color = Color.White, fontSize = 14.sp)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                PinPad(onSuccess = onUnlock, onCancel = { showPin = false })
            }
        }
    }
}

@Composable
private fun PinPad(onSuccess: () -> Unit, onCancel: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun press(k: String) {
        when (k) {
            "⌫" -> entered = entered.dropLast(1)
            "OK" -> if (Prefs.checkPin(entered)) onSuccess() else { error = true; entered = "" }
            else -> if (entered.length < 8) {
                entered += k
                error = false
                if (entered.length >= 4 && Prefs.checkPin(entered)) onSuccess()
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (error) "Wrong PIN" else "Enter PIN", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(maxOf(4, entered.length)) { i ->
                Box(
                    Modifier.size(12.dp).clip(CircleShape)
                        .background(if (i < entered.length) Color.White else Color.Transparent)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK").chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(68.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape)
                            .clickable { press(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, color = Color.White, fontSize = if (k.length > 1) 18.sp else 26.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
    }
}
