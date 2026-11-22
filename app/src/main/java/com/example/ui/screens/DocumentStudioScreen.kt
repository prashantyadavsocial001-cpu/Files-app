package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.UnifiedDocument
import com.example.model.VaultItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.DocLoadState

private enum class DocViewMode { VISUAL_PDF, READ, EDIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentStudioScreen(
    item: VaultItem,
    loadState: DocLoadState,
    editorFontSizeSp: Int,
    onBack: () -> Unit,
    onUpdateDocument: (UnifiedDocument) -> Unit,
    onSaveDocument: () -> Unit,
    onOpenConverter: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val isPdf = item.docFormat == DocFormat.PDF
    var mode by remember(item.id) {
        mutableStateOf(if (isPdf) DocViewMode.VISUAL_PDF else DocViewMode.READ)
    }
    var showFindReplace by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val loaded = loadState as? DocLoadState.Success
                        val sub = if (loaded != null) {
                            val dirty = if (loaded.isModified) " • Unsaved" else ""
                            "${item.extension.uppercase()} • ${loaded.document.wordCount} words$dirty"
                        } else {
                            item.extension.uppercase()
                        }
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("doc_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val loaded = loadState as? DocLoadState.Success
                    if (loaded != null && loaded.document.kind == DocumentKind.PROSE && mode == DocViewMode.EDIT) {
                        IconButton(onClick = { showFindReplace = true }) {
                            Icon(Icons.Filled.FindReplace, contentDescription = "Find & Replace")
                        }
                    }
                    if (loaded != null && (item.docFormat?.canWrite == true)) {
                        IconButton(
                            onClick = onSaveDocument,
                            modifier = Modifier.testTag("doc_save")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Save document",
                                tint = if (loaded.isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onOpenConverter(item) },
                        modifier = Modifier.testTag("doc_convert")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Convert format")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (loadState) {
                DocLoadState.Idle, DocLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DocLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = loadState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is DocLoadState.Success -> {
                    val doc = loadState.document
                    // Mode Switcher Bar
                    if (doc.kind == DocumentKind.PROSE || isPdf) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isPdf) {
                                FilterChip(
                                    selected = mode == DocViewMode.VISUAL_PDF,
                                    onClick = { mode = DocViewMode.VISUAL_PDF },
                                    label = { Text("Visual PDF") }
                                )
                            }
                            FilterChip(
                                selected = mode == DocViewMode.READ,
                                onClick = { mode = DocViewMode.READ },
                                label = { Text("Reader") }
                            )
                            FilterChip(
                                selected = mode == DocViewMode.EDIT,
                                onClick = { mode = DocViewMode.EDIT },
                                label = { Text("Edit Text") }
                            )
                        }
                    }

                    // Warnings Banner (e.g. legacy OLE2 or scanned PDF)
                    if (doc.warnings.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = doc.warnings.first(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    when {
                        isPdf && mode == DocViewMode.VISUAL_PDF -> {
                            PdfVisualViewer(
                                item = item,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        doc.kind == DocumentKind.SPREADSHEET -> {
                            SpreadsheetStudioView(
                                document = doc,
                                onDocumentChanged = onUpdateDocument,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        doc.kind == DocumentKind.PRESENTATION -> {
                            PresentationDeckView(
                                document = doc,
                                onDocumentChanged = onUpdateDocument,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        mode == DocViewMode.READ -> {
                            ProseReaderView(
                                doc = doc,
                                fontSizeSp = editorFontSizeSp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> {
                            ProseEditorView(
                                doc = doc,
                                fontSizeSp = editorFontSizeSp,
                                onUpdate = onUpdateDocument,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFindReplace && loadState is DocLoadState.Success) {
        var findStr by remember { mutableStateOf("") }
        var replaceStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFindReplace = false },
            title = { Text("Find & Replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findStr,
                        onValueChange = { findStr = it },
                        label = { Text("Find") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replaceStr,
                        onValueChange = { replaceStr = it },
                        label = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplace = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    if (findStr.isNotEmpty()) {
                        val replacedText = loadState.document.toPlainText().replace(findStr, replaceStr)
                        val newBlocks = textToBlocks(replacedText)
                        onUpdateDocument(
                            loadState.document.copy(blocks = newBlocks, rawText = replacedText)
                        )
                    }
                    showFindReplace = false
                }) {
                    Text("Replace All")
                }
            }
        )
    }
}

@Composable
private fun ProseReaderView(
    doc: UnifiedDocument,
    fontSizeSp: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(doc.asProseBlocks()) { block ->
            when (block) {
                is DocBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> (fontSizeSp + 8).sp
                        2 -> (fontSizeSp + 5).sp
                        else -> (fontSizeSp + 2).sp
                    }
                    Text(
                        text = block.text,
                        fontSize = size,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DocBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp,
                        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                is DocBlock.BulletItem -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "•",
                            fontSize = fontSizeSp.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = block.text,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                is DocBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = block.text,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = (fontSizeSp - 2).coerceAtLeast(11).sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is DocBlock.TableBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        block.rows.forEachIndexed { rIdx, row ->
                            Row(
                                modifier = Modifier.background(
                                    if (rIdx == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        text = cell,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .width(130.dp)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                DocBlock.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ProseEditorView(
    doc: UnifiedDocument,
    fontSizeSp: Int,
    onUpdate: (UnifiedDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorText by remember(doc.title, doc.rawText) {
        mutableStateOf(
            if (doc.sourceFormat == DocFormat.MD || doc.sourceFormat == DocFormat.JSON ||
                doc.sourceFormat == DocFormat.XML || doc.sourceFormat == DocFormat.HTML
            ) {
                doc.rawText.ifBlank { doc.toPlainText() }
            } else {
                doc.toPlainText()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = editorText,
            onValueChange = { next ->
                editorText = next
                onUpdate(
                    doc.copy(
                        blocks = textToBlocks(next),
                        rawText = next
                    )
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSizeSp.sp,
                fontFamily = if (doc.sourceFormat in listOf(DocFormat.JSON, DocFormat.XML, DocFormat.HTML)) {
                    JetBrainsMonoFamily
                } else {
                    MaterialTheme.typography.bodyLarge.fontFamily
                }
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("doc_prose_editor")
        )
    }
}

private fun textToBlocks(text: String): List<DocBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<DocBlock>()
    for (line in lines) {
        val t = line.trim()
        when {
            t.startsWith("# ") -> blocks.add(DocBlock.Heading(1, t.removePrefix("# ").trim()))
            t.startsWith("## ") -> blocks.add(DocBlock.Heading(2, t.removePrefix("## ").trim()))
            t.startsWith("### ") -> blocks.add(DocBlock.Heading(3, t.removePrefix("### ").trim()))
            t.startsWith("• ") || t.startsWith("- ") -> blocks.add(DocBlock.BulletItem(t.drop(2).trim()))
            t == "---" -> blocks.add(DocBlock.Divider)
            t.isNotEmpty() -> blocks.add(DocBlock.Paragraph(line))
        }
    }
    return blocks.ifEmpty { listOf(DocBlock.Paragraph("")) }
}
