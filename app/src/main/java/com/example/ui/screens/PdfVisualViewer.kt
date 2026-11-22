package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.engine.DocumentEngine
import com.example.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfVisualViewer(
    item: VaultItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageCount by remember(item.id) { mutableIntStateOf(0) }
    var currentPage by remember(item.id) { mutableIntStateOf(0) }
    var currentBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var errorMsg by remember(item.id) { mutableStateOf<String?>(null) }
    var isRendering by remember(item.id) { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Bounded 12 MB LRU cache so large PDFs stay ultra-low memory
    val cache = remember(item.id) {
        object : LruCache<Int, Bitmap>(12 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    var localPdfPair by remember(item.id) { mutableStateOf<Pair<File, Boolean>?>(null) }

    DisposableEffect(item.id) {
        onDispose {
            cache.evictAll()
            localPdfPair?.let { (file, shouldDelete) ->
                if (shouldDelete) file.delete()
            }
            localPdfPair = null
        }
    }

    LaunchedEffect(item.id, currentPage) {
        isRendering = true
        errorMsg = null
        try {
            val cached = cache.get(currentPage)
            if (cached != null && !cached.isRecycled) {
                currentBitmap = cached
                isRendering = false
                return@LaunchedEffect
            }
            val rendered = withContext(Dispatchers.IO) {
                val pair = localPdfPair ?: DocumentEngine.resolveReadableFile(context, item).also {
                    localPdfPair = it
                }
                ParcelFileDescriptor.open(pair.first, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        if (renderer.pageCount == 0) return@withContext null
                        val safePage = currentPage.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safePage).use { page ->
                            val targetW = 1240
                            val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                                .toInt()
                                .coerceIn(400, 2200)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
            if (rendered != null) {
                cache.put(currentPage, rendered)
                currentBitmap = rendered
            } else {
                errorMsg = "PDF contains 0 pages."
            }
        } catch (e: Exception) {
            errorMsg = "Unable to render PDF page: ${e.message}"
        } finally {
            isRendering = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A2230))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4.5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                errorMsg != null -> {
                    Text(
                        text = errorMsg ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                isRendering && currentBitmap == null -> {
                    CircularProgressIndicator()
                }
                currentBitmap != null -> {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
            }
        }

        // Page Scrubber Bar
        Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (currentPage > 0) {
                            currentPage--
                            scale = 1f
                            offset = Offset.Zero
                        }
                    },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }

                Text(
                    text = if (pageCount > 0) "Page ${currentPage + 1} of $pageCount" else "PDF Viewer",
                    style = MaterialTheme.typography.labelLarge
                )

                Row {
                    IconButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = "Reset zoom")
                    }
                    IconButton(
                        onClick = {
                            if (currentPage < pageCount - 1) {
                                currentPage++
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        enabled = currentPage < pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}
