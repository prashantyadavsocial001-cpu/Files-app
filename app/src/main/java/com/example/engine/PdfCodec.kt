package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.UnifiedDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

enum class PdfPaperSize(val widthPt: Int, val heightPt: Int, val label: String) {
    A4(595, 842, "A4 (595×842 pt)"),
    LETTER(612, 792, "US Letter (612×792 pt)")
}

object PdfCodec {

    fun parsePdfFile(title: String, file: File): UnifiedDocument {
        var pageCount = 1
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                }
            }
        } catch (_: Exception) {
            // Fallback to stream scan if PdfRenderer cannot open corrupted header
        } catch (_: LinkageError) {
            // Guard against JVM test environments without native PdfRenderer JNI
        }

        val extracted = extractTextBestEffort(file)
        val blocks = if (extracted.isNotBlank()) {
            extracted.split(Regex("\\r?\\n\\r?\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { DocBlock.Paragraph(it) }
        } else {
            listOf(
                DocBlock.Paragraph(
                    "Rendered visually across $pageCount page(s). Switch to Edit Text to author notes or export to another format."
                )
            )
        }

        val warnings = if (extracted.isBlank()) {
            listOf("This PDF uses custom font glyphs or scanned page images. Visual pages render natively via Android PdfRenderer.")
        } else emptyList()

        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.PDF,
            kind = DocumentKind.PROSE,
            blocks = blocks,
            rawText = extracted,
            pageCount = pageCount,
            warnings = warnings
        )
    }

    /**
     * Lightweight pure-Kotlin PDF text operator extractor (supports uncompressed and FlateDecode streams).
     * Bounded to 16 MB so memory usage remains tiny.
     */
    internal fun extractTextBestEffort(file: File): String {
        if (file.length() > 16L * 1024L * 1024L) return ""
        val rawBytes = file.readBytes()
        val latin = String(rawBytes, StandardCharsets.ISO_8859_1)
        val allExtracted = StringBuilder()

        val streamMarker = "stream"
        val endStreamMarker = "endstream"
        var searchIdx = 0
        while (searchIdx < latin.length) {
            val sIdx = latin.indexOf(streamMarker, searchIdx)
            if (sIdx == -1) break
            var dataStart = sIdx + streamMarker.length
            if (dataStart < latin.length && latin[dataStart] == '\r') dataStart++
            if (dataStart < latin.length && latin[dataStart] == '\n') dataStart++
            val eIdx = latin.indexOf(endStreamMarker, dataStart)
            if (eIdx == -1) break

            val headerWindow = latin.substring((sIdx - 220).coerceAtLeast(0), sIdx)
            val streamSlice = rawBytes.copyOfRange(dataStart, eIdx)
            val contentText = if (headerWindow.contains("FlateDecode")) {
                inflateSafely(streamSlice)
            } else {
                String(streamSlice, StandardCharsets.ISO_8859_1)
            }
            if (contentText.contains("BT") && (contentText.contains("Tj") || contentText.contains("TJ"))) {
                val parsed = parsePdfContentOperators(contentText)
                if (parsed.isNotBlank()) {
                    allExtracted.append(parsed).append("\n\n")
                }
            }
            searchIdx = eIdx + endStreamMarker.length
        }
        return allExtracted.toString().trim()
    }

    private fun inflateSafely(compressed: ByteArray): String {
        return try {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var total = 0
            while (!inflater.finished() && total < 2 * 1024 * 1024) {
                val count = inflater.inflate(buf)
                if (count == 0) break
                out.write(buf, 0, count)
                total += count
            }
            inflater.end()
            String(out.toByteArray(), StandardCharsets.ISO_8859_1)
        } catch (_: Exception) {
            ""
        }
    }

    private fun parsePdfContentOperators(ops: String): String {
        val sb = StringBuilder()
        var inBt = false
        var i = 0
        while (i < ops.length) {
            when {
                ops.startsWith("BT", i) && (i == 0 || !ops[i - 1].isLetterOrDigit()) -> {
                    inBt = true
                    i += 2
                }
                ops.startsWith("ET", i) && (i == 0 || !ops[i - 1].isLetterOrDigit()) -> {
                    inBt = false
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                    i += 2
                }
                inBt && ops[i] == '(' -> {
                    val end = readPdfLiteralString(ops, i, sb)
                    i = end
                }
                inBt && (ops.startsWith("T*", i) || ops.startsWith("Td", i) || ops.startsWith("TD", i)) -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith(" ")) sb.append(' ')
                    i += 2
                }
                else -> i++
            }
        }
        return sb.toString()
            .replace(Regex(" +"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun readPdfLiteralString(src: String, start: Int, out: StringBuilder): Int {
        var i = start + 1
        var depth = 1
        val chunk = StringBuilder()
        while (i < src.length && depth > 0) {
            val c = src[i]
            when (c) {
                '\\' -> {
                    if (i + 1 < src.length) {
                        when (val n = src[i + 1]) {
                            'n' -> chunk.append('\n')
                            'r' -> chunk.append('\r')
                            't' -> chunk.append('\t')
                            '(', ')', '\\' -> chunk.append(n)
                            else -> chunk.append(n)
                        }
                        i += 2
                    } else i++
                }
                '(' -> {
                    depth++
                    chunk.append(c)
                    i++
                }
                ')' -> {
                    depth--
                    if (depth > 0) chunk.append(c)
                    i++
                }
                else -> {
                    chunk.append(c)
                    i++
                }
            }
        }
        val text = chunk.toString()
        if (text.any { it.code in 32..126 }) {
            out.append(text)
        }
        return i
    }

    fun writePdf(
        doc: UnifiedDocument,
        out: OutputStream,
        paperSize: PdfPaperSize = PdfPaperSize.A4
    ) {
        val pdf = PdfDocument()
        try {
            when (doc.kind) {
                DocumentKind.PRESENTATION -> writeSlidesPdf(pdf, doc)
                DocumentKind.SPREADSHEET -> writeSpreadsheetPdf(pdf, doc, paperSize)
                DocumentKind.PROSE -> writeProsePdf(pdf, doc, paperSize)
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun writeProsePdf(pdf: PdfDocument, doc: UnifiedDocument, paperSize: PdfPaperSize) {
        val pageW = paperSize.widthPt
        val pageH = paperSize.heightPt
        val margin = 44f
        val contentW = (pageW - margin * 2).toInt()
        val maxY = pageH - margin - 20f

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas
        var y = margin

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
        }

        fun finishAndStartNewPage() {
            canvas.drawText("${doc.title}  •  Page $pageNum", margin, pageH - 20f, footerPaint)
            pdf.finishPage(page)
            pageNum++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun drawWrappedText(text: String, paint: TextPaint, indent: Float = 0f, lineSpacing: Float = 1.25f) {
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (contentW - indent).toInt().coerceAtLeast(100))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacing)
                .build()
            if (y + layout.height > maxY && y > margin + 20f) {
                finishAndStartNewPage()
            }
            canvas.save()
            canvas.translate(margin + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F766E")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 14.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 11f
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }

        // Document title header
        drawWrappedText(doc.title, h1Paint)
        y += 8f
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        canvas.drawLine(margin, y, pageW - margin, y, linePaint)
        y += 14f

        for (block in doc.asProseBlocks()) {
            when (block) {
                is DocBlock.Heading -> {
                    y += 6f
                    val p = if (block.level == 1) h1Paint else h2Paint
                    drawWrappedText(block.text, p)
                    y += 6f
                }
                is DocBlock.Paragraph -> {
                    val p = TextPaint(bodyPaint).apply {
                        if (block.isBold && block.isItalic) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                        else if (block.isBold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        else if (block.isItalic) typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    }
                    drawWrappedText(block.text, p)
                    y += 8f
                }
                is DocBlock.BulletItem -> {
                    canvas.drawText("•", margin + 6f, y + 11f, bodyPaint)
                    drawWrappedText(block.text, bodyPaint, indent = 18f)
                    y += 5f
                }
                is DocBlock.CodeBlock -> {
                    val lines = block.text.lines().take(60)
                    for (line in lines) {
                        drawWrappedText(line.ifEmpty { " " }, codePaint, indent = 8f, lineSpacing = 1.1f)
                    }
                    y += 8f
                }
                is DocBlock.TableBlock -> {
                    val rows = block.rows
                    if (rows.isNotEmpty()) {
                        val cols = rows.maxOf { it.size }.coerceIn(1, 8)
                        val colW = contentW.toFloat() / cols
                        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1E293B")
                            textSize = 9.5f
                        }
                        val headerBg = Paint().apply { color = Color.parseColor("#0F766E") }
                        val headerTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT_BOLD
                        }
                        val border = Paint().apply {
                            color = Color.parseColor("#CBD5E1")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }
                        rows.forEachIndexed { rIdx, row ->
                            if (y + 22f > maxY) finishAndStartNewPage()
                            if (rIdx == 0) {
                                canvas.drawRect(margin, y, margin + contentW, y + 20f, headerBg)
                            }
                            canvas.drawRect(margin, y, margin + contentW, y + 20f, border)
                            for (c in 0 until cols) {
                                val txt = row.getOrElse(c) { "" }.take(24)
                                canvas.drawText(
                                    txt,
                                    margin + c * colW + 4f,
                                    y + 13.5f,
                                    if (rIdx == 0) headerTxt else cellPaint
                                )
                            }
                            y += 20f
                        }
                        y += 10f
                    }
                }
                DocBlock.Divider -> {
                    if (y + 12f > maxY) finishAndStartNewPage()
                    canvas.drawLine(margin, y + 4f, pageW - margin, y + 4f, linePaint)
                    y += 12f
                }
            }
        }
        canvas.drawText("${doc.title}  •  Page $pageNum", margin, pageH - 20f, footerPaint)
        pdf.finishPage(page)
    }

    private fun writeSpreadsheetPdf(pdf: PdfDocument, doc: UnifiedDocument, paperSize: PdfPaperSize) {
        val pageW = paperSize.heightPt // Landscape for wide spreadsheets!
        val pageH = paperSize.widthPt
        val margin = 36f
        val contentW = pageW - margin * 2
        val maxY = pageH - margin - 18f

        var pageNum = 1
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerBg = Paint().apply { color = Color.parseColor("#0F766E") }
        val zebraBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        val headerTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val cellTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
        }
        val border = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        for (sheet in doc.asSheets()) {
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            var y = margin
            canvas.drawText("${doc.title} — ${sheet.name}", margin, y + 12f, titlePaint)
            y += 24f

            val maxCols = (sheet.rows.maxOfOrNull { it.size } ?: 1).coerceIn(1, 10)
            val colW = contentW / maxCols
            val maxChars = (colW / 5.5f).toInt().coerceAtLeast(6)

            sheet.rows.forEachIndexed { rIdx, row ->
                if (y + 20f > maxY) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    y = margin
                }
                when {
                    rIdx == 0 -> canvas.drawRect(margin, y, margin + contentW, y + 20f, headerBg)
                    rIdx % 2 == 0 -> canvas.drawRect(margin, y, margin + contentW, y + 20f, zebraBg)
                }
                canvas.drawRect(margin, y, margin + contentW, y + 20f, border)
                for (c in 0 until maxCols) {
                    val raw = row.getOrElse(c) { "" }
                    val txt = if (raw.length > maxChars) raw.take(maxChars - 1) + "…" else raw
                    canvas.drawText(txt, margin + c * colW + 4f, y + 13.5f, if (rIdx == 0) headerTxt else cellTxt)
                }
                y += 20f
            }
            pdf.finishPage(page)
            pageNum++
        }
    }

    private fun writeSlidesPdf(pdf: PdfDocument, doc: UnifiedDocument) {
        val pageW = 842
        val pageH = 474 // 16:9 widescreen slide PDF
        val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
        val accentPaint = Paint().apply { color = Color.parseColor("#0F766E") }
        val cardPaint = Paint().apply { color = Color.parseColor("#1E293B") }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bulletPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 15f
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 11f
        }

        val slides = doc.asSlides()
        slides.forEachIndexed { idx, slide ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create())
            val canvas = page.canvas
            canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)
            canvas.drawRect(0f, 0f, 12f, pageH.toFloat(), accentPaint)
            canvas.drawRoundRect(36f, 32f, pageW - 36f, pageH - 44f, 18f, 18f, cardPaint)

            val titleLayout = StaticLayout.Builder
                .obtain(slide.title, 0, slide.title.length, titlePaint, pageW - 120)
                .build()
            canvas.save()
            canvas.translate(60f, 56f)
            titleLayout.draw(canvas)
            canvas.restore()

            var y = 66f + titleLayout.height + 18f
            for (b in slide.bullets) {
                if (y > pageH - 80f) break
                val line = "•  $b"
                val bLayout = StaticLayout.Builder
                    .obtain(line, 0, line.length, bulletPaint, pageW - 140)
                    .build()
                canvas.save()
                canvas.translate(68f, y)
                bLayout.draw(canvas)
                canvas.restore()
                y += bLayout.height + 10f
            }

            canvas.drawText("${doc.title}  •  Slide ${idx + 1} / ${slides.size}", 60f, pageH - 18f, metaPaint)
            pdf.finishPage(page)
        }
    }

    fun writeImageBitmapToPdf(bitmap: Bitmap, title: String, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            val maxW = 595
            val maxH = 842
            val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height, 1f)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(100)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(100)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
            val dest = android.graphics.Rect(0, 0, w, h)
            page.canvas.drawBitmap(bitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }
}
