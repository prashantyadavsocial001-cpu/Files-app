package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocFormat
import com.example.model.FileCategory
import com.example.model.MediaFormat
import com.example.model.VaultItem
import com.example.model.formatFileSize
import com.example.ui.components.FormatBadge
import com.example.ui.components.badgeColorForItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterStudioScreen(
    sourceItem: VaultItem?,
    isConverting: Boolean,
    lastConvertedItem: VaultItem?,
    onPickSourceFile: () -> Unit,
    onConvertDocumentToVault: (VaultItem, DocFormat) -> Unit,
    onExportDocumentToDevice: (VaultItem, DocFormat) -> Unit,
    onConvertImageToVault: (VaultItem, MediaFormat?, Boolean, Int) -> Unit,
    onOpenConverted: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDocTarget by remember(sourceItem) {
        val default = when (sourceItem?.docFormat) {
            DocFormat.PDF -> DocFormat.DOCX
            DocFormat.DOCX -> DocFormat.PDF
            DocFormat.XLSX -> DocFormat.CSV
            DocFormat.CSV -> DocFormat.XLSX
            DocFormat.PPTX -> DocFormat.PDF
            else -> DocFormat.PDF
        }
        mutableStateOf(default)
    }

    var selectedImgTarget by remember(sourceItem) { mutableStateOf<MediaFormat?>(MediaFormat.WEBP) }
    var exportImageAsPdf by remember(sourceItem) { mutableStateOf(false) }
    var imageQuality by remember { mutableIntStateOf(90) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Format Interchange Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "100% offline conversion between PDF, Office XML, Text & Image formats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Source Selector Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Source File",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (sourceItem == null) {
                        OutlinedButton(
                            onClick = onPickSourceFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("converter_pick_source")
                        ) {
                            Icon(Icons.Filled.FileOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Document or Image")
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sourceItem.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FormatBadge(
                                        label = sourceItem.extension.ifBlank { "FILE" },
                                        color = badgeColorForItem(sourceItem)
                                    )
                                    Text(
                                        text = formatFileSize(sourceItem.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(onClick = onPickSourceFile) {
                                Text("Change")
                            }
                        }
                    }
                }
            }
        }

        if (sourceItem != null && sourceItem.category == FileCategory.IMAGE) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Target Image / PDF Format",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(MediaFormat.PNG, MediaFormat.JPEG, MediaFormat.WEBP).forEach { fmt ->
                                FilterChip(
                                    selected = !exportImageAsPdf && selectedImgTarget == fmt,
                                    onClick = {
                                        exportImageAsPdf = false
                                        selectedImgTarget = fmt
                                    },
                                    label = { Text(fmt.extension.uppercase()) }
                                )
                            }
                            FilterChip(
                                selected = exportImageAsPdf,
                                onClick = { exportImageAsPdf = true },
                                label = { Text("PDF PAGE") }
                            )
                        }
                        if (!exportImageAsPdf && selectedImgTarget != MediaFormat.PNG) {
                            Column {
                                Text(
                                    text = "Compression Quality: $imageQuality%",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Slider(
                                    value = imageQuality.toFloat(),
                                    onValueChange = { imageQuality = it.toInt() },
                                    valueRange = 30f..100f
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onConvertImageToVault(sourceItem, selectedImgTarget, exportImageAsPdf, imageQuality)
                            },
                            enabled = !isConverting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("converter_run_image")
                        ) {
                            if (isConverting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.SwapHoriz, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Convert & Save to Vault")
                            }
                        }
                    }
                }
            }
        } else if (sourceItem != null && sourceItem.docFormat != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Choose Target Format",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DocFormat.writableFormats.forEach { fmt ->
                                FilterChip(
                                    selected = selectedDocTarget == fmt,
                                    onClick = { selectedDocTarget = fmt },
                                    label = { Text(fmt.extension.uppercase()) },
                                    modifier = Modifier.testTag("target_format_${fmt.extension}")
                                )
                            }
                        }
                        Text(
                            text = "Target: ${selectedDocTarget.displayName} (.${selectedDocTarget.extension})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onConvertDocumentToVault(sourceItem, selectedDocTarget) },
                                enabled = !isConverting,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("converter_save_vault")
                            ) {
                                if (isConverting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.SwapHoriz, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save to Vault")
                                }
                            }
                            OutlinedButton(
                                onClick = { onExportDocumentToDevice(sourceItem, selectedDocTarget) },
                                enabled = !isConverting,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("converter_export_device")
                            ) {
                                Icon(Icons.Filled.SaveAlt, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Export As")
                            }
                        }
                    }
                }
            }
        } else if (sourceItem != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Audio and video files play natively in Media Studio. Select a Document (PDF, DOCX, XLSX, PPTX, TXT, MD, CSV, HTML, JSON, RTF) or Image (PNG, JPG, WEBP) to convert formats.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (lastConvertedItem != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConverted(lastConvertedItem) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Converted: ${lastConvertedItem.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Tap to open in Studio (${formatFileSize(lastConvertedItem.sizeBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            text = "OPEN",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Interchange Capabilities Matrix Summary
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Supported Interchange Paths",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MatrixLine("Prose & Docs", "PDF, DOCX, DOC → PDF, DOCX, MD, HTML, TXT, RTF, JSON, XML, PPTX")
                    MatrixLine("Spreadsheets", "XLSX, XLS, CSV, TSV → XLSX, CSV, TSV, PDF (Landscape), HTML, JSON")
                    MatrixLine("Presentations", "PPTX, PPT → PPTX, PDF (16:9 Slides), DOCX, MD, HTML, TXT")
                    MatrixLine("Images", "PNG, JPEG, WEBP, GIF, BMP, HEIF → PNG, JPEG, WEBP, PDF")
                }
            }
        }
    }
}

@Composable
private fun MatrixLine(label: String, desc: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
