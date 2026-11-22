package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory
import com.example.model.StorageOrigin
import com.example.model.VaultItem
import com.example.model.formatFileSize
import com.example.ui.theme.BadgeAudio
import com.example.ui.theme.BadgeDocx
import com.example.ui.theme.BadgeImage
import com.example.ui.theme.BadgeOther
import com.example.ui.theme.BadgePdf
import com.example.ui.theme.BadgePptx
import com.example.ui.theme.BadgeText
import com.example.ui.theme.BadgeVideo
import com.example.ui.theme.BadgeXlsx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun badgeColorForItem(item: VaultItem): Color {
    if (item.isDirectory) return Color(0xFFF59E0B)
    return when (item.extension.lowercase()) {
        "pdf" -> BadgePdf
        "docx", "doc", "rtf" -> BadgeDocx
        "xlsx", "xls", "csv", "tsv" -> BadgeXlsx
        "pptx", "ppt" -> BadgePptx
        "txt", "md", "markdown", "json", "xml", "html", "htm" -> BadgeText
        else -> when (item.category) {
            FileCategory.IMAGE -> BadgeImage
            FileCategory.AUDIO -> BadgeAudio
            FileCategory.VIDEO -> BadgeVideo
            else -> BadgeOther
        }
    }
}

fun iconForItem(item: VaultItem): ImageVector {
    if (item.isDirectory) return Icons.Filled.Folder
    return when (item.extension.lowercase()) {
        "pdf" -> Icons.Filled.PictureAsPdf
        "xlsx", "xls", "csv", "tsv" -> Icons.Filled.TableChart
        "pptx", "ppt" -> Icons.Filled.Slideshow
        "docx", "doc", "txt", "md", "rtf", "html", "json", "xml" -> Icons.Filled.Description
        else -> when (item.category) {
            FileCategory.IMAGE -> Icons.Filled.Image
            FileCategory.AUDIO -> Icons.Filled.AudioFile
            FileCategory.VIDEO -> Icons.Filled.VideoFile
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }
}

@Composable
fun FormatBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun VaultFileRow(
    item: VaultItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = badgeColorForItem(item)
    val dateStr = if (item.lastModified > 0L) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    } else ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("file_row_${item.name}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForItem(item),
                    contentDescription = item.name,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeLabel = if (item.isDirectory) "DIR" else item.extension.ifBlank { "FILE" }
                    FormatBadge(label = badgeLabel, color = accent)
                    val meta = if (item.isDirectory) {
                        "${item.sizeBytes} items"
                    } else {
                        "${formatFileSize(item.sizeBytes)} • $dateStr"
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.origin == StorageOrigin.DEVICE_SAF) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(4.dp))
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("file_more_${item.name}")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "File options for ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileInspectorDialog(
    item: VaultItem,
    sha256: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "File Security & Metadata",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InspectorLine("Name", item.name)
                InspectorLine("Format", item.extension.uppercase().ifBlank { "Folder" })
                InspectorLine("MIME Type", item.mimeType)
                InspectorLine("Size", formatFileSize(item.sizeBytes))
                InspectorLine(
                    "Storage",
                    if (item.origin == StorageOrigin.VAULT) "Private Offline Vault" else "Device SAF Tree"
                )
                InspectorLine("SHA-256", sha256, monospace = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InspectorLine(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = if (monospace) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
