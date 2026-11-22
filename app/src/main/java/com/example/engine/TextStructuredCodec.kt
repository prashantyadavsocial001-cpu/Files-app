package com.example.engine

import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.SheetData
import com.example.model.UnifiedDocument
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object TextStructuredCodec {

    fun parse(title: String, format: DocFormat, bytes: ByteArray): UnifiedDocument {
        val text = decodeUtf8WithBom(bytes)
        return when (format) {
            DocFormat.CSV -> parseDelimited(title, format, text, ',')
            DocFormat.TSV -> parseDelimited(title, format, text, '\t')
            DocFormat.MD -> parseMarkdown(title, text)
            DocFormat.HTML -> parseHtml(title, text)
            DocFormat.JSON -> parseJson(title, text)
            DocFormat.XML -> parseXmlText(title, text)
            DocFormat.RTF -> parseRtf(title, text)
            else -> parsePlainText(title, format, text)
        }
    }

    fun write(doc: UnifiedDocument, targetFormat: DocFormat, out: OutputStream) {
        val content = when (targetFormat) {
            DocFormat.TXT -> doc.toPlainText()
            DocFormat.MD -> exportMarkdown(doc)
            DocFormat.CSV -> exportDelimited(doc, ',')
            DocFormat.TSV -> exportDelimited(doc, '\t')
            DocFormat.HTML -> exportHtml(doc)
            DocFormat.JSON -> exportJson(doc)
            DocFormat.XML -> exportXml(doc)
            DocFormat.RTF -> exportRtf(doc)
            else -> doc.toPlainText()
        }
        out.write(content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeUtf8WithBom(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return String(bytes, offset, bytes.size - offset, StandardCharsets.UTF_8)
    }

    private fun parsePlainText(title: String, format: DocFormat, text: String): UnifiedDocument {
        val blocks = text.split(Regex("\\r?\\n\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { DocBlock.Paragraph(it) }
            .ifEmpty { listOf(DocBlock.Paragraph(text)) }
        return UnifiedDocument(
            title = title,
            sourceFormat = format,
            kind = DocumentKind.PROSE,
            blocks = blocks,
            rawText = text
        )
    }

    private fun parseMarkdown(title: String, text: String): UnifiedDocument {
        val blocks = mutableListOf<DocBlock>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    blocks.add(DocBlock.CodeBlock(codeLines.joinToString("\n"), lang))
                }
                trimmed == "---" || trimmed == "***" -> {
                    blocks.add(DocBlock.Divider)
                }
                trimmed.startsWith("### ") -> {
                    blocks.add(DocBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    blocks.add(DocBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                }
                trimmed.startsWith("# ") -> {
                    blocks.add(DocBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    blocks.add(DocBlock.BulletItem(trimmed.drop(2).trim()))
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    val tableRows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val rowLine = lines[i].trim()
                        val cells = rowLine.trim('|').split('|').map { it.trim() }
                        val isSeparator = cells.all { it.isNotEmpty() && it.all { ch -> ch == '-' || ch == ':' } }
                        if (!isSeparator) {
                            tableRows.add(cells)
                        }
                        i++
                    }
                    if (tableRows.isNotEmpty()) {
                        blocks.add(DocBlock.TableBlock(tableRows))
                    }
                    continue
                }
                trimmed.isNotEmpty() -> {
                    blocks.add(DocBlock.Paragraph(trimmed))
                }
            }
            i++
        }
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.MD,
            kind = DocumentKind.PROSE,
            blocks = blocks.ifEmpty { listOf(DocBlock.Paragraph(text)) },
            rawText = text
        )
    }

    private fun parseDelimited(title: String, format: DocFormat, text: String, delimiter: Char): UnifiedDocument {
        val rows = parseCsvRows(text, delimiter)
        val normalized = if (rows.isEmpty()) listOf(listOf("")) else rows
        return UnifiedDocument(
            title = title,
            sourceFormat = format,
            kind = DocumentKind.SPREADSHEET,
            sheets = listOf(SheetData(title.substringBeforeLast('.').ifBlank { "Sheet1" }, normalized)),
            rawText = text
        )
    }

    fun parseCsvRows(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            cell.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cell.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                c == '\n' -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    private fun parseHtml(title: String, html: String): UnifiedDocument {
        val cleaned = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
        val blocks = mutableListOf<DocBlock>()
        val blockRegex = Regex("(?is)<(h1|h2|h3|p|li|pre)[^>]*>(.*?)</\\1>")
        val matches = blockRegex.findAll(cleaned).toList()
        for (m in matches) {
            val tag = m.groupValues[1].lowercase()
            val inner = stripHtmlTags(m.groupValues[2]).trim()
            if (inner.isBlank()) continue
            when (tag) {
                "h1" -> blocks.add(DocBlock.Heading(1, inner))
                "h2" -> blocks.add(DocBlock.Heading(2, inner))
                "h3" -> blocks.add(DocBlock.Heading(3, inner))
                "li" -> blocks.add(DocBlock.BulletItem(inner))
                "pre" -> blocks.add(DocBlock.CodeBlock(inner))
                else -> blocks.add(DocBlock.Paragraph(inner))
            }
        }
        val plain = stripHtmlTags(cleaned).trim()
        if (blocks.isEmpty() && plain.isNotBlank()) {
            plain.lines().filter { it.isNotBlank() }.forEach { blocks.add(DocBlock.Paragraph(it.trim())) }
        }
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.HTML,
            kind = DocumentKind.PROSE,
            blocks = blocks,
            rawText = plain
        )
    }

    private fun stripHtmlTags(raw: String): String {
        return raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t]+"), " ")
    }

    private fun parseJson(title: String, text: String): UnifiedDocument {
        return try {
            val parsed = JSONTokener(text.trim()).nextValue()
            val pretty = when (parsed) {
                is JSONObject -> parsed.toString(2)
                is JSONArray -> parsed.toString(2)
                else -> text
            }
            val blocks = mutableListOf<DocBlock>()
            if (parsed is JSONArray && parsed.length() > 0 && parsed.optJSONObject(0) != null) {
                val keys = parsed.getJSONObject(0).keys().asSequence().toList()
                val tableRows = mutableListOf(keys)
                for (i in 0 until minOf(parsed.length(), 500)) {
                    val obj = parsed.optJSONObject(i) ?: continue
                    tableRows.add(keys.map { k -> obj.opt(k)?.toString() ?: "" })
                }
                blocks.add(DocBlock.TableBlock(tableRows))
            }
            blocks.add(DocBlock.CodeBlock(pretty, "json"))
            UnifiedDocument(
                title = title,
                sourceFormat = DocFormat.JSON,
                kind = DocumentKind.PROSE,
                blocks = blocks,
                rawText = pretty
            )
        } catch (_: Exception) {
            UnifiedDocument(
                title = title,
                sourceFormat = DocFormat.JSON,
                kind = DocumentKind.PROSE,
                blocks = listOf(DocBlock.CodeBlock(text, "json")),
                rawText = text,
                warnings = listOf("JSON syntax could not be formatted; opened in raw mode.")
            )
        }
    }

    private fun parseXmlText(title: String, text: String): UnifiedDocument {
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.XML,
            kind = DocumentKind.PROSE,
            blocks = listOf(DocBlock.CodeBlock(text, "xml")),
            rawText = text
        )
    }

    private fun parseRtf(title: String, rtf: String): UnifiedDocument {
        val sb = StringBuilder()
        var depth = 0
        var skipGroupDepth = Int.MAX_VALUE
        var i = 0
        while (i < rtf.length) {
            val c = rtf[i]
            when (c) {
                '{' -> {
                    depth++
                    val lookahead = rtf.substring(i + 1, minOf(i + 18, rtf.length))
                    if (lookahead.startsWith("\\fonttbl") ||
                        lookahead.startsWith("\\colortbl") ||
                        lookahead.startsWith("\\stylesheet") ||
                        lookahead.startsWith("\\info") ||
                        lookahead.startsWith("\\*")
                    ) {
                        if (skipGroupDepth == Int.MAX_VALUE) skipGroupDepth = depth
                    }
                }
                '}' -> {
                    if (depth == skipGroupDepth) skipGroupDepth = Int.MAX_VALUE
                    depth--
                }
                '\\' -> {
                    if (depth < skipGroupDepth && i + 1 < rtf.length) {
                        val next = rtf[i + 1]
                        when (next) {
                            '\\', '{', '}' -> {
                                sb.append(next)
                                i++
                            }
                            '\'' -> {
                                if (i + 3 < rtf.length) {
                                    val hex = rtf.substring(i + 2, i + 4).toIntOrNull(16)
                                    if (hex != null) sb.append(hex.toChar())
                                    i += 3
                                }
                            }
                            else -> {
                                val wordStart = i + 1
                                var j = wordStart
                                while (j < rtf.length && rtf[j].isLetter()) j++
                                val word = rtf.substring(wordStart, j)
                                while (j < rtf.length && (rtf[j].isDigit() || rtf[j] == '-')) j++
                                if (j < rtf.length && rtf[j] == ' ') j++
                                when (word) {
                                    "par", "line" -> sb.append('\n')
                                    "tab" -> sb.append('\t')
                                }
                                i = j - 1
                            }
                        }
                    } else {
                        i++
                    }
                }
                '\r', '\n' -> Unit
                else -> if (depth < skipGroupDepth) sb.append(c)
            }
            i++
        }
        val plain = sb.toString().trim()
        val blocks = plain.lines().filter { it.isNotBlank() }.map { DocBlock.Paragraph(it.trim()) }
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.RTF,
            kind = DocumentKind.PROSE,
            blocks = blocks.ifEmpty { listOf(DocBlock.Paragraph(plain)) },
            rawText = plain
        )
    }

    private fun exportMarkdown(doc: UnifiedDocument): String {
        return when (doc.kind) {
            DocumentKind.PROSE -> doc.asProseBlocks().joinToString("\n\n") { block ->
                when (block) {
                    is DocBlock.Heading -> "${"#".repeat(block.level.coerceIn(1, 6))} ${block.text}"
                    is DocBlock.Paragraph -> block.text
                    is DocBlock.BulletItem -> "- ${block.text}"
                    is DocBlock.CodeBlock -> "```${block.language}\n${block.text}\n```"
                    is DocBlock.TableBlock -> buildString {
                        val rows = block.rows
                        if (rows.isNotEmpty()) {
                            appendLine("| ${rows.first().joinToString(" | ")} |")
                            appendLine("| ${rows.first().joinToString(" | ") { "---" }} |")
                            rows.drop(1).forEach { r ->
                                appendLine("| ${r.joinToString(" | ")} |")
                            }
                        }
                    }.trimEnd()
                    DocBlock.Divider -> "---"
                }
            }
            DocumentKind.SPREADSHEET -> doc.sheets.joinToString("\n\n") { sheet ->
                buildString {
                    appendLine("## ${sheet.name}")
                    appendLine()
                    if (sheet.rows.isNotEmpty()) {
                        appendLine("| ${sheet.rows.first().joinToString(" | ")} |")
                        appendLine("| ${sheet.rows.first().joinToString(" | ") { "---" }} |")
                        sheet.rows.drop(1).forEach { r ->
                            appendLine("| ${r.joinToString(" | ")} |")
                        }
                    }
                }.trimEnd()
            }
            DocumentKind.PRESENTATION -> doc.slides.mapIndexed { idx, slide ->
                buildString {
                    appendLine("## Slide ${idx + 1}: ${slide.title}")
                    appendLine()
                    slide.bullets.forEach { appendLine("- $it") }
                    if (slide.notes.isNotBlank()) {
                        appendLine()
                        appendLine("> Notes: ${slide.notes}")
                    }
                }.trimEnd()
            }.joinToString("\n\n---\n\n")
        }
    }

    private fun exportDelimited(doc: UnifiedDocument, delimiter: Char): String {
        val sheet = doc.asSheets().firstOrNull() ?: SheetData("Sheet1", emptyList())
        return sheet.rows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell ->
                val needsQuote = cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')
                if (needsQuote) "\"${cell.replace("\"", "\"\"")}\"" else cell
            }
        }
    }

    private fun exportHtml(doc: UnifiedDocument): String {
        val body = StringBuilder()
        when (doc.kind) {
            DocumentKind.PROSE -> doc.asProseBlocks().forEach { block ->
                when (block) {
                    is DocBlock.Heading -> {
                        val lvl = block.level.coerceIn(1, 6)
                        body.appendLine("<h$lvl>${SecurityUtils.escapeXml(block.text)}</h$lvl>")
                    }
                    is DocBlock.Paragraph -> body.appendLine("<p>${SecurityUtils.escapeXml(block.text)}</p>")
                    is DocBlock.BulletItem -> body.appendLine("<ul><li>${SecurityUtils.escapeXml(block.text)}</li></ul>")
                    is DocBlock.CodeBlock -> body.appendLine("<pre><code>${SecurityUtils.escapeXml(block.text)}</code></pre>")
                    is DocBlock.TableBlock -> {
                        body.appendLine("<table>")
                        block.rows.forEachIndexed { idx, row ->
                            val tag = if (idx == 0) "th" else "td"
                            body.append("<tr>")
                            row.forEach { cell -> body.append("<$tag>${SecurityUtils.escapeXml(cell)}</$tag>") }
                            body.appendLine("</tr>")
                        }
                        body.appendLine("</table>")
                    }
                    DocBlock.Divider -> body.appendLine("<hr/>")
                }
            }
            DocumentKind.SPREADSHEET -> doc.sheets.forEach { sheet ->
                body.appendLine("<h2>${SecurityUtils.escapeXml(sheet.name)}</h2>")
                body.appendLine("<table>")
                sheet.rows.forEachIndexed { idx, row ->
                    val tag = if (idx == 0) "th" else "td"
                    body.append("<tr>")
                    row.forEach { cell -> body.append("<$tag>${SecurityUtils.escapeXml(cell)}</$tag>") }
                    body.appendLine("</tr>")
                }
                body.appendLine("</table>")
            }
            DocumentKind.PRESENTATION -> doc.slides.forEachIndexed { idx, slide ->
                body.appendLine("<section class='slide'>")
                body.appendLine("<h2>Slide ${idx + 1}: ${SecurityUtils.escapeXml(slide.title)}</h2>")
                body.appendLine("<ul>")
                slide.bullets.forEach { body.appendLine("<li>${SecurityUtils.escapeXml(it)}</li>") }
                body.appendLine("</ul>")
                if (slide.notes.isNotBlank()) {
                    body.appendLine("<p class='notes'>${SecurityUtils.escapeXml(slide.notes)}</p>")
                }
                body.appendLine("</section>")
            }
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>${SecurityUtils.escapeXml(doc.title)}</title>
            <style>
              body { font-family: system-ui, -apple-system, sans-serif; max-width: 860px; margin: 2rem auto; padding: 0 1rem; color: #0f172a; line-height: 1.6; }
              table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
              th, td { border: 1px solid #cbd5e1; padding: 8px 10px; text-align: left; }
              th { background: #0f766e; color: white; }
              pre { background: #0f172a; color: #e2e8f0; padding: 12px; border-radius: 8px; overflow-x: auto; }
              .slide { border: 1px solid #cbd5e1; border-radius: 12px; padding: 16px; margin-bottom: 16px; }
              .notes { font-style: italic; color: #475569; }
            </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun exportJson(doc: UnifiedDocument): String {
        val root = JSONObject()
        root.put("title", doc.title)
        root.put("kind", doc.kind.name)
        when (doc.kind) {
            DocumentKind.PROSE -> {
                val arr = JSONArray()
                doc.asProseBlocks().forEach { block ->
                    val obj = JSONObject()
                    when (block) {
                        is DocBlock.Heading -> {
                            obj.put("type", "heading")
                            obj.put("level", block.level)
                            obj.put("text", block.text)
                        }
                        is DocBlock.Paragraph -> {
                            obj.put("type", "paragraph")
                            obj.put("text", block.text)
                        }
                        is DocBlock.BulletItem -> {
                            obj.put("type", "bullet")
                            obj.put("text", block.text)
                        }
                        is DocBlock.CodeBlock -> {
                            obj.put("type", "code")
                            obj.put("language", block.language)
                            obj.put("text", block.text)
                        }
                        is DocBlock.TableBlock -> {
                            obj.put("type", "table")
                            val rowsArr = JSONArray()
                            block.rows.forEach { r -> rowsArr.put(JSONArray(r)) }
                            obj.put("rows", rowsArr)
                        }
                        DocBlock.Divider -> obj.put("type", "divider")
                    }
                    arr.put(obj)
                }
                root.put("blocks", arr)
            }
            DocumentKind.SPREADSHEET -> {
                val sheetsObj = JSONObject()
                doc.sheets.forEach { sheet ->
                    val rowsArr = JSONArray()
                    val headers = sheet.rows.firstOrNull()
                    if (headers != null && sheet.rows.size > 1) {
                        sheet.rows.drop(1).forEach { row ->
                            val rowObj = JSONObject()
                            headers.forEachIndexed { idx, colName ->
                                val key = colName.ifBlank { "col_${idx + 1}" }
                                rowObj.put(key, row.getOrElse(idx) { "" })
                            }
                            rowsArr.put(rowObj)
                        }
                    } else {
                        sheet.rows.forEach { r -> rowsArr.put(JSONArray(r)) }
                    }
                    sheetsObj.put(sheet.name, rowsArr)
                }
                root.put("sheets", sheetsObj)
            }
            DocumentKind.PRESENTATION -> {
                val slidesArr = JSONArray()
                doc.slides.forEach { slide ->
                    val sObj = JSONObject()
                    sObj.put("title", slide.title)
                    sObj.put("bullets", JSONArray(slide.bullets))
                    sObj.put("notes", slide.notes)
                    slidesArr.put(sObj)
                }
                root.put("slides", slidesArr)
            }
        }
        return root.toString(2)
    }

    private fun exportXml(doc: UnifiedDocument): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<document title=\"${SecurityUtils.escapeXml(doc.title)}\" kind=\"${doc.kind.name}\">")
        when (doc.kind) {
            DocumentKind.PROSE -> doc.asProseBlocks().forEach { block ->
                when (block) {
                    is DocBlock.Heading -> sb.appendLine("  <heading level=\"${block.level}\">${SecurityUtils.escapeXml(block.text)}</heading>")
                    is DocBlock.Paragraph -> sb.appendLine("  <paragraph>${SecurityUtils.escapeXml(block.text)}</paragraph>")
                    is DocBlock.BulletItem -> sb.appendLine("  <bullet>${SecurityUtils.escapeXml(block.text)}</bullet>")
                    is DocBlock.CodeBlock -> sb.appendLine("  <code>${SecurityUtils.escapeXml(block.text)}</code>")
                    is DocBlock.TableBlock -> {
                        sb.appendLine("  <table>")
                        block.rows.forEach { r ->
                            sb.append("    <row>")
                            r.forEach { c -> sb.append("<cell>${SecurityUtils.escapeXml(c)}</cell>") }
                            sb.appendLine("</row>")
                        }
                        sb.appendLine("  </table>")
                    }
                    DocBlock.Divider -> sb.appendLine("  <divider/>")
                }
            }
            DocumentKind.SPREADSHEET -> doc.sheets.forEach { sheet ->
                sb.appendLine("  <sheet name=\"${SecurityUtils.escapeXml(sheet.name)}\">")
                sheet.rows.forEach { r ->
                    sb.append("    <row>")
                    r.forEach { c -> sb.append("<cell>${SecurityUtils.escapeXml(c)}</cell>") }
                    sb.appendLine("</row>")
                }
                sb.appendLine("  </sheet>")
            }
            DocumentKind.PRESENTATION -> doc.slides.forEachIndexed { idx, slide ->
                sb.appendLine("  <slide index=\"${idx + 1}\" title=\"${SecurityUtils.escapeXml(slide.title)}\">")
                slide.bullets.forEach { sb.appendLine("    <bullet>${SecurityUtils.escapeXml(it)}</bullet>") }
                if (slide.notes.isNotBlank()) {
                    sb.appendLine("    <notes>${SecurityUtils.escapeXml(slide.notes)}</notes>")
                }
                sb.appendLine("  </slide>")
            }
        }
        sb.appendLine("</document>")
        return sb.toString()
    }

    private fun exportRtf(doc: UnifiedDocument): String {
        fun esc(s: String): String = buildString {
            for (ch in s) {
                when {
                    ch == '\\' -> append("\\\\")
                    ch == '{' -> append("\\{")
                    ch == '}' -> append("\\}")
                    ch == '\n' -> append("\\par ")
                    ch.code > 127 -> append("\\u${ch.code}?")
                    else -> append(ch)
                }
            }
        }
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Helvetica;}}\\fs24 ")
        doc.asProseBlocks().forEach { block ->
            when (block) {
                is DocBlock.Heading -> sb.append("\\b\\fs32 ${esc(block.text)}\\b0\\fs24\\par\\par ")
                is DocBlock.Paragraph -> sb.append("${esc(block.text)}\\par\\par ")
                is DocBlock.BulletItem -> sb.append("\\bullet  ${esc(block.text)}\\par ")
                is DocBlock.CodeBlock -> sb.append("${esc(block.text)}\\par\\par ")
                is DocBlock.TableBlock -> {
                    block.rows.forEach { row ->
                        sb.append("${esc(row.joinToString("   |   "))}\\par ")
                    }
                    sb.append("\\par ")
                }
                DocBlock.Divider -> sb.append("--------------------\\par ")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
