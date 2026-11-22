package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.engine.DocumentEngine
import com.example.engine.PdfCodec
import com.example.engine.PdfPaperSize
import com.example.engine.SecurityUtils
import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.FileCategory
import com.example.model.MediaFormat
import com.example.model.SheetData
import com.example.model.SlideData
import com.example.model.SortOption
import com.example.model.StorageOrigin
import com.example.model.UnifiedDocument
import com.example.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class VaultRepository(
    private val context: Context,
    private val dao: VaultDao
) {
    val vaultRoot: File by lazy {
        File(context.filesDir, "vault").apply { mkdirs() }
    }

    val recentFilesFlow: Flow<List<VaultItem>> = dao.observeRecentFiles().map { list ->
        list.mapNotNull { e ->
            if (e.filePath != null && !File(e.filePath).exists()) return@mapNotNull null
            VaultItem(
                id = e.id,
                name = e.name,
                isDirectory = false,
                sizeBytes = e.sizeBytes,
                lastModified = e.lastOpenedAt,
                extension = e.extension,
                mimeType = e.mimeType,
                category = categorizeExtension(e.extension, false),
                origin = if (e.origin == StorageOrigin.VAULT.name) StorageOrigin.VAULT else StorageOrigin.DEVICE_SAF,
                filePath = e.filePath,
                contentUri = e.uriString?.let { Uri.parse(it) },
                isPinned = e.isPinned
            )
        }
    }

    val mountedFoldersFlow: Flow<List<MountedFolderEntity>> = dao.observeMountedFolders()

    suspend fun ensureSeededWorkspace() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("folio_seed", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded_v1", false)) return@withContext

        val docsDir = File(vaultRoot, "Documents").apply { mkdirs() }
        val mediaDir = File(vaultRoot, "Media").apply { mkdirs() }
        File(vaultRoot, "Converted").mkdirs()

        // 1. Sample DOCX Report
        val docxDoc = UnifiedDocument(
            title = "Offline Architecture Brief",
            sourceFormat = DocFormat.DOCX,
            kind = DocumentKind.PROSE,
            blocks = listOf(
                DocBlock.Heading(1, "FolioFlow Offline Architecture"),
                DocBlock.Paragraph("Every document parser, editor, converter, and media player runs 100% natively on-device with zero network permissions."),
                DocBlock.Heading(2, "Supported Document Interchange"),
                DocBlock.BulletItem("PDF reading via Android PdfRenderer & native PdfDocument generation"),
                DocBlock.BulletItem("DOCX, XLSX, and PPTX streaming OpenXML engine with Zip-Slip protection"),
                DocBlock.BulletItem("Markdown, CSV, JSON, HTML, XML, RTF, and Plain Text live editing"),
                DocBlock.TableBlock(
                    listOf(
                        listOf("Format", "Read", "Edit", "Convert"),
                        listOf("PDF", "Native", "Text/Notes", "To DOCX/TXT/MD/HTML"),
                        listOf("DOCX", "Full", "Block/Rich", "To PDF/MD/HTML/PPTX"),
                        listOf("XLSX", "Full", "Cell Grid", "To CSV/PDF/JSON/HTML"),
                        listOf("PPTX", "Full", "Slide Deck", "To PDF/DOCX/MD")
                    )
                )
            )
        )
        FileOutputStream(File(docsDir, "Architecture_Brief.docx")).use {
            DocumentEngine.writeDocument(docxDoc, DocFormat.DOCX, it)
        }

        // 2. Sample PDF Guide
        FileOutputStream(File(docsDir, "Privacy_Manifesto.pdf")).use {
            PdfCodec.writePdf(docxDoc.copy(title = "FolioFlow Privacy Manifesto"), it, PdfPaperSize.A4)
        }

        // 3. Sample XLSX Workbook
        val xlsxDoc = UnifiedDocument(
            title = "Offline Budget Tracker",
            sourceFormat = DocFormat.XLSX,
            kind = DocumentKind.SPREADSHEET,
            sheets = listOf(
                SheetData(
                    name = "Q4 Summary",
                    rows = listOf(
                        listOf("Category", "Planned", "Actual", "Status"),
                        listOf("Cloud Servers", "0", "0", "100% Offline"),
                        listOf("Hardware Storage", "120", "98", "Under Budget"),
                        listOf("Media Codecs", "0", "0", "Native Media3"),
                        listOf("Security Audit", "450", "450", "Verified")
                    )
                ),
                SheetData(
                    name = "Formats",
                    rows = listOf(
                        listOf("Extension", "Category"),
                        listOf(".pdf", "Document"),
                        listOf(".docx", "Word Processing"),
                        listOf(".xlsx", "Spreadsheet"),
                        listOf(".pptx", "Presentation")
                    )
                )
            )
        )
        FileOutputStream(File(docsDir, "Budget_Tracker.xlsx")).use {
            DocumentEngine.writeDocument(xlsxDoc, DocFormat.XLSX, it)
        }

        // 4. Sample PPTX Presentation
        val pptxDoc = UnifiedDocument(
            title = "Studio Capabilities Deck",
            sourceFormat = DocFormat.PPTX,
            kind = DocumentKind.PRESENTATION,
            slides = listOf(
                SlideData(
                    title = "Welcome to FolioFlow",
                    bullets = listOf(
                        "Universal Offline Document & Media Studio",
                        "Zero Internet Permissions — Maximum Privacy",
                        "Low-Memory Streaming Architecture"
                    ),
                    notes = "Opening slide introducing core offline guarantees."
                ),
                SlideData(
                    title = "Interchange Engine",
                    bullets = listOf(
                        "Convert PDF ↔ DOCX ↔ MD ↔ HTML ↔ TXT",
                        "Transform XLSX ↔ CSV ↔ JSON ↔ PDF",
                        "Export PPTX decks straight to 16:9 PDF"
                    ),
                    notes = "Highlight seamless cross-format conversion."
                ),
                SlideData(
                    title = "Universal Media Studio",
                    bullets = listOf(
                        "Image Viewer, EXIF Stripper & PNG/JPG/WebP Converter",
                        "Media3 ExoPlayer for MP3, M4A, OGG, FLAC, WAV, MP4, MKV, WEBM"
                    ),
                    notes = "Hardware-accelerated local playback."
                )
            )
        )
        FileOutputStream(File(docsDir, "Capabilities_Deck.pptx")).use {
            DocumentEngine.writeDocument(pptxDoc, DocFormat.PPTX, it)
        }

        // 5. Sample Markdown & CSV
        File(docsDir, "Quick_Notes.md").writeText(
            """
            # Workspace Notes

            Tap **Edit** to modify this Markdown document, or tap **Convert** to export it directly to **PDF**, **DOCX**, **PPTX**, or **HTML**.

            - 100% local execution
            - SHA-256 file integrity checks
            - Zero telemetry or analytics
            """.trimIndent()
        )
        File(docsDir, "Formats_Matrix.csv").writeText(
            "Format,Type,Read,Edit,Export\nPDF,Document,Yes,Yes,Yes\nDOCX,Document,Yes,Yes,Yes\nXLSX,Spreadsheet,Yes,Yes,Yes\nPPTX,Presentation,Yes,Yes,Yes\nMP4/MP3/OGG,Media,Yes,Meta,Yes"
        )

        // 6. Sample PNG Visual Card & Synthesized Offline WAV Audio Chime
        createSamplePng(File(mediaDir, "Studio_Palette_Card.png"))
        createSampleWavChime(File(mediaDir, "Ambient_Studio_Chime.wav"))

        prefs.edit().putBoolean("seeded_v1", true).apply()
    }

    private fun createSamplePng(dest: File) {
        val w = 720
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.parseColor("#0B111A"), Color.parseColor("#0F766E"), Color.parseColor("#1E293B")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(w * 0.5f, h * 0.5f, 120f, accent)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("FolioFlow Offline Media", w * 0.5f, h * 0.5f + 12f, textPaint)
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun createSampleWavChime(dest: File) {
        val sampleRate = 22050
        val durationSec = 3
        val numSamples = sampleRate * durationSec
        val pcmBytes = ByteArray(numSamples * 2)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = kotlin.math.exp(-t * 1.1) * (1.0 - kotlin.math.exp(-t * 25.0))
            val wave = 0.5 * sin(2.0 * PI * 440.0 * t) +
                0.3 * sin(2.0 * PI * 554.37 * t) +
                0.2 * sin(2.0 * PI * 659.25 * t)
            val sample = (wave * env * 20000.0).toInt().coerceIn(-32768, 32767).toShort()
            bb.putShort(sample)
        }
        val dataSize = pcmBytes.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray())
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // Byte rate
            putShort(2) // Block align
            putShort(16) // Bits per sample
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
        FileOutputStream(dest).use { out ->
            out.write(header)
            out.write(pcmBytes)
        }
    }

    suspend fun listVaultDirectory(
        directory: File,
        categoryFilter: FileCategory = FileCategory.ALL,
        sortOption: SortOption = SortOption.DATE_DESC,
        query: String = ""
    ): List<VaultItem> = withContext(Dispatchers.IO) {
        val files = directory.listFiles()?.toList() ?: emptyList()
        val mapped = files.filter { !it.name.startsWith(".tmp_") }.map { file ->
            val ext = if (file.isDirectory) "" else file.extension.lowercase()
            VaultItem(
                id = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) file.listFiles()?.size?.toLong() ?: 0L else file.length(),
                lastModified = file.lastModified(),
                extension = ext,
                mimeType = guessMimeType(ext, file.isDirectory),
                category = categorizeExtension(ext, file.isDirectory),
                origin = StorageOrigin.VAULT,
                filePath = file.absolutePath
            )
        }
        applyFilterAndSort(mapped, categoryFilter, sortOption, query)
    }

    suspend fun listAllVaultMedia(category: FileCategory? = null): List<VaultItem> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<VaultItem>()
            fun walk(dir: File) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) {
                        walk(f)
                    } else if (!f.name.startsWith(".tmp_")) {
                        val ext = f.extension.lowercase()
                        val cat = categorizeExtension(ext, false)
                        val matches = if (category != null) cat == category
                        else cat == FileCategory.IMAGE || cat == FileCategory.AUDIO || cat == FileCategory.VIDEO
                        if (matches) {
                            all.add(
                                VaultItem(
                                    id = f.absolutePath,
                                    name = f.name,
                                    isDirectory = false,
                                    sizeBytes = f.length(),
                                    lastModified = f.lastModified(),
                                    extension = ext,
                                    mimeType = guessMimeType(ext, false),
                                    category = cat,
                                    origin = StorageOrigin.VAULT,
                                    filePath = f.absolutePath
                                )
                            )
                        }
                    }
                }
            }
            walk(vaultRoot)
            all.sortedByDescending { it.lastModified }
        }

    suspend fun listSafTreeChildren(
        treeUri: Uri,
        parentDocumentId: String? = null,
        categoryFilter: FileCategory = FileCategory.ALL,
        sortOption: SortOption = SortOption.NAME_ASC,
        query: String = ""
    ): List<VaultItem> = withContext(Dispatchers.IO) {
        val docId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val items = mutableListOf<VaultItem>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val cId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: "Unnamed"
                val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (!cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                val modified = if (!cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cId)
                items.add(
                    VaultItem(
                        id = childUri.toString(),
                        name = name,
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = modified,
                        extension = ext,
                        mimeType = mime,
                        category = categorizeExtension(ext, isDir),
                        origin = StorageOrigin.DEVICE_SAF,
                        contentUri = childUri
                    )
                )
            }
        }
        applyFilterAndSort(items, categoryFilter, sortOption, query)
    }

    private fun applyFilterAndSort(
        items: List<VaultItem>,
        categoryFilter: FileCategory,
        sortOption: SortOption,
        query: String
    ): List<VaultItem> {
        val filtered = items.filter { item ->
            val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
            val matchesCat = categoryFilter == FileCategory.ALL ||
                (categoryFilter == FileCategory.FOLDER && item.isDirectory) ||
                item.category == categoryFilter
            matchesQuery && matchesCat
        }
        val dirs = filtered.filter { it.isDirectory }
        val files = filtered.filter { !it.isDirectory }
        val comparator: Comparator<VaultItem> = when (sortOption) {
            SortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.DATE_DESC -> compareByDescending { it.lastModified }
            SortOption.SIZE_DESC -> compareByDescending { it.sizeBytes }
            SortOption.TYPE -> compareBy<VaultItem> { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        return dirs.sortedBy { it.name.lowercase() } + files.sortedWith(comparator)
    }

    suspend fun mountFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val display = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Device Folder" }
        dao.upsertMountedFolder(MountedFolderEntity(treeUri.toString(), display))
    }

    suspend fun unmountFolder(treeUri: String) = withContext(Dispatchers.IO) {
        dao.removeMountedFolder(treeUri)
    }

    suspend fun resolveUriAsVaultItem(uri: Uri, importIntoVault: Boolean = false): VaultItem =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            var displayName = "imported_file"
            var sizeBytes = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
            val safeName = SecurityUtils.sanitizeFileName(displayName)
            val ext = safeName.substringAfterLast('.', "").lowercase()
            val mime = context.contentResolver.getType(uri) ?: guessMimeType(ext, false)

            if (importIntoVault) {
                val dest = uniqueFile(vaultRoot, safeName)
                DocumentEngine.copyUriToFile(context, uri, dest)
                VaultItem(
                    id = dest.absolutePath,
                    name = dest.name,
                    isDirectory = false,
                    sizeBytes = dest.length(),
                    lastModified = dest.lastModified(),
                    extension = dest.extension.lowercase(),
                    mimeType = mime,
                    category = categorizeExtension(dest.extension.lowercase(), false),
                    origin = StorageOrigin.VAULT,
                    filePath = dest.absolutePath
                )
            } else {
                VaultItem(
                    id = uri.toString(),
                    name = safeName,
                    isDirectory = false,
                    sizeBytes = sizeBytes,
                    lastModified = System.currentTimeMillis(),
                    extension = ext,
                    mimeType = mime,
                    category = categorizeExtension(ext, false),
                    origin = StorageOrigin.DEVICE_SAF,
                    contentUri = uri
                )
            }
        }

    suspend fun createFolder(parentDir: File, name: String): File = withContext(Dispatchers.IO) {
        val folder = uniqueFile(parentDir, SecurityUtils.sanitizeFileName(name))
        folder.mkdirs()
        folder
    }

    suspend fun createBlankDocument(parentDir: File, baseName: String, format: DocFormat): VaultItem =
        withContext(Dispatchers.IO) {
            val cleanBase = SecurityUtils.sanitizeFileName(baseName.substringBeforeLast('.'))
            val target = uniqueFile(parentDir, "$cleanBase.${format.extension}")
            val templateDoc = when (format.category) {
                FileCategory.SPREADSHEET -> UnifiedDocument(
                    title = cleanBase,
                    sourceFormat = format,
                    kind = DocumentKind.SPREADSHEET,
                    sheets = listOf(
                        SheetData(
                            "Sheet1",
                            listOf(
                                listOf("Item", "Value", "Notes"),
                                listOf("Sample A", "100", ""),
                                listOf("Sample B", "200", "")
                            )
                        )
                    )
                )
                FileCategory.PRESENTATION -> UnifiedDocument(
                    title = cleanBase,
                    sourceFormat = format,
                    kind = DocumentKind.PRESENTATION,
                    slides = listOf(
                        SlideData(cleanBase, listOf("First key point", "Second key point"), "")
                    )
                )
                else -> UnifiedDocument(
                    title = cleanBase,
                    sourceFormat = format,
                    kind = DocumentKind.PROSE,
                    blocks = listOf(
                        DocBlock.Heading(1, cleanBase),
                        DocBlock.Paragraph("Start writing offline in $cleanBase...")
                    ),
                    rawText = "$cleanBase\n\nStart writing offline..."
                )
            }
            FileOutputStream(target).use { DocumentEngine.writeDocument(templateDoc, format, it) }
            VaultItem(
                id = target.absolutePath,
                name = target.name,
                isDirectory = false,
                sizeBytes = target.length(),
                lastModified = target.lastModified(),
                extension = format.extension,
                mimeType = format.mimeType,
                category = format.category,
                origin = StorageOrigin.VAULT,
                filePath = target.absolutePath
            )
        }

    suspend fun renameVaultItem(item: VaultItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = item.filePath?.let { File(it) } ?: return@withContext false
        val safe = SecurityUtils.sanitizeFileName(newName)
        val dest = File(file.parentFile, safe)
        if (dest.exists()) return@withContext false
        val ok = file.renameTo(dest)
        if (ok) dao.deleteRecent(item.id)
        ok
    }

    suspend fun duplicateVaultItem(item: VaultItem): VaultItem? = withContext(Dispatchers.IO) {
        val file = item.filePath?.let { File(it) } ?: return@withContext null
        if (file.isDirectory) return@withContext null
        val base = file.nameWithoutExtension
        val ext = file.extension
        val destName = if (ext.isNotEmpty()) "${base}_copy.$ext" else "${base}_copy"
        val dest = uniqueFile(file.parentFile ?: vaultRoot, destName)
        file.copyTo(dest, overwrite = false)
        VaultItem(
            id = dest.absolutePath,
            name = dest.name,
            isDirectory = false,
            sizeBytes = dest.length(),
            lastModified = dest.lastModified(),
            extension = ext.lowercase(),
            mimeType = item.mimeType,
            category = item.category,
            origin = StorageOrigin.VAULT,
            filePath = dest.absolutePath
        )
    }

    suspend fun deleteVaultItem(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        val ok = when {
            item.filePath != null -> File(item.filePath).deleteRecursively()
            item.contentUri != null -> try {
                DocumentsContract.deleteDocument(context.contentResolver, item.contentUri)
            } catch (_: Exception) {
                false
            }
            else -> false
        }
        if (ok) dao.deleteRecent(item.id)
        ok
    }

    suspend fun recordOpened(item: VaultItem) {
        if (item.isDirectory) return
        val existingPinned = dao.isPinned(item.id) ?: item.isPinned
        dao.upsertRecent(
            RecentFileEntity(
                id = item.id,
                name = item.name,
                extension = item.extension,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                origin = item.origin.name,
                filePath = item.filePath,
                uriString = item.contentUri?.toString(),
                isPinned = existingPinned,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePin(item: VaultItem) {
        val currentlyPinned = dao.isPinned(item.id) ?: item.isPinned
        if (dao.isPinned(item.id) == null) {
            recordOpened(item.copy(isPinned = !currentlyPinned))
        }
        dao.setPinned(item.id, !currentlyPinned)
    }

    suspend fun computeSha256(item: VaultItem): String = withContext(Dispatchers.IO) {
        DocumentEngine.openInputStream(context, item).use { SecurityUtils.computeSha256(it) }
    }

    suspend fun computeVaultUsageBytes(): Long = withContext(Dispatchers.IO) {
        vaultRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun uniqueFile(parent: File, fileName: String): File {
        var candidate = File(parent, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.')
        val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "${base}_$counter$ext")
            counter++
        }
        return candidate
    }

    companion object {
        fun categorizeExtension(ext: String, isDir: Boolean): FileCategory {
            if (isDir) return FileCategory.FOLDER
            val clean = ext.lowercase().trimStart('.')
            DocFormat.fromExtension(clean)?.let { return it.category }
            MediaFormat.fromExtension(clean)?.let { return it.category }
            return FileCategory.ALL
        }

        fun guessMimeType(ext: String, isDir: Boolean): String {
            if (isDir) return DocumentsContract.Document.MIME_TYPE_DIR
            val clean = ext.lowercase().trimStart('.')
            DocFormat.fromExtension(clean)?.let { return it.mimeType }
            MediaFormat.fromExtension(clean)?.let { return it.mimeType }
            return "application/octet-stream"
        }
    }
}
