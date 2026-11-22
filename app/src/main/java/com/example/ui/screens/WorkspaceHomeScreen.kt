package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VaultItem
import com.example.model.formatFileSize
import com.example.ui.components.FormatBadge
import com.example.ui.components.VaultFileRow
import com.example.ui.theme.BadgeAudio
import com.example.ui.theme.BadgeDocx
import com.example.ui.theme.BadgeImage
import com.example.ui.theme.BadgePdf
import com.example.ui.theme.BadgePptx
import com.example.ui.theme.BadgeXlsx

@Composable
fun WorkspaceHomeScreen(
    recentFiles: List<VaultItem>,
    vaultUsageBytes: Long,
    onOpenAnyFile: () -> Unit,
    onOpenMediaPicker: () -> Unit,
    onCreateNewDoc: () -> Unit,
    onGoToConverter: () -> Unit,
    onGoToFiles: () -> Unit,
    onGoToSettings: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onItemMore: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FolioFlow Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Offline Document & Media Engine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onGoToSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings and Privacy Audit",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Lightweight Hero Studio Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner_1795274499326),
                    contentDescription = "FolioFlow Offline Studio Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xE60B111A),
                                    Color(0xB30B111A),
                                    Color(0x4D0B111A)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F766E).copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "100% Offline",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "100% OFFLINE • ZERO NETWORK",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Read, Edit & Interchange",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Vault footprint: ${formatFileSize(vaultUsageBytes)} • Native PDF, Office XML & Media3",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // Quick Action Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStudioTile(
                        title = "Open Any File",
                        subtitle = "PDF, DOCX, XLSX, PPTX...",
                        icon = Icons.Filled.FolderOpen,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenAnyFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_open_file")
                    )
                    QuickStudioTile(
                        title = "Pick Media",
                        subtitle = "Photos & Videos",
                        icon = Icons.Filled.PermMedia,
                        accent = BadgeImage,
                        onClick = onOpenMediaPicker,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_pick_media")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStudioTile(
                        title = "New Document",
                        subtitle = "PDF, DOCX, XLSX, PPTX",
                        icon = Icons.Filled.AddCircleOutline,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onCreateNewDoc,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_new_doc")
                    )
                    QuickStudioTile(
                        title = "Format Matrix",
                        subtitle = "Convert any format",
                        icon = Icons.Filled.SwapHoriz,
                        accent = BadgeAudio,
                        onClick = onGoToConverter,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_converter")
                    )
                }
            }
        }

        // Supported Format Strip
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Native Offline Codecs Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatBadge("PDF", BadgePdf)
                        FormatBadge("DOCX", BadgeDocx)
                        FormatBadge("XLSX", BadgeXlsx)
                        FormatBadge("PPTX", BadgePptx)
                        FormatBadge("PNG/WEBP", BadgeImage)
                        FormatBadge("MP3/MP4/OGG", BadgeAudio)
                    }
                }
            }
        }

        // Recent & Pinned Files Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent & Pinned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Browse Vault →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onGoToFiles)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("home_browse_vault")
                )
            }
        }

        if (recentFiles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your recent documents & media appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Open the Files tab to explore preloaded offline samples.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            items(recentFiles.take(10), key = { it.id }) { item ->
                VaultFileRow(
                    item = item,
                    onClick = { onOpenItem(item) },
                    onMoreClick = { onItemMore(item) }
                )
            }
        }
    }
}

@Composable
private fun QuickStudioTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
