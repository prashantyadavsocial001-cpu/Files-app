package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.model.MediaFormat
import com.example.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

data class ImageInfo(
    val width: Int,
    val height: Int,
    val exifSummary: List<Pair<String, String>>
)

data class MediaTechMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val bitrateBps: Long,
    val sampleRateHz: Int,
    val videoWidth: Int,
    val videoHeight: Int,
    val mimeType: String?,
    val albumArt: Bitmap?
)

object MediaEngine {

    suspend fun decodeSubsampledBitmap(
        context: Context,
        item: VaultItem,
        maxDimensionPx: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        val (file, shouldDelete) = DocumentEngine.resolveReadableFile(context, item)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sampleSize > maxDimensionPx) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = FileInputStream(file).use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val exifRotation = readExifRotationDegrees(file)
            if (exifRotation != 0f) {
                val matrix = Matrix().apply { postRotate(exifRotation) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated !== rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }
        } finally {
            if (shouldDelete) file.delete()
        }
    }

    suspend fun readImageInfo(context: Context, item: VaultItem): ImageInfo = withContext(Dispatchers.IO) {
        val (file, shouldDelete) = DocumentEngine.resolveReadableFile(context, item)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
            val exifList = mutableListOf<Pair<String, String>>()
            try {
                val exif = ExifInterface(file.absolutePath)
                fun addTag(label: String, tag: String) {
                    val v = exif.getAttribute(tag)
                    if (!v.isNullOrBlank()) exifList.add(label to v)
                }
                addTag("Camera Make", ExifInterface.TAG_MAKE)
                addTag("Camera Model", ExifInterface.TAG_MODEL)
                addTag("Date Taken", ExifInterface.TAG_DATETIME)
                addTag("Orientation", ExifInterface.TAG_ORIENTATION)
                addTag("ISO", ExifInterface.TAG_ISO_SPEED_RATINGS)
                addTag("Exposure", ExifInterface.TAG_EXPOSURE_TIME)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    exifList.add("GPS Location" to "${latLong[0]}, ${latLong[1]}")
                }
            } catch (_: Exception) {
                // Non-EXIF image format (PNG/BMP/GIF)
            }
            ImageInfo(
                width = bounds.outWidth.coerceAtLeast(0),
                height = bounds.outHeight.coerceAtLeast(0),
                exifSummary = exifList
            )
        } finally {
            if (shouldDelete) file.delete()
        }
    }

    fun transformBitmap(bitmap: Bitmap, rotationDegrees: Int, flipHorizontal: Boolean): Bitmap {
        if (rotationDegrees == 0 && !flipHorizontal) return bitmap
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            if (flipHorizontal) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun encodeImage(
        bitmap: Bitmap,
        targetFormat: MediaFormat,
        quality: Int,
        out: OutputStream
    ) = withContext(Dispatchers.IO) {
        val compressFormat = when (targetFormat) {
            MediaFormat.PNG -> Bitmap.CompressFormat.PNG
            MediaFormat.JPEG -> Bitmap.CompressFormat.JPEG
            MediaFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        bitmap.compress(compressFormat, quality.coerceIn(10, 100), out)
    }

    suspend fun stripExifFromJpegFile(file: File) = withContext(Dispatchers.IO) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext
        try {
            val tmp = File(file.parentFile, ".clean_${file.name}")
            FileOutputStream(tmp).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file.delete()
            tmp.renameTo(file)
        } finally {
            bmp.recycle()
        }
    }

    suspend fun extractMediaMetadata(context: Context, item: VaultItem): MediaTechMetadata =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                when {
                    item.filePath != null -> retriever.setDataSource(item.filePath)
                    item.contentUri != null -> retriever.setDataSource(context, item.contentUri)
                }
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                } else 0
                val videoW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val videoH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                val artBmp = if (shouldQueryEmbeddedPicture(context, item)) {
                    runCatching { retriever.embeddedPicture }.getOrNull()?.let { bytes ->
                        decodeAlbumArtSubsampled(bytes, maxSizePx = 512)
                    }
                } else {
                    null
                }

                MediaTechMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    bitrateBps = bitrate,
                    sampleRateHz = sampleRate,
                    videoWidth = videoW,
                    videoHeight = videoH,
                    mimeType = mime,
                    albumArt = artBmp
                )
            } catch (_: Exception) {
                MediaTechMetadata(
                    title = item.name,
                    artist = null,
                    album = null,
                    durationMs = 0L,
                    bitrateBps = 0L,
                    sampleRateHz = 0,
                    videoWidth = 0,
                    videoHeight = 0,
                    mimeType = item.mimeType,
                    albumArt = null
                )
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

    private fun shouldQueryEmbeddedPicture(context: Context, item: VaultItem): Boolean {
        if (item.category != com.example.model.FileCategory.AUDIO) return false
        val ext = item.extension.lowercase()
        if (ext in setOf("wav", "amr", "3gp", "mid", "midi")) return false

        // Inspect header/tail bytes so MediaMetadataRetrieverJNI isn't called on files with no cover atom/frame
        return try {
            val stream = when {
                item.filePath != null -> FileInputStream(File(item.filePath))
                item.contentUri != null -> context.contentResolver.openInputStream(item.contentUri)
                else -> null
            } ?: return false
            val foundInHead = stream.use { input ->
                val head = ByteArray(128 * 1024)
                val read = input.read(head)
                if (read <= 0) return false
                val headAscii = String(head, 0, read, Charsets.ISO_8859_1)
                headAscii.contains("APIC") ||
                    headAscii.contains("PIC\u0000") ||
                    headAscii.contains("covr") ||
                    headAscii.contains("METADATA_BLOCK_PICTURE") ||
                    (ext == "flac" && headAscii.startsWith("fLaC"))
            }
            if (foundInHead) return true
            if (ext == "m4a" && item.filePath != null) {
                val f = File(item.filePath)
                if (f.length() > 128L * 1024L) {
                    java.io.RandomAccessFile(f, "r").use { raf ->
                        val tailLen = minOf(128 * 1024, f.length().toInt())
                        raf.seek(f.length() - tailLen)
                        val tail = ByteArray(tailLen)
                        raf.readFully(tail)
                        return String(tail, Charsets.ISO_8859_1).contains("covr")
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeAlbumArtSubsampled(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > maxSizePx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun readExifRotationDegrees(file: File): Float {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
    }
}
