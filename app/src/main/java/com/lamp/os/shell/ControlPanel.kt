package com.lamp.os.shell

import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lamp.os.data.Apps
import com.lamp.os.gps.FakeGpsActivity
import com.lamp.os.gps.MockLocationService
import com.lamp.os.settings.SettingsActivity
import com.lamp.os.ui.lampFrame
import kotlinx.coroutines.delay
import java.util.Date

private class Tile(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val onLong: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun ControlPanel(visible: Boolean, svc: ShellAccessibilityService, onClose: () -> Unit) {
    val scrim by animateFloatAsState(if (visible) 0.55f else 0f, label = "scrim")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            PanelBody(svc, onClose)
        }
    }
}

@Composable
private fun PanelBody(svc: ShellAccessibilityService, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        Sys.initTorch(ctx)
        while (true) { delay(1000); tick++ }
    }
    var dragY by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(28.dp)

    BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(10.dp)) {
        val wide = maxWidth > 720.dp
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xF2000000), shape)
                .lampFrame(28.dp, 2.dp, Color(0xF2000000))
                .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close the panel
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (dragY < -120f) onClose(); dragY = 0f },
                        onDragCancel = { dragY = 0f },
                        onVerticalDrag = { _, d -> dragY += d },
                    )
                }
                .padding(18.dp)
        ) {
            Header(tick, onClose)
            Spacer(Modifier.height(14.dp))
            if (wide) {
                Row {
                    Column(Modifier.weight(1f)) { Controls(svc, tick) }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) { Notifications(svc, Modifier.heightIn(max = 520.dp)) }
                }
            } else {
                Controls(svc, tick)
                Spacer(Modifier.height(14.dp))
                Notifications(svc, Modifier.heightIn(max = 340.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(44.dp, 4.dp)
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun Header(tick: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember(tick) { Date() }
    val time = DateFormat.getTimeFormat(ctx).format(now)
    val date = DateFormat.format("EEEE, d MMMM", now).toString()
    val battery = remember(tick / 10) { Sys.battery(ctx) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(date, color = Color.White, fontSize = 14.sp)
            if (battery >= 0) Text("Battery $battery%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
    }
}

@Composable
private fun Controls(svc: ShellAccessibilityService, tick: Int) {
    val ctx = LocalContext.current
    val torch by Sys.torchOn.collectAsState()
    val dnd by NotifService.dnd.collectAsState()
    val gps by MockLocationService.running.collectAsState()

    val tiles = remember(tick, torch, dnd, gps) {
        listOf(
            Tile("Wi-Fi", Icons.Filled.Wifi, Sys.wifiOn(ctx)) {
                svc.launchFromPanel(Intent(Settings.Panel.ACTION_WIFI))
            },
            Tile("Bluetooth", Icons.Filled.Bluetooth, Sys.bluetoothOn(ctx)) {
                svc.launchFromPanel(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            Tile("Mobile data", Icons.Filled.SignalCellularAlt, false) {
                svc.launchFromPanel(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            },
            Tile("Airplane", Icons.Filled.AirplanemodeActive, Sys.airplaneOn(ctx)) {
                svc.launchFromPanel(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            },
            Tile("Flashlight", Icons.Filled.FlashlightOn, torch) { Sys.toggleTorch(ctx) },
            Tile("Auto-rotate", Icons.Filled.ScreenRotation, Sys.autoRotate(ctx)) {
                if (Sys.canWriteSettings(ctx)) Sys.setAutoRotate(ctx, !Sys.autoRotate(ctx))
                else svc.launchFromPanel(Sys.writeSettingsIntent(ctx))
            },
            Tile("Do not disturb", Icons.Filled.DoNotDisturbOn, dnd) { NotifService.toggleDnd() },
            Tile(
                "Fake GPS", Icons.Filled.LocationOn, gps,
                onLong = { svc.launchFromPanel(Intent(ctx, FakeGpsActivity::class.java)) },
            ) {
                if (gps) MockLocationService.stop(ctx) else MockLocationService.start(ctx)
            },
            Tile("Screenshot", Icons.Filled.Screenshot, false) { svc.screenshot() },
            Tile("Lock", Icons.Filled.Lock, false) { svc.lockNow() },
            Tile("Power", Icons.Filled.PowerSettingsNew, false) { svc.powerMenu() },
            Tile("Settings", Icons.Filled.Settings, false) {
                svc.launchFromPanel(Intent(Settings.ACTION_SETTINGS))
            },
            Tile("Lamp", Icons.Filled.Tune, false) {
                svc.launchFromPanel(Intent(ctx, SettingsActivity::class.java))
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { TileView(it, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Sliders(svc)
        MediaCard()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(t: Tile, modifier: Modifier) {
    val fg = if (t.active) Color.Black else Color.White
    Column(
        modifier
            .lampFrame(16.dp, 2.dp, if (t.active) Color.White else Color.Black)
            .combinedClickable(onClick = t.onClick, onLongClick = t.onLong)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(t.label, color = fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Sliders(svc: ShellAccessibilityService) {
    val ctx = LocalContext.current
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
    )
    var bright by remember { mutableFloatStateOf(Sys.brightness(ctx).toFloat() / Sys.brightnessMax) }
    var vol by remember { mutableFloatStateOf(Sys.volume(ctx)) }
    val canWrite = remember { Sys.canWriteSettings(ctx) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BrightnessMedium, "Brightness", tint = Color.White)
        Spacer(Modifier.width(10.dp))
        if (canWrite) {
            Slider(
                value = bright,
                onValueChange = { bright = it; Sys.setBrightness(ctx, (it * Sys.brightnessMax).toInt()) },
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                "Tap to allow brightness control",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).clickable { svc.launchFromPanel(Sys.writeSettingsIntent(ctx)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Slider(
            value = vol,
            onValueChange = { vol = it; Sys.setVolume(ctx, it) },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MediaCard() {
    val c by Media.controller.collectAsState()
    val tick by Media.tick.collectAsState()
    val controller = c ?: return
    val (title, artist, playing) = remember(controller, tick) {
        val md = controller.metadata
        Triple(
            md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing",
            md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            controller.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().lampFrame(16.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotEmpty()) {
                Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tc = controller.transportControls
        IconButton(onClick = { tc.skipToPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White) }
        IconButton(onClick = { if (playing) tc.pause() else tc.play() }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = Color.White)
        }
        IconButton(onClick = { tc.skipToNext() }) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}

@Composable
private fun Notifications(svc: ShellAccessibilityService, modifier: Modifier) {
    val ctx = LocalContext.current
    val items by NotifService.items.collectAsState()
    val connected = NotifService.instance != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Notifications", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (items.any { it.clearable }) {
            Text(
                "Clear all",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { NotifService.clearAll() }.padding(6.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    when {
        !connected -> Text(
            "Turn on notification access for Lamp to see notifications here.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.clickable {
                svc.launchFromPanel(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
        items.isEmpty() -> Text("No notifications", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        else -> LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.key }) { n ->
                NotifCard(n, onOpen = { svc.closePanel(); NotifService.open(ctx, n) })
            }
        }
    }
}

@Composable
private fun NotifCard(n: NotifItem, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val app = remember(n.pkg) { Apps.byPackage(n.pkg) }
    val label = remember(n.pkg) { Apps.label(ctx, n.pkg) }
    Row(
        Modifier.fillMaxWidth().lampFrame(16.dp, 1.5.dp).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (app != null) {
            Image(app.icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$label · ${DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
            )
            if (n.title.isNotBlank()) {
                Text(n.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (n.text.isNotBlank()) {
                Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (n.clearable) {
            IconButton(onClick = { NotifService.dismiss(n) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
