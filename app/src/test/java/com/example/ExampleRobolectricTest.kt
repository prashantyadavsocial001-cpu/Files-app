package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.engine.OfficeXmlCodec
import com.example.engine.PdfCodec
import com.example.engine.SecurityUtils
import com.example.engine.TextStructuredCodec
import com.example.model.DocBlock
import com.example.model.DocFormat
import com.example.model.DocumentKind
import com.example.model.SheetData
import com.example.model.SlideData
import com.example.model.UnifiedDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and zero internet permission verified`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FolioFlow", appName)

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        assertFalse(perms.contains(android.Manifest.permission.INTERNET))
    }

    @Test
    fun `zip slip paths are rejected`() {
        assertFalse(SecurityUtils.isSafeZipEntryName("../evil.xml"))
        assertFalse(SecurityUtils.isSafeZipEntryName("word/../../etc/passwd"))
        assertFalse(SecurityUtils.isSafeZipEntryName("/absolute/path.xml"))
        assertTrue(SecurityUtils.isSafeZipEntryName("word/document.xml"))
    }

    @Test
    fun `docx write and parse roundtrip preserves headings bullets and tables`() {
        val doc = UnifiedDocument(
            title = "Spec",
            sourceFormat = DocFormat.DOCX,
            kind = DocumentKind.PROSE,
            blocks = listOf(
                DocBlock.Heading(1, "Quarterly Review"),
                DocBlock.Paragraph("Offline summary paragraph."),
                DocBlock.BulletItem("Zero internet dependency"),
                DocBlock.TableBlock(listOf(listOf("ColA", "ColB"), listOf("10", "20")))
            )
        )
        val out = ByteArrayOutputStream()
        OfficeXmlCodec.writeDocx(doc, out)
        val parsed = OfficeXmlCodec.parseDocx("Spec.docx", ByteArrayInputStream(out.toByteArray()))
        val text = parsed.toPlainText()
        assertTrue(text.contains("Quarterly Review"))
        assertTrue(text.contains("Offline summary paragraph."))
        assertTrue(text.contains("Zero internet dependency"))
        assertTrue(parsed.blocks.any { it is DocBlock.TableBlock })
    }

    @Test
    fun `xlsx write and parse roundtrip preserves sheets and cells`() {
        val doc = UnifiedDocument(
            title = "Ledger",
            sourceFormat = DocFormat.XLSX,
            kind = DocumentKind.SPREADSHEET,
            sheets = listOf(
                SheetData("Budget", listOf(listOf("Item", "Cost"), listOf("Server", "0")))
            )
        )
        val out = ByteArrayOutputStream()
        OfficeXmlCodec.writeXlsx(doc, out)
        val parsed = OfficeXmlCodec.parseXlsx("Ledger.xlsx", ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, parsed.sheets.size)
        assertEquals("Budget", parsed.sheets[0].name)
        assertEquals("Item", parsed.sheets[0].rows[0][0])
        assertEquals("0", parsed.sheets[0].rows[1][1])
    }

    @Test
    fun `pptx write and parse roundtrip preserves slides`() {
        val doc = UnifiedDocument(
            title = "Deck",
            sourceFormat = DocFormat.PPTX,
            kind = DocumentKind.PRESENTATION,
            slides = listOf(
                SlideData("Intro", listOf("Point Alpha", "Point Beta")),
                SlideData("Summary", listOf("Point Gamma"))
            )
        )
        val out = ByteArrayOutputStream()
        OfficeXmlCodec.writePptx(doc, out)
        val parsed = OfficeXmlCodec.parsePptx("Deck.pptx", ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, parsed.slides.size)
        assertEquals("Intro", parsed.slides[0].title)
        assertTrue(parsed.slides[0].bullets.contains("Point Alpha"))
        assertEquals("Summary", parsed.slides[1].title)
    }

    @Test
    fun `pdf text stream extractor parses uncompressed pdf streams offline`() {
        val samplePdf = "%PDF-1.4\n1 0 obj << /Length 44 >> stream\nBT /F1 12 Tf 72 720 Td (Hello Offline PDF) Tj ET\nendstream endobj\n%%EOF"
        val tmp = java.io.File.createTempFile("sample_test_", ".pdf")
        try {
            tmp.writeText(samplePdf)
            val parsed = PdfCodec.parsePdfFile("sample.pdf", tmp)
            assertTrue(parsed.toPlainText().contains("Hello Offline PDF"))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `rtf and html interchange works offline`() {
        val doc = UnifiedDocument(
            title = "Notes",
            sourceFormat = DocFormat.MD,
            kind = DocumentKind.PROSE,
            blocks = listOf(DocBlock.Heading(1, "Header"), DocBlock.Paragraph("Body text"))
        )
        val rtfOut = ByteArrayOutputStream()
        TextStructuredCodec.write(doc, DocFormat.RTF, rtfOut)
        val parsedRtf = TextStructuredCodec.parse("notes.rtf", DocFormat.RTF, rtfOut.toByteArray())
        assertTrue(parsedRtf.toPlainText().contains("Header"))
        assertTrue(parsedRtf.toPlainText().contains("Body text"))

        val htmlOut = ByteArrayOutputStream()
        TextStructuredCodec.write(doc, DocFormat.HTML, htmlOut)
        val parsedHtml = TextStructuredCodec.parse("notes.html", DocFormat.HTML, htmlOut.toByteArray())
        assertTrue(parsedHtml.toPlainText().contains("Body text"))
    }

    @Test
    fun `csv to json and markdown interchange works offline`() {
        val csvBytes = "Name,Role\nAlice,Engineer\nBob,Designer".toByteArray()
        val doc = TextStructuredCodec.parse("team.csv", DocFormat.CSV, csvBytes)
        val jsonOut = ByteArrayOutputStream()
        TextStructuredCodec.write(doc, DocFormat.JSON, jsonOut)
        val jsonStr = jsonOut.toString("UTF-8")
        assertTrue(jsonStr.contains("Alice"))
        assertTrue(jsonStr.contains("Engineer"))

        val mdOut = ByteArrayOutputStream()
        TextStructuredCodec.write(doc, DocFormat.MD, mdOut)
        assertTrue(mdOut.toString("UTF-8").contains("| Alice | Engineer |"))
    }
}
