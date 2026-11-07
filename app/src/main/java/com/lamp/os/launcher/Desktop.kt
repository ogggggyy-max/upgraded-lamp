package com.lamp.os.launcher

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lamp.os.data.AppEntry
import com.lamp.os.data.Apps
import com.lamp.os.data.Prefs
import com.lamp.os.gps.FakeGpsActivity
import com.lamp.os.settings.SettingsActivity
import com.lamp.os.shell.ShellAccessibilityService
import com.lamp.os.ui.lampFrame
import kotlin.math.max
import kotlin.math.roundToInt

private data class Placed(val app: AppEntry, val col: Int, val row: Int)
private data class Layout(val placed: List<Placed>, val positions: Map<String, Pair<Int, Int>>, val changed: Boolean, val overflow: Int)

/**
 * One page, Windows-desktop style: every app that isn't hidden or in the dock
 * gets a cell, filled top-to-bottom then left-to-right. Icons you've moved keep
 * their spot; new apps take the first free cell.
 */
private fun computeLayout(apps: List<AppEntry>, cols: Int, rows: Int): Layout {
    val saved = Prefs.desktop()
    val hidden = Prefs.hidden()
    val dock = Prefs.dock().toSet()
    val visible = apps.filter { it.key !in hidden && it.key !in dock }
    val taken = HashSet<Pair<Int, Int>>()
    val result = LinkedHashMap<String, Pair<Int, Int>>()
    val queue = ArrayList<AppEntry>()
    for (a in visible) {
        val p = saved[a.key]
        if (p != null && p.first in 0 until cols && p.second in 0 until rows && taken.add(p)) result[a.key] = p
        else queue += a
    }
    var overflow = 0
    var c = 0
    var r = 0
    for (a in queue) {
        while (c < cols && (c to r) in taken) {
            r++
            if (r >= rows) { r = 0; c++ }
        }
        if (c >= cols) { overflow++; continue }
        val p = c to r
        taken += p
        result[a.key] = p
    }
    val byKey = visible.associateBy { it.key }
    val placed = result.mapNotNull { (k, p) -> byKey[k]?.let { Placed(it, p.first, p.second) } }
    // Keep positions of apps that are temporarily missing (e.g. on an SD card).
    val merged = HashMap(saved).apply { putAll(result) }
    return Layout(placed, merged, merged != saved, overflow)
}

@Composable
fun Desktop(apps: List<AppEntry>, modifier: Modifier) {
    val ctx = LocalContext.current
    val v by Prefs.version.collectAsState()
    val iconDp = remember(v) { Prefs.iconSizeDp }.dp
    val labels = remember(v) { Prefs.showLabels }
    val framed = remember(v) { Prefs.framedIcons }
    val cellW = iconDp + 34.dp
    val cellH = iconDp + if (labels) 32.dp else 14.dp
    var pickSlot by remember { mutableStateOf<Int?>(null) }
    var bgMenu by remember { mutableStateOf(false) }

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val cols = max(1, (maxWidth / cellW).toInt())
            val rows = max(1, (maxHeight / cellH).toInt())
            val layout = remember(apps, v, cols, rows) { computeLayout(apps, cols, rows) }
            LaunchedEffect(layout) {
                if (layout.changed && apps.isNotEmpty()) Prefs.saveDesktop(layout.positions, notify = false)
            }

            // Empty desktop: long-press for the menu, double-tap to lock.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { bgMenu = true },
                        onDoubleTap = { ShellAccessibilityService.instance?.lockNow() },
                    )
                }
            ) {
                DropdownMenu(expanded = bgMenu, onDismissRequest = { bgMenu = false }) {
                    DropdownMenuItem(text = { Text("Wallpaper & settings") }, onClick = {
                        bgMenu = false
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                    DropdownMenuItem(text = { Text("Fake GPS") }, onClick = {
                        bgMenu = false
                        ctx.startActivity(Intent(ctx, FakeGpsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                    DropdownMenuItem(text = { Text("Tidy up icons") }, onClick = {
                        bgMenu = false
                        Prefs.resetDesktop()
                    })
                    if (layout.overflow > 0) {
                        DropdownMenuItem(
                            text = { Text("${layout.overflow} apps don't fit: make icons smaller in settings") },
                            onClick = { bgMenu = false },
                        )
                    }
                }
            }

            layout.placed.forEach { p ->
                key(p.app.key) {
                    DesktopIcon(p, cellW, cellH, cols, rows, iconDp, labels, framed)
                }
            }
        }
        Dock(apps, v, onEmptySlot = { pickSlot = it })
    }

    pickSlot?.let { slot ->
        AppPickerDialog(
            apps = apps,
            onDismiss = { pickSlot = null },
            onPick = {
                Prefs.setDockSlot(slot, it.key)
                pickSlot = null
            },
        )
    }
}

@Composable
private fun DesktopIcon(
    placed: Placed,
    cellW: Dp,
    cellH: Dp,
    cols: Int,
    rows: Int,
    iconDp: Dp,
    labels: Boolean,
    framed: Boolean,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cw = with(density) { cellW.toPx() }
    val ch = with(density) { cellH.toPx() }
    val menuSlop = with(density) { 10.dp.toPx() }
    val p by rememberUpdatedState(placed)
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .offset { IntOffset((p.col * cw + drag.x).roundToInt(), (p.row * ch + drag.y).roundToInt()) }
            .size(cellW, cellH)
            .zIndex(if (dragging) 1f else 0f)
            .alpha(if (dragging) 0.8f else 1f)
            .pointerInput(cols, rows) {
                iconGestures(
                    onTap = { Apps.launch(ctx, p.app) },
                    onLongStart = {
                        dragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { drag = it },
                    onLongEnd = { total ->
                        dragging = false
                        if (total.getDistance() < menuSlop) {
                            menu = true
                        } else {
                            val nc = ((p.col * cw + total.x) / cw).roundToInt().coerceIn(0, cols - 1)
                            val nr = ((p.row * ch + total.y) / ch).roundToInt().coerceIn(0, rows - 1)
                            Prefs.moveDesktop(p.app.key, nc, nr)
                        }
                        drag = Offset.Zero
                    },
                )
            }
    ) {
        AppIcon(p.app, iconDp, framed, labels)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val dock = Prefs.dock()
            val free = dock.indexOfFirst { it.isEmpty() }
            if (free >= 0) {
                DropdownMenuItem(text = { Text("Add to dock") }, onClick = {
                    menu = false
                    Prefs.setDockSlot(free, p.app.key)
                })
            }
            DropdownMenuItem(text = { Text("Hide from desktop") }, onClick = {
                menu = false
                Prefs.hide(p.app.key)
            })
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                Apps.appInfo(ctx, p.app)
            })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                menu = false
                Apps.uninstall(ctx, p.app)
            })
        }
    }
}

private val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black, Offset(0f, 1f), 6f),
)

@Composable
fun AppIcon(app: AppEntry, iconDp: Dp, framed: Boolean, label: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (framed) {
            Box(Modifier.size(iconDp).lampFrame(iconDp * 0.26f, 1.5.dp).padding(iconDp * 0.14f)) {
                Image(app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
            }
        } else {
            Image(app.icon, contentDescription = app.label, modifier = Modifier.size(iconDp))
        }
        if (label) {
            Spacer(Modifier.height(3.dp))
            Text(
                app.label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** The bottom dock: five framed slots in a framed pill, like the reference picture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(apps: List<AppEntry>, v: Int, onEmptySlot: (Int) -> Unit) {
    val ctx = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val dock = remember(v) { Prefs.dock() }
    val slot = 46.dp
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.lampFrame(slot * 0.42f, 2.5.dp).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(Prefs.DOCK_SLOTS) { i ->
                val app = byKey[dock[i]]
                var menu by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(slot)
                        .lampFrame(slot * 0.26f, 2.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (app != null) Apps.launch(ctx, app) else onEmptySlot(i) },
                            onLongClick = { menu = true },
                        )
                        .padding(slot * 0.17f)
                ) {
                    if (app != null) Image(app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (app == null) "Choose app" else "Change app") }, onClick = {
                            menu = false
                            onEmptySlot(i)
                        })
                        if (app != null) {
                            DropdownMenuItem(text = { Text("Remove from dock") }, onClick = {
                                menu = false
                                Prefs.setDockSlot(i, "")
                            })
                        }
                    }
                }
            }
        }
    }
}
