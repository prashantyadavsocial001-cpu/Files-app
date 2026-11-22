package com.example.model

enum class DocumentKind {
    PROSE,
    SPREADSHEET,
    PRESENTATION
}

sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : DocBlock
    data class BulletItem(val text: String) : DocBlock
    data class CodeBlock(val text: String, val language: String = "") : DocBlock
    data class TableBlock(val rows: List<List<String>>) : DocBlock
    data object Divider : DocBlock
}

data class SheetData(
    val name: String,
    val rows: List<List<String>>
)

data class SlideData(
    val title: String,
    val bullets: List<String>,
    val notes: String = ""
)

data class UnifiedDocument(
    val title: String,
    val sourceFormat: DocFormat,
    val kind: DocumentKind,
    val blocks: List<DocBlock> = emptyList(),
    val sheets: List<SheetData> = emptyList(),
    val slides: List<SlideData> = emptyList(),
    val rawText: String = "",
    val pageCount: Int = 1,
    val warnings: List<String> = emptyList()
) {
    fun toPlainText(): String {
        return when (kind) {
            DocumentKind.PROSE -> {
                if (blocks.isEmpty()) rawText
                else blocks.joinToString("\n\n") { block ->
                    when (block) {
                        is DocBlock.Heading -> block.text
                        is DocBlock.Paragraph -> block.text
                        is DocBlock.BulletItem -> "• ${block.text}"
                        is DocBlock.CodeBlock -> block.text
                        is DocBlock.TableBlock -> block.rows.joinToString("\n") { it.joinToString(" | ") }
                        DocBlock.Divider -> "---"
                    }
                }
            }
            DocumentKind.SPREADSHEET -> {
                sheets.joinToString("\n\n") { sheet ->
                    buildString {
                        appendLine("[Sheet: ${sheet.name}]")
                        sheet.rows.forEach { row ->
                            appendLine(row.joinToString("\t"))
                        }
                    }.trimEnd()
                }
            }
            DocumentKind.PRESENTATION -> {
                slides.mapIndexed { idx, slide ->
                    buildString {
                        appendLine("Slide ${idx + 1}: ${slide.title}")
                        slide.bullets.forEach { appendLine("  • $it") }
                        if (slide.notes.isNotBlank()) {
                            appendLine("  [Notes: ${slide.notes}]")
                        }
                    }.trimEnd()
                }.joinToString("\n\n")
            }
        }
    }

    fun asProseBlocks(): List<DocBlock> {
        if (blocks.isNotEmpty() && kind == DocumentKind.PROSE) return blocks
        return when (kind) {
            DocumentKind.PROSE -> rawText.split("\n").map { DocBlock.Paragraph(it) }
            DocumentKind.SPREADSHEET -> buildList {
                sheets.forEach { sheet ->
                    add(DocBlock.Heading(2, sheet.name))
                    if (sheet.rows.isNotEmpty()) {
                        add(DocBlock.TableBlock(sheet.rows))
                    }
                }
            }
            DocumentKind.PRESENTATION -> buildList {
                slides.forEachIndexed { i, slide ->
                    add(DocBlock.Heading(2, "${i + 1}. ${slide.title}"))
                    slide.bullets.forEach { add(DocBlock.BulletItem(it)) }
                    if (slide.notes.isNotBlank()) {
                        add(DocBlock.Paragraph("Notes: ${slide.notes}", isItalic = true))
                    }
                    if (i < slides.lastIndex) add(DocBlock.Divider)
                }
            }
        }
    }

    fun asSheets(): List<SheetData> {
        if (sheets.isNotEmpty()) return sheets
        return when (kind) {
            DocumentKind.SPREADSHEET -> listOf(SheetData("Sheet1", listOf(listOf("Column 1"))))
            DocumentKind.PROSE -> {
                val tables = blocks.filterIsInstance<DocBlock.TableBlock>()
                if (tables.isNotEmpty()) {
                    tables.mapIndexed { i, tbl -> SheetData("Table${i + 1}", tbl.rows) }
                } else {
                    val rows = mutableListOf(listOf("Block", "Content"))
                    blocks.forEach { block ->
                        when (block) {
                            is DocBlock.Heading -> rows.add(listOf("H${block.level}", block.text))
                            is DocBlock.Paragraph -> if (block.text.isNotBlank()) rows.add(listOf("Text", block.text))
                            is DocBlock.BulletItem -> rows.add(listOf("Bullet", block.text))
                            is DocBlock.CodeBlock -> rows.add(listOf("Code", block.text))
                            is DocBlock.TableBlock -> rows.addAll(block.rows)
                            DocBlock.Divider -> Unit
                        }
                    }
                    if (rows.size == 1 && rawText.isNotBlank()) {
                        rawText.lines().forEachIndexed { idx, line ->
                            rows.add(listOf("${idx + 1}", line))
                        }
                    }
                    listOf(SheetData("Document", rows))
                }
            }
            DocumentKind.PRESENTATION -> {
                val rows = mutableListOf(listOf("Slide", "Title", "Bullets", "Notes"))
                slides.forEachIndexed { idx, slide ->
                    rows.add(
                        listOf(
                            "${idx + 1}",
                            slide.title,
                            slide.bullets.joinToString("; "),
                            slide.notes
                        )
                    )
                }
                listOf(SheetData("Slides", rows))
            }
        }
    }

    fun asSlides(): List<SlideData> {
        if (slides.isNotEmpty()) return slides
        return when (kind) {
            DocumentKind.PRESENTATION -> listOf(SlideData(title, emptyList()))
            DocumentKind.PROSE -> {
                val generated = mutableListOf<SlideData>()
                var currentTitle = title
                var currentBullets = mutableListOf<String>()
                for (block in blocks) {
                    when (block) {
                        is DocBlock.Heading -> {
                            if (currentBullets.isNotEmpty() || generated.isNotEmpty()) {
                                generated.add(SlideData(currentTitle, currentBullets.toList()))
                                currentBullets = mutableListOf()
                            }
                            currentTitle = block.text
                        }
                        is DocBlock.BulletItem -> currentBullets.add(block.text)
                        is DocBlock.Paragraph -> if (block.text.isNotBlank()) currentBullets.add(block.text)
                        is DocBlock.CodeBlock -> currentBullets.add(block.text.take(180))
                        is DocBlock.TableBlock -> currentBullets.add("Table (${block.rows.size} rows)")
                        DocBlock.Divider -> {
                            if (currentBullets.isNotEmpty()) {
                                generated.add(SlideData(currentTitle, currentBullets.toList()))
                                currentBullets = mutableListOf()
                            }
                        }
                    }
                }
                if (currentBullets.isNotEmpty() || generated.isEmpty()) {
                    generated.add(SlideData(currentTitle, currentBullets.ifEmpty {
                        rawText.lines().filter { it.isNotBlank() }.take(8)
                    }))
                }
                generated
            }
            DocumentKind.SPREADSHEET -> {
                sheets.map { sheet ->
                    val header = sheet.rows.firstOrNull()?.joinToString(" | ") ?: sheet.name
                    val bullets = sheet.rows.drop(1).take(8).map { it.joinToString(" • ") }
                    SlideData(sheet.name, listOf(header) + bullets)
                }
            }
        }
    }

    val wordCount: Int
        get() = toPlainText().trim().split(Regex("\\s+")).count { it.isNotBlank() }

    val charCount: Int
        get() = toPlainText().length
}
