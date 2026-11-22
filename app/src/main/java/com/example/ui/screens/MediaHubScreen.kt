package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.FileCategory
import com.example.model.VaultItem
import com.example.model.formatFileSize
import com.example.ui.components.FormatBadge
import com.example.ui.components.badgeColorForItem
import java.io.File

@Composable
fun MediaHubScreen(
    mediaItems: List<VaultItem>,
    onPickPhotosVideos: () -> Unit,
    onPickAudioOrAnyMedia: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(FileCategory.ALL) }
    val filtered = remember(mediaItems, filter) {
        when (filter) {
            FileCategory.IMAGE, FileCategory.AUDIO, FileCategory.VIDEO ->
                mediaItems.filter { it.category == filter }
            else -> mediaItems
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Universal Media Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "PNG, JPEG, WEBP, GIF, MP3, M4A, OGG, OPUS, FLAC, WAV, MP4, MKV, WEBM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickPhotosVideos,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_photo_picker")
                ) {
                    Icon(Icons.Filled.PermMedia, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos & Video")
                }
                OutlinedButton(
                    onClick = onPickAudioOrAnyMedia,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_audio_picker")
                ) {
                    Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Audio / Media")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    FileCategory.ALL to "All Media",
                    FileCategory.IMAGE to "Images",
                    FileCategory.AUDIO to "Audio",
                    FileCategory.VIDEO to "Video"
                ).forEach { (cat, label) ->
                    FilterChip(
                        selected = filter == cat,
                        onClick = { filter = cat },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media files in this filter. Import photos, audio, or video using the buttons above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    MediaThumbRow(item = item, onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

@Composable
private fun MediaThumbRow(
    item: VaultItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val accent = badgeColorForItem(item)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.category == FileCategory.IMAGE) {
                    val model: Any? = item.contentUri ?: item.filePath?.let { File(it) }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model)
                            .size(160) // Low-memory thumbnail decoding!
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (item.category == FileCategory.VIDEO) {
                            Icons.Filled.VideoFile
                        } else {
                            Icons.Filled.AudioFile
                        },
                        contentDescription = item.name,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormatBadge(item.extension.ifBlank { "MEDIA" }, accent)
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.category != FileCategory.IMAGE) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${item.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
