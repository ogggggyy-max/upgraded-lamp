package com.lamp.os.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lamp.os.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Draws the chosen wallpaper. For "system" nothing is drawn, so the system
 * wallpaper (including any live wallpaper) shows through the window.
 *
 * @param playing video only plays (and makes sound) while this is true
 * @param lock use the lock screen's sound setting instead of the home screen's
 */
@Composable
fun WallpaperLayer(playing: Boolean, lock: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val v by Prefs.version.collectAsState()
    val type = remember(v) { Prefs.wallpaperType }
    val uri = remember(v) { Prefs.wallpaperUri }
    val volume = remember(v, lock) {
        val sound = if (lock) Prefs.lockVideoSound else Prefs.videoSound
        if (sound) Prefs.videoVolume else 0f
    }
    when {
        uri == null -> Unit
        type == "image" -> key(uri) { ImageWallpaper(Uri.parse(uri), modifier) }
        type == "video" -> key(uri) { VideoWallpaper(Uri.parse(uri), volume, playing, modifier) }
    }
}

@Composable
private fun ImageWallpaper(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val dm = ctx.resources.displayMetrics
                val maxSide = max(dm.widthPixels, dm.heightPixels).coerceAtMost(3000)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val s = info.size
                    val scale = maxSide.toFloat() / max(s.width, s.height)
                    if (scale < 1f) d.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    bmp?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoWallpaper(uri: Uri, volume: Float, playing: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            // Don't take audio focus: the wallpaper shouldn't pause your music app.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, volume) { player.volume = volume }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
