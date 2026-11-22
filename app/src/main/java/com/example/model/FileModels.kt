package com.example.model

import android.net.Uri

enum class FileCategory(val label: String) {
    ALL("All"),
    DOCUMENT("Documents"),
    SPREADSHEET("Sheets"),
    PRESENTATION("Decks"),
    IMAGE("Images"),
    AUDIO("Audio"),
    VIDEO("Video"),
    FOLDER("Folders")
}

enum class DocFormat(
    val extension: String,
    val displayName: String,
    val mimeType: String,
    val category: FileCategory,
    val canWrite: Boolean = true
) {
    PDF("pdf", "PDF Document", "application/pdf", FileCategory.DOCUMENT, canWrite = true),
    DOCX("docx", "Word (DOCX)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileCategory.DOCUMENT, canWrite = true),
    DOC("doc", "Legacy Word (DOC)", "application/msword", FileCategory.DOCUMENT, canWrite = false),
    XLSX("xlsx", "Excel (XLSX)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FileCategory.SPREADSHEET, canWrite = true),
    XLS("xls", "Legacy Excel (XLS)", "application/vnd.ms-excel", FileCategory.SPREADSHEET, canWrite = false),
    PPTX("pptx", "PowerPoint (PPTX)", "application/vnd.openxmlformats-officedocument.presentationml.presentation", FileCategory.PRESENTATION, canWrite = true),
    PPT("ppt", "Legacy PPT", "application/vnd.ms-powerpoint", FileCategory.PRESENTATION, canWrite = false),
    TXT("txt", "Plain Text", "text/plain", FileCategory.DOCUMENT, canWrite = true),
    MD("md", "Markdown", "text/markdown", FileCategory.DOCUMENT, canWrite = true),
    CSV("csv", "CSV Table", "text/csv", FileCategory.SPREADSHEET, canWrite = true),
    TSV("tsv", "TSV Table", "text/tab-separated-values", FileCategory.SPREADSHEET, canWrite = true),
    HTML("html", "HTML Page", "text/html", FileCategory.DOCUMENT, canWrite = true),
    JSON("json", "JSON Data", "application/json", FileCategory.DOCUMENT, canWrite = true),
    XML("xml", "XML Document", "application/xml", FileCategory.DOCUMENT, canWrite = true),
    RTF("rtf", "Rich Text (RTF)", "application/rtf", FileCategory.DOCUMENT, canWrite = true);

    companion object {
        fun fromExtension(ext: String): DocFormat? {
            val clean = ext.lowercase().trimStart('.')
            return when (clean) {
                "pdf" -> PDF
                "docx" -> DOCX
                "doc" -> DOC
                "xlsx", "xlsm" -> XLSX
                "xls" -> XLS
                "pptx" -> PPTX
                "ppt" -> PPT
                "txt", "log", "ini", "cfg", "kt", "java", "py", "js", "yml", "yaml" -> TXT
                "md", "markdown" -> MD
                "csv" -> CSV
                "tsv" -> TSV
                "html", "htm" -> HTML
                "json" -> JSON
                "xml" -> XML
                "rtf" -> RTF
                else -> null
            }
        }

        val writableFormats: List<DocFormat>
            get() = entries.filter { it.canWrite }
    }
}

enum class MediaFormat(
    val extension: String,
    val displayName: String,
    val mimeType: String,
    val category: FileCategory,
    val canEncodeImage: Boolean = false
) {
    PNG("png", "PNG Image", "image/png", FileCategory.IMAGE, canEncodeImage = true),
    JPEG("jpg", "JPEG Image", "image/jpeg", FileCategory.IMAGE, canEncodeImage = true),
    WEBP("webp", "WebP Image", "image/webp", FileCategory.IMAGE, canEncodeImage = true),
    GIF("gif", "GIF Image", "image/gif", FileCategory.IMAGE, canEncodeImage = false),
    BMP("bmp", "BMP Image", "image/bmp", FileCategory.IMAGE, canEncodeImage = false),
    HEIF("heic", "HEIC/HEIF Image", "image/heif", FileCategory.IMAGE, canEncodeImage = false),
    MP3("mp3", "MP3 Audio", "audio/mpeg", FileCategory.AUDIO),
    M4A("m4a", "M4A / AAC Audio", "audio/mp4", FileCategory.AUDIO),
    AAC("aac", "AAC Audio", "audio/aac", FileCategory.AUDIO),
    OGG("ogg", "OGG Vorbis", "audio/ogg", FileCategory.AUDIO),
    OPUS("opus", "Opus Audio", "audio/opus", FileCategory.AUDIO),
    FLAC("flac", "FLAC Lossless", "audio/flac", FileCategory.AUDIO),
    WAV("wav", "WAV PCM Audio", "audio/wav", FileCategory.AUDIO),
    MP4("mp4", "MP4 Video", "video/mp4", FileCategory.VIDEO),
    MKV("mkv", "MKV Video", "video/x-matroska", FileCategory.VIDEO),
    WEBM("webm", "WebM Video", "video/webm", FileCategory.VIDEO),
    THREE_GP("3gp", "3GP Video", "video/3gpp", FileCategory.VIDEO);

    companion object {
        fun fromExtension(ext: String): MediaFormat? {
            return when (ext.lowercase().trimStart('.')) {
                "png" -> PNG
                "jpg", "jpeg" -> JPEG
                "webp" -> WEBP
                "gif" -> GIF
                "bmp" -> BMP
                "heic", "heif" -> HEIF
                "mp3" -> MP3
                "m4a" -> M4A
                "aac" -> AAC
                "ogg", "oga" -> OGG
                "opus" -> OPUS
                "flac" -> FLAC
                "wav" -> WAV
                "mp4", "m4v" -> MP4
                "mkv" -> MKV
                "webm" -> WEBM
                "3gp" -> THREE_GP
                else -> null
            }
        }
    }
}

enum class StorageOrigin { VAULT, DEVICE_SAF }

enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    DATE_DESC("Newest first"),
    SIZE_DESC("Largest first"),
    TYPE("Type")
}

data class VaultItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val category: FileCategory,
    val origin: StorageOrigin,
    val filePath: String? = null,
    val contentUri: Uri? = null,
    val isPinned: Boolean = false
) {
    val docFormat: DocFormat? get() = if (isDirectory) null else DocFormat.fromExtension(extension)
    val mediaFormat: MediaFormat? get() = if (isDirectory) null else MediaFormat.fromExtension(extension)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}
