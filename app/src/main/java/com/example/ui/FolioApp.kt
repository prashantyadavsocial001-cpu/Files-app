package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.DocFormat
import com.example.model.StorageOrigin
import com.example.model.VaultItem
import com.example.ui.components.FileInspectorDialog
import com.example.ui.screens.ConverterStudioScreen
import com.example.ui.screens.DocumentStudioScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MediaHubScreen
import com.example.ui.screens.MediaPlayerScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.WorkspaceHomeScreen
import com.example.viewmodel.FolioViewModel
import kotlinx.coroutines.launch

object FolioRoutes {
    const val HOME = "route_home"
    const val FILES = "route_files"
    const val CONVERT = "route_convert"
    const val MEDIA = "route_media"
    const val DOC_STUDIO = "route_doc_studio"
    const val IMAGE_STUDIO = "route_image_studio"
    const val MEDIA_PLAYER = "route_media_player"
    const val SETTINGS = "route_settings"
}

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val mainTabs = listOf(
    NavTab(FolioRoutes.HOME, "Studio", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(FolioRoutes.FILES, "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    NavTab(FolioRoutes.CONVERT, "Convert", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    NavTab(FolioRoutes.MEDIA, "Media", Icons.Filled.PermMedia, Icons.Outlined.PermMedia)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolioApp(
    viewModel: FolioViewModel,
    incomingUri: Uri? = null,
    onConsumeIncomingUri: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val mountedFolders by viewModel.mountedFolders.collectAsStateWithLifecycle()
    val fileState by viewModel.fileManagerState.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaGalleryItems.collectAsStateWithLifecycle()
    val activeDocItem by viewModel.activeDocItem.collectAsStateWithLifecycle()
    val docLoadState by viewModel.docLoadState.collectAsStateWithLifecycle()
    val activeImageItem by viewModel.activeImageItem.collectAsStateWithLifecycle()
    val activeMediaItem by viewModel.activeMediaItem.collectAsStateWithLifecycle()
    val activeMediaQueue by viewModel.activeMediaQueue.collectAsStateWithLifecycle()
    val converterItem by viewModel.converterItem.collectAsStateWithLifecycle()
    val isConverting by viewModel.isConverting.collectAsStateWithLifecycle()
    val lastConvertedItem by viewModel.lastConvertedItem.collectAsStateWithLifecycle()
    val audioBarState by viewModel.audioBarState.collectAsStateWithLifecycle()

    // Wire viewmodel navigation callbacks
    LaunchedEffect(navController) {
        viewModel.onNavigateToDocStudio = { navController.navigate(FolioRoutes.DOC_STUDIO) }
        viewModel.onNavigateToImageStudio = { navController.navigate(FolioRoutes.IMAGE_STUDIO) }
        viewModel.onNavigateToMediaPlayer = { navController.navigate(FolioRoutes.MEDIA_PLAYER) }
        viewModel.onNavigateToConverter = {
            navController.navigate(FolioRoutes.CONVERT) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            viewModel.importExternalUris(listOf(incomingUri), copyToVault = false, openFirst = true)
            onConsumeIncomingUri()
        }
    }

    // SAF & Photo Picker Launchers (100% Google Play Policy Compliant)
    val openAnyFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalUris(uris, copyToVault = true, openFirst = uris.size == 1)
        }
    }

    val pickConverterSourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectConverterUri(uri)
        }
    }

    val mountFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.mountSafFolder(treeUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalUris(uris, copyToVault = true, openFirst = uris.size == 1)
        }
    }

    var pendingExportPair by remember { mutableStateOf<Pair<VaultItem, DocFormat>?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val pair = pendingExportPair
        pendingExportPair = null
        if (destUri != null && pair != null) {
            viewModel.convertDocumentToExternalUri(pair.first, pair.second, destUri)
        }
    }

    // Dialog states
    var actionSheetItem by remember { mutableStateOf<VaultItem?>(null) }
    var renameTargetItem by remember { mutableStateOf<VaultItem?>(null) }
    var deleteTargetItem by remember { mutableStateOf<VaultItem?>(null) }
    var inspectorData by remember { mutableStateOf<Pair<VaultItem, String>?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewDocDialog by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: FolioRoutes.HOME
    val isTopLevelTab = mainTabs.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isTopLevelTab && !useRail) {
                    Column {
                        if (audioBarState.currentItem != null) {
                            AudioMiniBar(
                                item = audioBarState.currentItem!!,
                                isPlaying = audioBarState.isPlaying,
                                onTogglePlay = { viewModel.toggleMiniPlayerPlayPause() },
                                onOpenPlayer = { navController.navigate(FolioRoutes.MEDIA_PLAYER) },
                                onClose = { viewModel.stopMiniPlayer() }
                            )
                        }
                        NavigationBar {
                            mainTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(FolioRoutes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_${tab.label.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelTab && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        mainTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(FolioRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = FolioRoutes.HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(FolioRoutes.HOME) {
                            WorkspaceHomeScreen(
                                recentFiles = recentFiles,
                                vaultUsageBytes = fileState.vaultUsageBytes,
                                onOpenAnyFile = { openAnyFilesLauncher.launch(arrayOf("*/*")) },
                                onOpenMediaPicker = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onCreateNewDoc = { showNewDocDialog = true },
                                onGoToConverter = {
                                    navController.navigate(FolioRoutes.CONVERT) { launchSingleTop = true }
                                },
                                onGoToFiles = {
                                    navController.navigate(FolioRoutes.FILES) { launchSingleTop = true }
                                },
                                onGoToSettings = { navController.navigate(FolioRoutes.SETTINGS) },
                                onOpenItem = { viewModel.openItem(it) },
                                onItemMore = { actionSheetItem = it }
                            )
                        }

                        composable(FolioRoutes.FILES) {
                            FileManagerScreen(
                                state = fileState,
                                mountedFolders = mountedFolders,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onCategoryChange = { viewModel.setCategoryFilter(it) },
                                onSortChange = { viewModel.setSortOption(it) },
                                onSelectVaultRoot = { viewModel.openVaultRoot() },
                                onSelectVaultCrumb = { viewModel.openVaultSubfolder(it) },
                                onSelectMountedFolder = { viewModel.openMountedFolder(it) },
                                onUnmountFolder = { viewModel.unmountSafFolder(it) },
                                onMountDeviceFolder = { mountFolderLauncher.launch(null) },
                                onImportFiles = { openAnyFilesLauncher.launch(arrayOf("*/*")) },
                                onNavigateUp = { viewModel.navigateUpDirectory() },
                                onCreateFolder = { showNewFolderDialog = true },
                                onCreateDocument = { showNewDocDialog = true },
                                onOpenItem = { viewModel.openItem(it) },
                                onItemMore = { actionSheetItem = it }
                            )
                        }

                        composable(FolioRoutes.CONVERT) {
                            ConverterStudioScreen(
                                sourceItem = converterItem,
                                isConverting = isConverting,
                                lastConvertedItem = lastConvertedItem,
                                onPickSourceFile = { pickConverterSourceLauncher.launch(arrayOf("*/*")) },
                                onConvertDocumentToVault = { item, fmt ->
                                    viewModel.convertDocumentToVault(item, fmt)
                                },
                                onExportDocumentToDevice = { item, fmt ->
                                    pendingExportPair = item to fmt
                                    val base = item.name.substringBeforeLast('.')
                                    createDocumentLauncher.launch("$base.${fmt.extension}")
                                },
                                onConvertImageToVault = { item, fmt, asPdf, quality ->
                                    viewModel.convertImageToVault(item, fmt, asPdf, quality)
                                },
                                onOpenConverted = { viewModel.openItem(it) }
                            )
                        }

                        composable(FolioRoutes.MEDIA) {
                            MediaHubScreen(
                                mediaItems = mediaItems,
                                onPickPhotosVideos = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onPickAudioOrAnyMedia = {
                                    openAnyFilesLauncher.launch(arrayOf("audio/*", "video/*", "image/*"))
                                },
                                onOpenItem = { viewModel.openItem(it) }
                            )
                        }

                        composable(FolioRoutes.DOC_STUDIO) {
                            val item = activeDocItem
                            if (item != null) {
                                DocumentStudioScreen(
                                    item = item,
                                    loadState = docLoadState,
                                    editorFontSizeSp = settings.editorFontSizeSp,
                                    onBack = { navController.popBackStack() },
                                    onUpdateDocument = { viewModel.updateActiveDocument(it) },
                                    onSaveDocument = { viewModel.saveActiveDocument() },
                                    onOpenConverter = { viewModel.openInConverter(it) }
                                )
                            }
                        }

                        composable(FolioRoutes.IMAGE_STUDIO) {
                            val item = activeImageItem
                            if (item != null) {
                                ImageStudioScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onConvertImage = { src, fmt, asPdf, q, rot, flip ->
                                        viewModel.convertImageToVault(src, fmt, asPdf, q, rot, flip)
                                    },
                                    onStripExif = { src, done -> viewModel.stripImageExif(src, done) }
                                )
                            }
                        }

                        composable(FolioRoutes.MEDIA_PLAYER) {
                            val item = activeMediaItem
                            if (item != null) {
                                MediaPlayerScreen(
                                    item = item,
                                    queue = activeMediaQueue,
                                    player = viewModel.getOrCreatePlayer(),
                                    onSelectQueueItem = { viewModel.openMediaPlayback(it) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable(FolioRoutes.SETTINGS) {
                            SettingsPrivacyScreen(
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onThemeChange = { viewModel.setThemeMode(it) },
                                onPaperSizeChange = { viewModel.setPdfPaperSize(it) },
                                onAutoStripExifChange = { viewModel.setAutoStripExif(it) },
                                onFontSizeChange = { viewModel.setEditorFontSize(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // File Options Modal Bottom Sheet
    actionSheetItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { actionSheetItem = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ListItem(
                    headlineContent = { Text("Open") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    modifier = Modifier.clickable {
                        actionSheetItem = null
                        viewModel.openItem(item)
                    }
                )
                if (!item.isDirectory && (item.docFormat != null || item.category == com.example.model.FileCategory.IMAGE)) {
                    ListItem(
                        headlineContent = { Text("Convert Format") },
                        leadingContent = { Icon(Icons.Filled.SwapHoriz, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            viewModel.openInConverter(item)
                        }
                    )
                }
                if (!item.isDirectory) {
                    ListItem(
                        headlineContent = { Text(if (item.isPinned) "Unpin from Recent" else "Pin to Recent") },
                        leadingContent = { Icon(Icons.Filled.PushPin, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            viewModel.togglePin(item)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = { Icon(Icons.Filled.Share, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            val intent = viewModel.buildShareIntent(item)
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, "Share ${item.name}"))
                            } else {
                                viewModel.emitToast("Unable to share this item")
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Security & SHA-256 Checksum") },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            scope.launch {
                                val hash = viewModel.getSha256(item)
                                inspectorData = item to hash
                            }
                        }
                    )
                }
                if (item.origin == StorageOrigin.VAULT && !item.isDirectory) {
                    ListItem(
                        headlineContent = { Text("Duplicate") },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            viewModel.duplicateItem(item)
                        }
                    )
                }
                if (item.origin == StorageOrigin.VAULT) {
                    ListItem(
                        headlineContent = { Text("Rename") },
                        leadingContent = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        modifier = Modifier.clickable {
                            actionSheetItem = null
                            renameTargetItem = item
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        actionSheetItem = null
                        deleteTargetItem = item
                    }
                )
            }
        }
    }

    // Rename Dialog
    renameTargetItem?.let { item ->
        var newName by remember(item.id) { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renameTargetItem = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    val clean = newName.trim()
                    if (clean.isNotEmpty()) viewModel.renameItem(item, clean)
                    renameTargetItem = null
                }) { Text("Rename") }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This permanently removes the file from offline storage.") },
            dismissButton = {
                TextButton(onClick = { deleteTargetItem = null }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteItem(item)
                    deleteTargetItem = null
                }) { Text("Delete") }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Vault Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotBlank()) viewModel.createNewFolder(folderName)
                    showNewFolderDialog = false
                }) { Text("Create") }
            }
        )
    }

    // New Document / Sheet / Deck Dialog
    if (showNewDocDialog) {
        var docName by remember { mutableStateOf("Untitled") }
        var selectedFormat by remember { mutableStateOf(DocFormat.DOCX) }
        AlertDialog(
            onDismissRequest = { showNewDocDialog = false },
            title = { Text("Create Offline Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = { docName = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Format", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            DocFormat.DOCX,
                            DocFormat.XLSX,
                            DocFormat.PPTX,
                            DocFormat.PDF,
                            DocFormat.MD,
                            DocFormat.TXT,
                            DocFormat.CSV,
                            DocFormat.JSON
                        ).forEach { fmt ->
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                label = { Text(fmt.extension.uppercase()) }
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDocDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    showNewDocDialog = false
                    viewModel.createNewDocument(docName, selectedFormat) {
                        navController.navigate(FolioRoutes.DOC_STUDIO)
                    }
                }) { Text("Create & Open") }
            }
        )
    }

    // SHA-256 Metadata Inspector Dialog
    inspectorData?.let { (item, hash) ->
        FileInspectorDialog(
            item = item,
            sha256 = hash,
            onDismiss = { inspectorData = null }
        )
    }
}

@Composable
private fun AudioMiniBar(
    item: VaultItem,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpenPlayer: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isPlaying) "Playing offline" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Stop mini player")
            }
        }
    }
}
