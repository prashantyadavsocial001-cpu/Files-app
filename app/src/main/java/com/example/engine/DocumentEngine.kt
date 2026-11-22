package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.model.DocFormat
import com.example.model.UnifiedDocument
import com.example.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object DocumentEngine {

    private const val MAX_TEXT_READ_BYTES = 12 * 1024 * 1024

    suspend fun loadDocument(context: Context, item: VaultItem): UnifiedDocument =
        withContext(Dispatchers.IO) {
            val format = item.docFormat ?: DocFormat.TXT
            when (format) {
                DocFormat.PDF -> {
                    val tempOrFile = resolveReadableFile(context, item)
                    try {
                        PdfCodec.parsePdfFile(item.name, tempOrFile.first)
                    } finally {
                        if (tempOrFile.second) tempOrFile.first.delete()
                    }
                }
                DocFormat.DOCX -> openInputStream(context, item).use { OfficeXmlCodec.parseDocx(item.name, it) }
                DocFormat.XLSX -> openInputStream(context, item).use { OfficeXmlCodec.parseXlsx(item.name, it) }
                DocFormat.PPTX -> openInputStream(context, item).use { OfficeXmlCodec.parsePptx(item.name, it) }
                DocFormat.DOC, DocFormat.XLS, DocFormat.PPT -> {
                    val bytes = openInputStream(context, item).use { readBoundedBytes(it) }
                    OfficeXmlCodec.parseLegacyBinaryOffice(item.name, format, bytes)
                }
                else -> {
                    val bytes = openInputStream(context, item).use { readBoundedBytes(it) }
                    TextStructuredCodec.parse(item.name, format, bytes)
                }
            }
        }

    suspend fun writeDocument(
        doc: UnifiedDocument,
        targetFormat: DocFormat,
        outputStream: OutputStream,
        paperSize: PdfPaperSize = PdfPaperSize.A4
    ) = withContext(Dispatchers.IO) {
        when (targetFormat) {
            DocFormat.PDF -> PdfCodec.writePdf(doc, outputStream, paperSize)
            DocFormat.DOCX -> OfficeXmlCodec.writeDocx(doc, outputStream)
            DocFormat.XLSX -> OfficeXmlCodec.writeXlsx(doc, outputStream)
            DocFormat.PPTX -> OfficeXmlCodec.writePptx(doc, outputStream)
            DocFormat.DOC, DocFormat.XLS, DocFormat.PPT ->
                throw IllegalArgumentException("Legacy binary format ${targetFormat.extension} is read-only. Export to modern ${targetFormat.extension}x or PDF instead.")
            else -> TextStructuredCodec.write(doc, targetFormat, outputStream)
        }
    }

    suspend fun saveDocumentToItem(
        context: Context,
        item: VaultItem,
        doc: UnifiedDocument,
        targetFormat: DocFormat = item.docFormat ?: DocFormat.TXT,
        paperSize: PdfPaperSize = PdfPaperSize.A4
    ) = withContext(Dispatchers.IO) {
        if (item.filePath != null) {
            val file = File(item.filePath)
            val tmp = File(file.parentFile, ".tmp_${System.currentTimeMillis()}_${file.name}")
            FileOutputStream(tmp).use { out ->
                writeDocument(doc, targetFormat, out, paperSize)
            }
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } else if (item.contentUri != null) {
            context.contentResolver.openOutputStream(item.contentUri, "wt")?.use { out ->
                writeDocument(doc, targetFormat, out, paperSize)
            } ?: throw IllegalStateException("Storage provider did not allow write access to this URI.")
        } else {
            throw IllegalStateException("No writable path or URI found for ${item.name}")
        }
    }

    fun openInputStream(context: Context, item: VaultItem): InputStream {
        return when {
            item.filePath != null -> FileInputStream(File(item.filePath))
            item.contentUri != null -> context.contentResolver.openInputStream(item.contentUri)
                ?: throw IllegalStateException("Cannot open content URI: ${item.contentUri}")
            else -> throw IllegalStateException("Invalid file reference")
        }
    }

    /**
     * Returns Pair(File, shouldDeleteAfterUse) for native ParcelFileDescriptor APIs like PdfRenderer.
     */
    fun resolveReadableFile(context: Context, item: VaultItem): Pair<File, Boolean> {
        if (item.filePath != null) {
            return File(item.filePath) to false
        }
        val uri = item.contentUri ?: throw IllegalStateException("No URI or path for item")
        val tmp = File.createTempFile("folio_view_", ".${item.extension.ifBlank { "bin" }}", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        } ?: throw IllegalStateException("Unable to read URI: $uri")
        return tmp to true
    }

    private fun readBoundedBytes(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            total += r
            if (total > MAX_TEXT_READ_BYTES) {
                out.write(buf, 0, r)
                break
            }
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        } ?: throw IllegalStateException("Unable to read selected file")
    }
}
