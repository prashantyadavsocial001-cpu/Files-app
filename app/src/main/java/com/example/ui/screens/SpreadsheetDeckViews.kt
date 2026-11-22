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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SheetData
import com.example.model.SlideData
import com.example.model.UnifiedDocument

private fun colName(idx: Int): String {
    var n = idx + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.append(('A'.code + rem).toChar())
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}

@Composable
fun SpreadsheetStudioView(
    document: UnifiedDocument,
    onDocumentChanged: (UnifiedDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheets = document.asSheets()
    var activeSheetIdx by remember(document.title) { mutableIntStateOf(0) }
    val safeSheetIdx = activeSheetIdx.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
    val currentSheet = sheets.getOrNull(safeSheetIdx) ?: SheetData("Sheet1", listOf(listOf("")))

    var selectedRow by remember { mutableIntStateOf(0) }
    var selectedCol by remember { mutableIntStateOf(0) }
    var cellEditorText by remember(safeSheetIdx, selectedRow, selectedCol, currentSheet) {
        mutableStateOf(currentSheet.rows.getOrNull(selectedRow)?.getOrNull(selectedCol) ?: "")
    }

    fun updateCell(r: Int, c: Int, newValue: String) {
        val updatedSheets = sheets.toMutableList()
        val target = updatedSheets[safeSheetIdx]
        val mutableRows = target.rows.map { it.toMutableList() }.toMutableList()
        while (mutableRows.size <= r) mutableRows.add(mutableListOf())
        val maxCols = maxOf(mutableRows.maxOfOrNull { it.size } ?: 1, c + 1)
        mutableRows.forEach { row -> while (row.size < maxCols) row.add("") }
        mutableRows[r][c] = newValue
        updatedSheets[safeSheetIdx] = target.copy(rows = mutableRows)
        onDocumentChanged(document.copy(sheets = updatedSheets))
    }

    fun addRow() {
        val cols = (currentSheet.rows.maxOfOrNull { it.size } ?: 3).coerceAtLeast(1)
        val updatedSheets = sheets.toMutableList()
        val newRows = currentSheet.rows + listOf(List(cols) { "" })
        updatedSheets[safeSheetIdx] = currentSheet.copy(rows = newRows)
        onDocumentChanged(document.copy(sheets = updatedSheets))
    }

    fun addColumn() {
        val updatedSheets = sheets.toMutableList()
        val newRows = currentSheet.rows.map { it + "" }
        updatedSheets[safeSheetIdx] = currentSheet.copy(rows = newRows)
        onDocumentChanged(document.copy(sheets = updatedSheets))
    }

    val colCount = (currentSheet.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val horizScroll = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Sheet Tabs + Add Row/Col
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sheets.forEachIndexed { idx, s ->
                FilterChip(
                    selected = idx == safeSheetIdx,
                    onClick = { activeSheetIdx = idx },
                    label = { Text(s.name) }
                )
            }
            OutlinedButton(onClick = ::addRow, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Row")
            }
            OutlinedButton(onClick = ::addColumn, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Col")
            }
        }

        // Formula / Cell Editor Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${colName(selectedCol)}${selectedRow + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            OutlinedTextField(
                value = cellEditorText,
                onValueChange = {
                    cellEditorText = it
                    updateCell(selectedRow, selectedCol, it)
                },
                placeholder = { Text("Edit cell value...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Grid
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizScroll)
                .padding(8.dp)
        ) {
            // Column Letter Header
            item {
                Row {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#", style = MaterialTheme.typography.labelSmall)
                    }
                    for (c in 0 until colCount) {
                        Box(
                            modifier = Modifier
                                .width(126.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = colName(c),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            itemsIndexed(currentSheet.rows) { rIdx, row ->
                Row {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${rIdx + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                    for (cIdx in 0 until colCount) {
                        val isSelected = rIdx == selectedRow && cIdx == selectedCol
                        val cellText = row.getOrElse(cIdx) { "" }
                        Box(
                            modifier = Modifier
                                .width(126.dp)
                                .height(40.dp)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        rIdx == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                                .clickable {
                                    selectedRow = rIdx
                                    selectedCol = cIdx
                                    cellEditorText = cellText
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (rIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresentationDeckView(
    document: UnifiedDocument,
    onDocumentChanged: (UnifiedDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val slides = document.asSlides()
    var activeIdx by remember(document.title) { mutableIntStateOf(0) }
    val safeIdx = activeIdx.coerceIn(0, (slides.size - 1).coerceAtLeast(0))
    val currentSlide = slides.getOrNull(safeIdx) ?: SlideData("Slide 1", emptyList())
    var showEditSlideDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 16:9 Slide Canvas Stage
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SLIDE ${safeIdx + 1} / ${slides.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2DD4BF)
                    )
                    Row {
                        IconButton(onClick = { showEditSlideDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit slide", tint = Color.White)
                        }
                        if (slides.size > 1) {
                            IconButton(onClick = {
                                val next = slides.toMutableList().apply { removeAt(safeIdx) }
                                activeIdx = safeIdx.coerceAtMost(next.lastIndex)
                                onDocumentChanged(document.copy(slides = next))
                            }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete slide", tint = Color(0xFFFB7185))
                            }
                        }
                    }
                }
                Text(
                    text = currentSlide.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                currentSlide.bullets.forEach { bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Color(0xFFFBBF24), style = MaterialTheme.typography.bodyLarge)
                        Text(bullet, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Speaker Notes Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Speaker Notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = currentSlide.notes.ifBlank { "No speaker notes for this slide. Tap Edit to add notes." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Slide Filmstrip + Add Slide
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(slides) { idx, slide ->
                val selected = idx == safeIdx
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .width(128.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { activeIdx = idx }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val next = slides + SlideData("New Slide ${slides.size + 1}", listOf("Key point"), "")
                        onDocumentChanged(document.copy(slides = next))
                        activeIdx = next.lastIndex
                    },
                    modifier = Modifier.height(68.dp)
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Slide")
                }
            }
        }
    }

    if (showEditSlideDialog) {
        var titleDraft by remember { mutableStateOf(currentSlide.title) }
        var bulletsDraft by remember { mutableStateOf(currentSlide.bullets.joinToString("\n")) }
        var notesDraft by remember { mutableStateOf(currentSlide.notes) }

        AlertDialog(
            onDismissRequest = { showEditSlideDialog = false },
            title = { Text("Edit Slide ${safeIdx + 1}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        label = { Text("Slide Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bulletsDraft,
                        onValueChange = { bulletsDraft = it },
                        label = { Text("Bullets (one per line)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it },
                        label = { Text("Speaker Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSlideDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = slides.toMutableList()
                    updated[safeIdx] = SlideData(
                        title = titleDraft.ifBlank { "Slide ${safeIdx + 1}" },
                        bullets = bulletsDraft.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        notes = notesDraft
                    )
                    onDocumentChanged(document.copy(slides = updated))
                    showEditSlideDialog = false
                }) {
                    Text("Apply")
                }
            }
        )
    }
}
