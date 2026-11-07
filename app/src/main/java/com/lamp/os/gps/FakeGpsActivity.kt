package com.lamp.os.gps

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lamp.os.data.Prefs
import com.lamp.os.shell.Sys
import com.lamp.os.ui.LampTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FakeGpsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(this@FakeGpsActivity, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
        }
        enableEdgeToEdge()
        setContent { LampTheme { FakeGpsScreen() } }
    }
}

private fun fmt(d: Double) = "%.6f".format(d)

/** Looks up a place name with OpenStreetMap's free Nominatim service. */
private suspend fun geocode(query: String): GeoPoint? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(query, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "LampOS/1.0 (Android launcher)")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(body)
        if (arr.length() == 0) null
        else arr.getJSONObject(0).let { GeoPoint(it.getString("lat").toDouble(), it.getString("lon").toDouble()) }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun FakeGpsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by MockLocationService.running.collectAsState()
    val error by MockLocationService.error.collectAsState()
    val v by Prefs.version.collectAsState()
    val jitter = remember(v) { Prefs.gpsJitter }

    var latText by remember { mutableStateOf(fmt(Prefs.gpsLat)) }
    var lonText by remember { mutableStateOf(fmt(Prefs.gpsLon)) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(Prefs.gpsLat, Prefs.gpsLon))
        }
    }
    val marker = remember {
        Marker(mapView).apply {
            position = GeoPoint(Prefs.gpsLat, Prefs.gpsLon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Fake location"
        }
    }

    fun setPoint(p: GeoPoint, center: Boolean) {
        Prefs.setGps(p.latitude, p.longitude)
        latText = fmt(p.latitude)
        lonText = fmt(p.longitude)
        marker.position = p
        if (center) mapView.controller.animateTo(p)
        mapView.invalidate()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(mapView) {
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { setPoint(p, false); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        mapView.overlays.add(marker)
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onDetach()
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        // "lat, lon" typed directly
        val parts = q.split(",", " ").filter { it.isNotBlank() }.mapNotNull { it.toDoubleOrNull() }
        if (parts.size == 2) { setPoint(GeoPoint(parts[0], parts[1]), true); return }
        status = "Searching…"
        scope.launch {
            val p = geocode(q)
            if (p != null) { setPoint(p, true); status = null } else status = "Nothing found for \"$q\""
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fake GPS", color = Color.White, fontSize = 26.sp, modifier = Modifier.weight(1f))
            Text(if (running) "● ON" else "○ OFF", color = Color.White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search a place, or type lat, lon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { search() }) { Text("Go") }
        }
        status?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(4.dp))
        Text("Tap the map to move the pin.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = latText, onValueChange = { latText = it }, label = { Text("Latitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = lonText, onValueChange = { lonText = it }, label = { Text("Longitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val la = latText.toDoubleOrNull()
                val lo = lonText.toDoubleOrNull()
                if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) setPoint(GeoPoint(la, lo), true)
                else status = "Latitude must be -90..90 and longitude -180..180"
            }) { Text("Set") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Small random drift (looks more natural)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = jitter, onCheckedChange = { Prefs.gpsJitter = it })
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
            OutlinedButton(onClick = {
                Sys.start(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }) { Text("Open Developer options") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { if (running) MockLocationService.stop(ctx) else MockLocationService.start(ctx) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (running) "Stop fake GPS" else "Start fake GPS (runs 24/7)", fontSize = 16.sp)
        }
    }
}
