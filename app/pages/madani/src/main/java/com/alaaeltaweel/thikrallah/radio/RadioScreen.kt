package com.alaaeltaweel.thikrallah.presentation.screen.radio

import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ✅ زي كروم بالظبط: نفتح الرابط ونشوف هل هو صوت مباشر ولا قايمة تشغيل (m3u/pls) ونستخرج الرابط الحقيقي منها
private fun resolveDirectStreamUrl(originalUrl: String): String {
    return try {
        val connection = URL(originalUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val contentType = connection.contentType ?: ""
        val finalUrl = connection.url.toString()

        val isPlaylist = contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("scpls", ignoreCase = true) ||
                contentType.contains("x-mpequrl", ignoreCase = true)

        if (!isPlaylist) {
            connection.disconnect()
            return finalUrl // صوت مباشر بالفعل - نستخدم الرابط النهائي بعد أي تحويلة
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val cleanUrl = body.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                when {
                    line.startsWith("http", ignoreCase = true) -> line
                    line.contains("=http", ignoreCase = true) -> line.substringAfter("=")
                    else -> null
                }
            }
            .firstOrNull()

        cleanUrl?.takeIf { it.startsWith("http") } ?: originalUrl
    } catch (e: Exception) {
        originalUrl // لو أي خطأ حصل، نرجع للرابط الأصلي زي ما كان
    }
}

@Composable
fun RadioScreen(
    onBackClick: () -> Unit = {},
    viewModel: RadioViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPlayingId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(state.channels) {
        val (url, _) = viewModel.consumePlayUrl() ?: return@LaunchedEffect
        val id = state.channels.firstOrNull { it.streamUrl == url }?.id ?: return@LaunchedEffect
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingId = id
        val resolvedUrl = withContext(Dispatchers.IO) { resolveDirectStreamUrl(url) } // ✅ زي كروم - نستخرج رابط الصوت الحقيقي لو الرابط الأصلي قايمة تشغيل
        val mp = MediaPlayer()
        @Suppress("DEPRECATION")
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mp.setDataSource(resolvedUrl)
        mp.setOnPreparedListener { it.start(); viewModel.onChannelReady(id) }
        mp.setOnErrorListener { _, _, _ -> viewModel.onPlayerError(); true }
        mp.prepareAsync()
        mediaPlayer = mp
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.stop(); mediaPlayer?.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B5E20))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "إذاعات القرآن الكريم",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.channels, key = { it.id }) { channel ->
                RadioChannelItem(
                    channel = channel,
                    onPlayClick = { viewModel.onPlayClick(channel.id) },
                    onPauseClick = {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentPlayingId = -1
                        viewModel.onPauseClick(channel.id)
                    }
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
            }
        }
    }
}

@Composable
private fun RadioChannelItem(
    channel: RadioUiState.RadioChannelUiState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (channel.isPlaying) onPauseClick() else onPlayClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                channel.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF1B5E20),
                    strokeWidth = 2.dp
                )
                channel.isPlaying -> IconButton(onClick = onPauseClick) {
                    Icon(Icons.Default.Pause, contentDescription = "إيقاف", tint = Color(0xFF1B5E20))
                }
                else -> IconButton(onClick = onPlayClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = Color(0xFF1B5E20))
                }
            }
        }
        Text(
            text = channel.nameAr,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
    }
}
