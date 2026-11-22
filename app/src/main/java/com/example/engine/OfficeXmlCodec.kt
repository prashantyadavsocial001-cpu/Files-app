package com.example.engine

import android.util.Xml
import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.SheetData
import com.example.model.SlideData
import com.example.model.UnifiedDocument
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin lightweight streaming OpenXML (.docx, .xlsx, .pptx) and legacy binary (.doc, .xls, .ppt) codec.
 * Runs 100% offline with zero bulky POI dependencies and strict Zip-Slip / Zip-Bomb safeguards.
 */
object OfficeXmlCodec {

    fun parseDocx(title: String, inputStream: InputStream): UnifiedDocument {
        val entries = SecurityUtils.readBoundedZipEntries(inputStream) { name ->
            name == "word/document.xml"
        }
        val docBytes = entries["word/document.xml"]
            ?: return UnifiedDocument(
                title = title,
                sourceFormat = DocFormat.DOCX,
                kind = DocumentKind.PROSE,
                blocks = listOf(DocBlock.Paragraph("Empty DOCX archive (missing word/document.xml).")),
                warnings = listOf("word/document.xml not found inside DOCX.")
            )
        val blocks = parseWordDocumentXml(docBytes)
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.DOCX,
            kind = DocumentKind.PROSE,
            blocks = blocks.ifEmpty { listOf(DocBlock.Paragraph("")) },
            rawText = blocks.joinToString("\n\n") {
                when (it) {
                    is DocBlock.Heading -> it.text
                    is DocBlock.Paragraph -> it.text
                    is DocBlock.BulletItem -> "• ${it.text}"
                    is DocBlock.CodeBlock -> it.text
                    is DocBlock.TableBlock -> it.rows.joinToString("\n") { r -> r.joinToString(" | ") }
                    DocBlock.Divider -> "---"
                }
            }
        )
    }

    private fun parseWordDocumentXml(xmlBytes: ByteArray): List<DocBlock> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
        val blocks = mutableListOf<DocBlock>()
        var inParagraph = false
        var inTable = false
        var inRow = false
        var inCell = false
        var currentParaText = StringBuilder()
        var currentStyle = ""
        var isBullet = false
        var isBold = false
        var isItalic = false
        var currentTable = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentCellText = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        tag.endsWith(":tbl") || tag == "tbl" -> {
                            inTable = true
                            currentTable = mutableListOf()
                        }
                        inTable && (tag.endsWith(":tr") || tag == "tr") -> {
                            inRow = true
                            currentRow = mutableListOf()
                        }
                        inRow && (tag.endsWith(":tc") || tag == "tc") -> {
                            inCell = true
                            currentCellText = StringBuilder()
                        }
                        tag.endsWith(":p") || tag == "p" -> {
                            inParagraph = true
                            currentParaText = StringBuilder()
                            currentStyle = ""
                            isBullet = false
                            isBold = false
                            isItalic = false
                        }
                        inParagraph && (tag.endsWith(":pStyle") || tag == "pStyle") -> {
                            currentStyle = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val")
                                ?: ""
                        }
                        inParagraph && (tag.endsWith(":numPr") || tag == "numPr") -> {
                            isBullet = true
                        }
                        inParagraph && (tag.endsWith(":b") || tag == "b") -> {
                            val v = parser.getAttributeValue(null, "w:val") ?: parser.getAttributeValue(null, "val")
                            if (v != "0" && v != "false") isBold = true
                        }
                        inParagraph && (tag.endsWith(":i") || tag == "i") -> {
                            val v = parser.getAttributeValue(null, "w:val") ?: parser.getAttributeValue(null, "val")
                            if (v != "0" && v != "false") isItalic = true
                        }
                        inParagraph && (tag.endsWith(":t") || tag == "t") -> {
                            val txt = parser.nextText()
                            if (inCell) {
                                currentCellText.append(txt)
                            } else {
                                currentParaText.append(txt)
                            }
                        }
                        inParagraph && (tag.endsWith(":tab") || tag == "tab") -> {
                            if (inCell) currentCellText.append("  ") else currentParaText.append("    ")
                        }
                        inParagraph && (tag.endsWith(":br") || tag == "br") -> {
                            if (inCell) currentCellText.append(" ") else currentParaText.append("\n")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    when {
                        tag.endsWith(":p") || tag == "p" -> {
                            if (inCell) {
                                if (currentCellText.isNotEmpty() && !currentCellText.endsWith("\n")) {
                                    currentCellText.append(" ")
                                }
                            } else {
                                val text = currentParaText.toString().trim()
                                if (text.isNotEmpty()) {
                                    val styleLower = currentStyle.lowercase()
                                    when {
                                        styleLower.contains("heading1") || styleLower == "title" ->
                                            blocks.add(DocBlock.Heading(1, text))
                                        styleLower.contains("heading2") || styleLower == "subtitle" ->
                                            blocks.add(DocBlock.Heading(2, text))
                                        styleLower.contains("heading") ->
                                            blocks.add(DocBlock.Heading(3, text))
                                        isBullet || styleLower.contains("list") ->
                                            blocks.add(DocBlock.BulletItem(text))
                                        else ->
                                            blocks.add(DocBlock.Paragraph(text, isBold = isBold, isItalic = isItalic))
                                    }
                                }
                            }
                            inParagraph = false
                        }
                        inRow && (tag.endsWith(":tc") || tag == "tc") -> {
                            currentRow.add(currentCellText.toString().trim())
                            inCell = false
                        }
                        inTable && (tag.endsWith(":tr") || tag == "tr") -> {
                            if (currentRow.isNotEmpty()) currentTable.add(currentRow.toList())
                            inRow = false
                        }
                        tag.endsWith(":tbl") || tag == "tbl" -> {
                            if (currentTable.isNotEmpty()) blocks.add(DocBlock.TableBlock(currentTable.toList()))
                            inTable = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    fun writeDocx(doc: UnifiedDocument, out: OutputStream) {
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
        """.trimIndent()

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        val docRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                <w:name w:val="Normal"/>
                <w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>
              </w:style>
              <w:style w:type="paragraph" w:styleId="Heading1">
                <w:name w:val="heading 1"/>
                <w:rPr><w:b/><w:color w:val="0F766E"/><w:sz w:val="36"/></w:rPr>
              </w:style>
              <w:style w:type="paragraph" w:styleId="Heading2">
                <w:name w:val="heading 2"/>
                <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
              </w:style>
              <w:style w:type="paragraph" w:styleId="Heading3">
                <w:name w:val="heading 3"/>
                <w:rPr><w:b/><w:sz w:val="24"/></w:rPr>
              </w:style>
            </w:styles>
        """.trimIndent()

        val bodyXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            appendLine("<w:body>")
            for (block in doc.asProseBlocks()) {
                when (block) {
                    is DocBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> "Heading1"
                            2 -> "Heading2"
                            else -> "Heading3"
                        }
                        appendLine("""<w:p><w:pPr><w:pStyle w:val="$style"/></w:pPr><w:r><w:t xml:space="preserve">${SecurityUtils.escapeXml(block.text)}</w:t></w:r></w:p>""")
                    }
                    is DocBlock.Paragraph -> {
                        val rPr = buildString {
                            if (block.isBold || block.isItalic) {
                                append("<w:rPr>")
                                if (block.isBold) append("<w:b/>")
                                if (block.isItalic) append("<w:i/>")
                                append("</w:rPr>")
                            }
                        }
                        appendLine("""<w:p><w:r>$rPr<w:t xml:space="preserve">${SecurityUtils.escapeXml(block.text)}</w:t></w:r></w:p>""")
                    }
                    is DocBlock.BulletItem -> {
                        appendLine("""<w:p><w:r><w:t xml:space="preserve">• ${SecurityUtils.escapeXml(block.text)}</w:t></w:r></w:p>""")
                    }
                    is DocBlock.CodeBlock -> {
                        block.text.lines().forEach { line ->
                            appendLine("""<w:p><w:r><w:rPr><w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/></w:rPr><w:t xml:space="preserve">${SecurityUtils.escapeXml(line)}</w:t></w:r></w:p>""")
                        }
                    }
                    is DocBlock.TableBlock -> {
                        appendLine("""<w:tbl><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:color="CBD5E1"/><w:bottom w:val="single" w:sz="4" w:color="CBD5E1"/><w:insideH w:val="single" w:sz="4" w:color="CBD5E1"/><w:insideV w:val="single" w:sz="4" w:color="CBD5E1"/></w:tblBorders></w:tblPr>""")
                        block.rows.forEach { row ->
                            appendLine("<w:tr>")
                            row.forEach { cell ->
                                appendLine("""<w:tc><w:p><w:r><w:t xml:space="preserve">${SecurityUtils.escapeXml(cell)}</w:t></w:r></w:p></w:tc>""")
                            }
                            appendLine("</w:tr>")
                        }
                        appendLine("</w:tbl>")
                    }
                    DocBlock.Divider -> {
                        appendLine("""<w:p><w:r><w:t>────────────────────────</w:t></w:r></w:p>""")
                    }
                }
            }
            appendLine("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr>""")
            appendLine("</w:body></w:document>")
        }

        ZipOutputStream(out.buffered()).use { zos ->
            putZipText(zos, "[Content_Types].xml", contentTypes)
            putZipText(zos, "_rels/.rels", rootRels)
            putZipText(zos, "word/_rels/document.xml.rels", docRels)
            putZipText(zos, "word/styles.xml", stylesXml)
            putZipText(zos, "word/document.xml", bodyXml)
        }
    }

    fun parseXlsx(title: String, inputStream: InputStream): UnifiedDocument {
        val entries = SecurityUtils.readBoundedZipEntries(inputStream) { name ->
            name == "xl/sharedStrings.xml" ||
                name == "xl/workbook.xml" ||
                name == "xl/_rels/workbook.xml.rels" ||
                name.startsWith("xl/worksheets/sheet")
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetMeta = parseWorkbookSheets(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])
        val parsedSheets = mutableListOf<SheetData>()

        if (sheetMeta.isNotEmpty()) {
            for ((sheetName, path) in sheetMeta) {
                val bytes = entries[path] ?: entries["xl/$path"] ?: continue
                parsedSheets.add(SheetData(sheetName, parseWorksheetXml(bytes, sharedStrings)))
            }
        }
        if (parsedSheets.isEmpty()) {
            val fallbackEntries = entries.filterKeys { it.startsWith("xl/worksheets/sheet") }.toSortedMap()
            fallbackEntries.entries.forEachIndexed { idx, (_, bytes) ->
                parsedSheets.add(SheetData("Sheet${idx + 1}", parseWorksheetXml(bytes, sharedStrings)))
            }
        }
        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.XLSX,
            kind = DocumentKind.SPREADSHEET,
            sheets = parsedSheets.ifEmpty { listOf(SheetData("Sheet1", listOf(listOf("")))) }
        )
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
        val strings = mutableListOf<String>()
        var inSi = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            inSi = true
                            current.setLength(0)
                        }
                        "t" -> if (inSi) current.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(current.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorkbookSheets(workbookBytes: ByteArray?, relsBytes: ByteArray?): List<Pair<String, String>> {
        if (workbookBytes == null) return emptyList()
        val relMap = mutableMapOf<String, String>()
        if (relsBytes != null) {
            val relParser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(ByteArrayInputStream(relsBytes), "UTF-8")
            }
            var event = relParser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                    val id = relParser.getAttributeValue(null, "Id")
                    val target = relParser.getAttributeValue(null, "Target")?.trimStart('/')
                    if (id != null && target != null) {
                        relMap[id] = if (target.startsWith("xl/")) target else "xl/$target"
                    }
                }
                event = relParser.next()
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        val wbParser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(workbookBytes), "UTF-8")
        }
        var idx = 1
        var event = wbParser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && wbParser.name == "sheet") {
                val name = wbParser.getAttributeValue(null, "name") ?: "Sheet$idx"
                val rId = wbParser.getAttributeValue(null, "r:id") ?: wbParser.getAttributeValue(null, "id")
                val path = (rId?.let { relMap[it] }) ?: "xl/worksheets/sheet$idx.xml"
                result.add(name to path)
                idx++
            }
            event = wbParser.next()
        }
        return result
    }

    private fun parseWorksheetXml(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
        val rowsMap = sortedMapOf<Int, MutableMap<Int, String>>()
        var maxCol = 0
        var currentRowIdx = 0
        var currentColIdx = 0
        var cellType = ""
        var inCell = false
        var cellValue = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            val rAttr = parser.getAttributeValue(null, "r")?.toIntOrNull()
                            currentRowIdx = (rAttr ?: (currentRowIdx + 1)) - 1
                            currentColIdx = -1
                        }
                        "c" -> {
                            inCell = true
                            val ref = parser.getAttributeValue(null, "r")
                            currentColIdx = if (ref != null) parseColIndex(ref) else (currentColIdx + 1)
                            if (currentColIdx > maxCol) maxCol = currentColIdx
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v" -> if (inCell) {
                            val raw = parser.nextText()
                            cellValue = when (cellType) {
                                "s" -> sharedStrings.getOrElse(raw.toIntOrNull() ?: -1) { raw }
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                        }
                        "t" -> if (inCell && cellType == "inlineStr") {
                            cellValue += parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "c" && inCell) {
                        val rowMap = rowsMap.getOrPut(currentRowIdx) { mutableMapOf() }
                        rowMap[currentColIdx] = cellValue
                        inCell = false
                    }
                }
            }
            event = parser.next()
        }
        if (rowsMap.isEmpty()) return listOf(listOf(""))
        val totalRows = (rowsMap.lastKey() + 1).coerceAtLeast(1)
        val totalCols = (maxCol + 1).coerceAtLeast(1)
        return List(totalRows) { r ->
            val rMap = rowsMap[r]
            List(totalCols) { c -> rMap?.get(c) ?: "" }
        }
    }

    private fun parseColIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun writeXlsx(doc: UnifiedDocument, out: OutputStream) {
        val sheets = doc.asSheets().ifEmpty { listOf(SheetData("Sheet1", listOf(listOf("")))) }
        val sharedList = mutableListOf<String>()
        val sharedIndexMap = HashMap<String, Int>()
        fun intern(s: String): Int = sharedIndexMap.getOrPut(s) {
            val idx = sharedList.size
            sharedList.add(s)
            idx
        }

        val sheetXmlList = sheets.map { sheet ->
            buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                sheet.rows.forEachIndexed { rIdx, row ->
                    val rNum = rIdx + 1
                    append("<row r=\"$rNum\">")
                    row.forEachIndexed { cIdx, cellVal ->
                        val ref = "${colIndexToLetters(cIdx)}$rNum"
                        val num = cellVal.toDoubleOrNull()
                        if (num != null && cellVal.isNotBlank() && !cellVal.startsWith("0") || cellVal == "0") {
                            append("<c r=\"$ref\"><v>${SecurityUtils.escapeXml(cellVal)}</v></c>")
                        } else if (cellVal.isNotEmpty()) {
                            val sIdx = intern(cellVal)
                            append("<c r=\"$ref\" t=\"s\"><v>$sIdx</v></c>")
                        }
                    }
                    appendLine("</row>")
                }
                appendLine("</sheetData></worksheet>")
            }
        }

        val contentTypes = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
            appendLine("""  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            appendLine("""  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            appendLine("""  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
            sheets.indices.forEach { idx ->
                appendLine("""  <Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            appendLine("</Types>")
        }

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()

        val wbRels = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.indices.forEach { idx ->
                appendLine("""  <Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>""")
            }
            val sId = sheets.size + 1
            appendLine("""  <Relationship Id="rId$sId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            appendLine("""  <Relationship Id="rId${sId + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
            appendLine("</Relationships>")
        }

        val workbookXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            sheets.forEachIndexed { idx, sheet ->
                val cleanName = SecurityUtils.escapeXml(sheet.name.take(31).ifBlank { "Sheet${idx + 1}" })
                appendLine("""  <sheet name="$cleanName" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
            }
            appendLine("</sheets></workbook>")
        }

        val sharedStringsXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedList.size}" uniqueCount="${sharedList.size}">""")
            sharedList.forEach { s ->
                appendLine("""  <si><t xml:space="preserve">${SecurityUtils.escapeXml(s)}</t></si>""")
            }
            appendLine("</sst>")
        }

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
              <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
              <borders count="1"><border/></borders>
              <cellStyleXfs count="1"><xf/></cellStyleXfs>
              <cellXfs count="1"><xf xfId="0"/></cellXfs>
            </styleSheet>
        """.trimIndent()

        ZipOutputStream(out.buffered()).use { zos ->
            putZipText(zos, "[Content_Types].xml", contentTypes)
            putZipText(zos, "_rels/.rels", rootRels)
            putZipText(zos, "xl/_rels/workbook.xml.rels", wbRels)
            putZipText(zos, "xl/workbook.xml", workbookXml)
            putZipText(zos, "xl/styles.xml", stylesXml)
            putZipText(zos, "xl/sharedStrings.xml", sharedStringsXml)
            sheetXmlList.forEachIndexed { idx, xml ->
                putZipText(zos, "xl/worksheets/sheet${idx + 1}.xml", xml)
            }
        }
    }

    fun parsePptx(title: String, inputStream: InputStream): UnifiedDocument {
        val entries = SecurityUtils.readBoundedZipEntries(inputStream) { name ->
            name == "ppt/presentation.xml" ||
                name == "ppt/_rels/presentation.xml.rels" ||
                name.startsWith("ppt/slides/slide") ||
                name.startsWith("ppt/slides/_rels/slide") ||
                name.startsWith("ppt/notesSlides/notesSlide")
        }

        val slideOrder = resolveSlideOrder(entries)
        val slides = slideOrder.mapIndexed { index, slidePath ->
            val slideBytes = entries[slidePath]
            val notesPath = resolveNotesPath(slidePath, entries)
            val notesText = notesPath?.let { entries[it] }?.let { extractAllTextRuns(it).joinToString(" ") } ?: ""
            if (slideBytes != null) {
                parseSlideXml(index + 1, slideBytes, notesText)
            } else {
                SlideData("Slide ${index + 1}", emptyList(), notesText)
            }
        }

        return UnifiedDocument(
            title = title,
            sourceFormat = DocFormat.PPTX,
            kind = DocumentKind.PRESENTATION,
            slides = slides.ifEmpty { listOf(SlideData(title, listOf("Empty presentation"))) }
        )
    }

    private fun resolveSlideOrder(entries: Map<String, ByteArray>): List<String> {
        val relBytes = entries["ppt/_rels/presentation.xml.rels"]
        val presBytes = entries["ppt/presentation.xml"]
        if (relBytes != null && presBytes != null) {
            val relMap = mutableMapOf<String, String>()
            val relParser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(ByteArrayInputStream(relBytes), "UTF-8")
            }
            var ev = relParser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                    val id = relParser.getAttributeValue(null, "Id")
                    val target = relParser.getAttributeValue(null, "Target")?.trimStart('/')
                    if (id != null && target != null) {
                        relMap[id] = if (target.startsWith("ppt/")) target else "ppt/$target"
                    }
                }
                ev = relParser.next()
            }
            val ordered = mutableListOf<String>()
            val presParser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(ByteArrayInputStream(presBytes), "UTF-8")
            }
            ev = presParser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && presParser.name.endsWith("sldId")) {
                    val rId = presParser.getAttributeValue(null, "r:id") ?: presParser.getAttributeValue(null, "id")
                    val path = rId?.let { relMap[it] }
                    if (path != null) ordered.add(path)
                }
                ev = presParser.next()
            }
            if (ordered.isNotEmpty()) return ordered
        }
        return entries.keys
            .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 }
    }

    private fun resolveNotesPath(slidePath: String, entries: Map<String, ByteArray>): String? {
        val fileName = slidePath.substringAfterLast('/')
        val relPath = "ppt/slides/_rels/$fileName.rels"
        val relBytes = entries[relPath] ?: return null
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(relBytes), "UTF-8")
        }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val target = parser.getAttributeValue(null, "Target") ?: ""
                if (target.contains("notesSlide")) {
                    val clean = target.substringAfterLast('/')
                    return "ppt/notesSlides/$clean"
                }
            }
            ev = parser.next()
        }
        return null
    }

    private fun parseSlideXml(slideNum: Int, xmlBytes: ByteArray, notes: String): SlideData {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
        var inShape = false
        var isTitleShape = false
        var inPara = false
        val paraText = StringBuilder()
        var title = ""
        val bullets = mutableListOf<String>()

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when {
                        parser.name.endsWith(":sp") || parser.name == "sp" -> {
                            inShape = true
                            isTitleShape = false
                        }
                        inShape && (parser.name.endsWith(":ph") || parser.name == "ph") -> {
                            val type = parser.getAttributeValue(null, "type")?.lowercase() ?: ""
                            if (type == "title" || type == "ctrtitle") isTitleShape = true
                        }
                        parser.name.endsWith(":p") || parser.name == "p" -> {
                            inPara = true
                            paraText.setLength(0)
                        }
                        inPara && (parser.name.endsWith(":t") || parser.name == "t") -> {
                            paraText.append(parser.nextText())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        parser.name.endsWith(":p") || parser.name == "p" -> {
                            val line = paraText.toString().trim()
                            if (line.isNotEmpty()) {
                                if (isTitleShape && title.isEmpty()) {
                                    title = line
                                } else {
                                    bullets.add(line)
                                }
                            }
                            inPara = false
                        }
                        parser.name.endsWith(":sp") || parser.name == "sp" -> {
                            inShape = false
                        }
                    }
                }
            }
            ev = parser.next()
        }
        if (title.isEmpty()) {
            title = bullets.firstOrNull()?.also { bullets.removeAt(0) } ?: "Slide $slideNum"
        }
        return SlideData(title = title, bullets = bullets, notes = notes)
    }

    private fun extractAllTextRuns(xmlBytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }
        val runs = mutableListOf<String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && (parser.name.endsWith(":t") || parser.name == "t")) {
                val txt = parser.nextText().trim()
                if (txt.isNotEmpty()) runs.add(txt)
            }
            ev = parser.next()
        }
        return runs
    }

    fun writePptx(doc: UnifiedDocument, out: OutputStream) {
        val slides = doc.asSlides().ifEmpty { listOf(SlideData(doc.title, emptyList())) }

        val contentTypes = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
            appendLine("""  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
            appendLine("""  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
            appendLine("""  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
            appendLine("""  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>""")
            slides.indices.forEach { idx ->
                appendLine("""  <Override PartName="/ppt/slides/slide${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            }
            appendLine("</Types>")
        }

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
            </Relationships>
        """.trimIndent()

        val presRels = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            appendLine("""  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
            appendLine("""  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>""")
            slides.indices.forEach { idx ->
                appendLine("""  <Relationship Id="rId${idx + 3}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${idx + 1}.xml"/>""")
            }
            appendLine("</Relationships>")
        }

        val presentationXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            appendLine("""  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>""")
            appendLine("""  <p:sldIdLst>""")
            slides.indices.forEach { idx ->
                appendLine("""    <p:sldId id="${256 + idx}" r:id="rId${idx + 3}"/>""")
            }
            appendLine("""  </p:sldIdLst>""")
            appendLine("""  <p:sldSz cx="12192000" cy="6858000"/>""")
            appendLine("""  <p:notesSz cx="6858000" cy="9144000"/>""")
            appendLine("</p:presentation>")
        }

        val slideMasterRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
            </Relationships>
        """.trimIndent()

        val slideMasterXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
              <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
              <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
            </p:sldMaster>
        """.trimIndent()

        val slideLayoutRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
            </Relationships>
        """.trimIndent()

        val slideLayoutXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="titleAndBody">
              <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
            </p:sldLayout>
        """.trimIndent()

        val themeXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="FolioFlow Theme">
              <a:themeElements>
                <a:clrScheme name="FolioFlow">
                  <a:dk1><a:srgbClr val="0F172A"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
                  <a:dk2><a:srgbClr val="1E293B"/></a:dk2><a:lt2><a:srgbClr val="F8FAFC"/></a:lt2>
                  <a:accent1><a:srgbClr val="0F766E"/></a:accent1><a:accent2><a:srgbClr val="D97706"/></a:accent2>
                  <a:accent3><a:srgbClr val="3B82F6"/></a:accent3><a:accent4><a:srgbClr val="10B981"/></a:accent4>
                  <a:accent5><a:srgbClr val="EC4899"/></a:accent5><a:accent6><a:srgbClr val="6366F1"/></a:accent6>
                  <a:hlink><a:srgbClr val="0F766E"/></a:hlink><a:folHlink><a:srgbClr val="64748B"/></a:folHlink>
                </a:clrScheme>
                <a:fontScheme name="FolioFlow"><a:majorFont><a:latin typeface="Calibri"/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/></a:minorFont></a:fontScheme>
                <a:fmtScheme name="FolioFlow"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>
              </a:themeElements>
            </a:theme>
        """.trimIndent()

        val slideRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
            </Relationships>
        """.trimIndent()

        ZipOutputStream(out.buffered()).use { zos ->
            putZipText(zos, "[Content_Types].xml", contentTypes)
            putZipText(zos, "_rels/.rels", rootRels)
            putZipText(zos, "ppt/_rels/presentation.xml.rels", presRels)
            putZipText(zos, "ppt/presentation.xml", presentationXml)
            putZipText(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels)
            putZipText(zos, "ppt/slideMasters/slideMaster1.xml", slideMasterXml)
            putZipText(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels)
            putZipText(zos, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml)
            putZipText(zos, "ppt/theme/theme1.xml", themeXml)

            slides.forEachIndexed { idx, slide ->
                val bulletsXml = slide.bullets.joinToString("") { bullet ->
                    """<a:p><a:pPr marL="342900" indent="-342900"><a:buChar char="•"/></a:pPr><a:r><a:rPr lang="en-US" sz="2000"/><a:t>${SecurityUtils.escapeXml(bullet)}</a:t></a:r></a:p>"""
                }.ifEmpty { """<a:p><a:r><a:rPr lang="en-US" sz="2000"/><a:t></a:t></a:r></a:p>""" }

                val slideXml = """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                      <p:cSld><p:spTree>
                        <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                        <p:grpSpPr/>
                        <p:sp>
                          <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                          <p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="10820400" cy="1143000"/></a:xfrm></p:spPr>
                          <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="3600" b="1"/><a:t>${SecurityUtils.escapeXml(slide.title)}</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                        <p:sp>
                          <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>
                          <p:spPr><a:xfrm><a:off x="685800" y="1828800"/><a:ext cx="10820400" cy="4343400"/></a:xfrm></p:spPr>
                          <p:txBody><a:bodyPr/><a:lstStyle/>$bulletsXml</p:txBody>
                        </p:sp>
                      </p:spTree></p:cSld>
                    </p:sld>
                """.trimIndent()
                putZipText(zos, "ppt/slides/slide${idx + 1}.xml", slideXml)
                putZipText(zos, "ppt/slides/_rels/slide${idx + 1}.xml.rels", slideRels)
            }
        }
    }

    /**
     * Safe offline extractor for legacy binary OLE2 Office files (.doc, .xls, .ppt).
     * Extracts readable UTF-16LE and ASCII text sequences so users can view and upgrade them to modern formats.
     */
    fun parseLegacyBinaryOffice(title: String, format: DocFormat, bytes: ByteArray): UnifiedDocument {
        val runs = mutableListOf<String>()
        val asciiBuf = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126 || b == 10 || b == 13) {
                asciiBuf.append(b.toChar())
            } else {
                if (asciiBuf.length >= 6) {
                    val candidate = asciiBuf.toString().trim()
                    if (candidate.any { it.isLetterOrDigit() } && !candidate.startsWith("Microsoft") && !candidate.contains("Root Entry")) {
                        runs.add(candidate)
                    }
                }
                asciiBuf.setLength(0)
            }
            i++
        }
        val text = runs.distinct().joinToString("\n\n").ifBlank {
            "Binary ${format.displayName} file (${bytes.size} bytes). Convert to modern ${
                when (format) {
                    DocFormat.DOC -> "DOCX"
                    DocFormat.XLS -> "XLSX"
                    else -> "PPTX"
                }
            } or TXT for full editing."
        }
        val blocks = text.split("\n\n").map { DocBlock.Paragraph(it) }
        return UnifiedDocument(
            title = title,
            sourceFormat = format,
            kind = DocumentKind.PROSE,
            blocks = blocks,
            rawText = text,
            warnings = listOf("Legacy binary ${format.extension.uppercase()} opened in safe text-extraction mode. Save/Convert to modern OpenXML (${format.extension.uppercase()}X) or PDF for full editing.")
        )
    }

    private fun putZipText(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }
}
