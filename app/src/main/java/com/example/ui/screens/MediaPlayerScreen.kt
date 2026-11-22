package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.engine.MediaEngine
import com.example.engine.MediaTechMetadata
import com.example.model.FileCategory
import com.example.model.VaultItem
import kotlinx.coroutines.delay
import kotlin.math.sin

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    item: VaultItem,
    queue: List<VaultItem>,
    player: ExoPlayer,
    onSelectQueueItem: (VaultItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (item.category == FileCategory.VIDEO) {
            player.pause()
        }
        onBack()
    }

    val context = LocalContext.current
    val isVideo = item.category == FileCategory.VIDEO
    var metadata by remember(item.id) { mutableStateOf<MediaTechMetadata?>(null) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    LaunchedEffect(item.id) {
        metadata = MediaEngine.extractMediaMetadata(context, item)
    }

    DisposableEffect(item.id) {
        onDispose {
            metadata = null
        }
    }

    LaunchedEffect(player, item.id) {
        while (true) {
            isPlaying = player.isPlaying
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = metadata?.title?.takeIf { it.isNotBlank() } ?: item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(item.extension.uppercase())
                                metadata?.let { m ->
                                    if (m.videoWidth > 0 && m.videoHeight > 0) {
                                        append(" • ${m.videoWidth}×${m.videoHeight}")
                                    }
                                    if (m.bitrateBps > 0) {
                                        append(" • ${m.bitrateBps / 1000} kbps")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isVideo) player.pause()
                            onBack()
                        },
                        modifier = Modifier.testTag("media_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isVideo) {
                // Hardware-accelerated Media3 PlayerView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                this.resizeMode = resizeMode
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { pv ->
                            pv.player = player
                            pv.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT },
                        label = { Text("Fit") }
                    )
                    FilterChip(
                        selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM },
                        label = { Text("Crop Fill") }
                    )
                }
            } else {
                // Aesthetic Audio Studio Stage with Album Art / Harmonic Canvas
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF115E59).copy(alpha = 0.45f), Color(0xFF1E293B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val art = metadata?.albumArt
                        if (art != null && !art.isRecycled) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = "Album art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(136.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AudioFile,
                                    contentDescription = "Audio track",
                                    tint = Color(0xFF2DD4BF),
                                    modifier = Modifier.size(48.dp)
                                )
                                HarmonicBarsCanvas(
                                    isPlaying = isPlaying,
                                    phaseMs = currentPosMs,
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Seek Slider & Timestamps
            Column {
                val safeDur = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentPosMs.coerceAtMost(durationMs).toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..safeDur
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPosMs), style = MaterialTheme.typography.labelMedium)
                    Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                        Player.REPEAT_MODE_OFF
                    } else {
                        Player.REPEAT_MODE_ONE
                    }
                    player.repeatMode = repeatMode
                }) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Toggle repeat",
                        tint = if (repeatMode == Player.REPEAT_MODE_ONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = {
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(30.dp))
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { if (player.isPlaying) player.pause() else player.play() }
                        .testTag("media_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(onClick = {
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L)))
                }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(30.dp))
                }

                FilterChip(
                    selected = playbackSpeed != 1f,
                    onClick = {
                        playbackSpeed = when (playbackSpeed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2f
                            2f -> 0.75f
                            else -> 1f
                        }
                        player.setPlaybackSpeed(playbackSpeed)
                    },
                    label = { Text("${playbackSpeed}x") },
                    leadingIcon = { Icon(Icons.Filled.Speed, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Up Next Queue
            if (queue.size > 1) {
                Text(
                    text = "Up Next (${queue.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(queue, key = { it.id }) { qItem ->
                        val active = qItem.id == item.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectQueueItem(qItem) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = qItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = qItem.extension.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HarmonicBarsCanvas(
    isPlaying: Boolean,
    phaseMs: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barCount = 24
        val gap = 6.dp.toPx()
        val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3f)
        val phase = phaseMs / 160.0
        for (i in 0 until barCount) {
            val amp = if (isPlaying) {
                0.25f + 0.72f * ((sin(phase + i * 0.55) + 1.0) / 2.0).toFloat()
            } else {
                0.2f
            }
            val barH = size.height * amp
            drawRoundRect(
                color = if (i % 3 == 0) Color(0xFFFBBF24) else Color(0xFF2DD4BF),
                topLeft = Offset(i * (barW + gap), (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
