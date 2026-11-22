package com.example.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SecurityUtils {

    private const val MAX_ZIP_ENTRY_BYTES = 16L * 1024L * 1024L // 16 MB per XML entry cap
    private const val MAX_TOTAL_ZIP_BYTES = 48L * 1024L * 1024L // 48 MB total decompressed XML cap

    fun isSafeZipEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        if (normalized.split('/').any { it == ".." }) return false
        return true
    }

    fun readBoundedZipEntries(
        inputStream: InputStream,
        predicate: (String) -> Boolean
    ): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var totalRead = 0L
        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory && isSafeZipEntryName(entryName) && predicate(entryName)) {
                    val out = ByteArrayOutputStream()
                    var entryBytes = 0L
                    while (true) {
                        val count = zis.read(buffer)
                        if (count == -1) break
                        entryBytes += count
                        totalRead += count
                        if (entryBytes > MAX_ZIP_ENTRY_BYTES || totalRead > MAX_TOTAL_ZIP_BYTES) {
                            throw SecurityException("Archive entry exceeds safe offline memory limit")
                        }
                        out.write(buffer, 0, count)
                    }
                    result[entryName] = out.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Strip illegal XML 1.0 control characters
                    val code = ch.code
                    if (code == 0x9 || code == 0xA || code == 0xD || code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (cleaned.isBlank()) "untitled" else cleaned
    }
}
