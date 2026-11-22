package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.ImageInfo
import com.example.engine.MediaEngine
import com.example.model.MediaFormat
import com.example.model.VaultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    item: VaultItem,
    onBack: () -> Unit,
    onConvertImage: (VaultItem, MediaFormat?, Boolean, Int, Int, Boolean) -> Unit,
    onStripExif: (VaultItem, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var imageInfo by remember(item.id) { mutableStateOf<ImageInfo?>(null) }
    var isLoading by remember(item.id) { mutableStateOf(true) }
    var rotationDeg by remember(item.id) { mutableIntStateOf(0) }
    var flipH by remember(item.id) { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showExifDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var reloadTrigger by remember(item.id) { mutableIntStateOf(0) }

    LaunchedEffect(item.id, reloadTrigger) {
        isLoading = true
        bitmap = MediaEngine.decodeSubsampledBitmap(context, item, maxDimensionPx = 2048)
        imageInfo = MediaEngine.readImageInfo(context, item)
        isLoading = false
    }

    DisposableEffect(item.id) {
        onDispose {
            bitmap = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        imageInfo?.let { info ->
                            Text(
                                text = "${info.width}×${info.height} • ${item.extension.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("image_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExifDialog = true },
                        modifier = Modifier.testTag("image_exif_info")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "EXIF & Privacy Info")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { rotationDeg = (rotationDeg + 90) % 360 }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rotate")
                    }
                    OutlinedButton(onClick = { flipH = !flipH }) {
                        Icon(Icons.Filled.Flip, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Flip")
                    }
                    Button(
                        onClick = { showConvertDialog = true },
                        modifier = Modifier.testTag("image_convert_btn")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF090D14))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 6f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                val bmp = bitmap
                if (bmp == null) {
                    Text("Unable to decode image.", color = Color.White)
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale * if (flipH) -1f else 1f
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                rotationZ = rotationDeg.toFloat()
                            }
                    )
                }
            }
        }
    }

    if (showExifDialog) {
        val info = imageInfo
        AlertDialog(
            onDismissRequest = { showExifDialog = false },
            title = { Text("Image & EXIF Privacy") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dimensions: ${info?.width ?: 0} × ${info?.height ?: 0} px",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (info == null || info.exifSummary.isEmpty()) {
                        Text(
                            text = "No camera or GPS EXIF metadata detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        info.exifSummary.forEach { (k, v) ->
                            Text("$k: $v", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            dismissButton = {
                if (item.extension.lowercase() in listOf("jpg", "jpeg") && item.filePath != null) {
                    TextButton(onClick = {
                        onStripExif(item) {
                            reloadTrigger++
                            showExifDialog = false
                        }
                    }) {
                        Icon(Icons.Filled.CleaningServices, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Strip EXIF")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExifDialog = false }) { Text("Done") }
            }
        )
    }

    if (showConvertDialog) {
        var targetFmt by remember { mutableStateOf<MediaFormat?>(MediaFormat.WEBP) }
        var asPdf by remember { mutableStateOf(false) }
        var quality by remember { mutableIntStateOf(90) }
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("Export / Convert Image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(MediaFormat.PNG, MediaFormat.JPEG, MediaFormat.WEBP).forEach { fmt ->
                            FilterChip(
                                selected = !asPdf && targetFmt == fmt,
                                onClick = {
                                    asPdf = false
                                    targetFmt = fmt
                                },
                                label = { Text(fmt.extension.uppercase()) }
                            )
                        }
                        FilterChip(
                            selected = asPdf,
                            onClick = { asPdf = true },
                            label = { Text("PDF") }
                        )
                    }
                    if (!asPdf && targetFmt != MediaFormat.PNG) {
                        Text("Quality: $quality%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = quality.toFloat(),
                            onValueChange = { quality = it.toInt() },
                            valueRange = 35f..100f
                        )
                    }
                    Text(
                        text = "Applies current rotation ($rotationDeg°) & flip and strips EXIF GPS data automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    showConvertDialog = false
                    onConvertImage(item, targetFmt, asPdf, quality, rotationDeg, flipH)
                }) {
                    Text("Save to Vault")
                }
            }
        )
    }
}
