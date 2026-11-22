package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.MountedFolderEntity
import com.example.data.SettingsStore
import com.example.data.VaultRepository
import com.example.engine.DocumentEngine
import com.example.engine.MediaEngine
import com.example.engine.PdfCodec
import com.example.engine.PdfPaperSize
import com.example.engine.SecurityUtils
import com.example.model.DocFormat
import com.example.model.FileCategory
import com.example.model.MediaFormat
import com.example.model.SortOption
import com.example.model.StorageOrigin
import com.example.model.UnifiedDocument
import com.example.model.VaultItem
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface DocLoadState {
    data object Idle : DocLoadState
    data object Loading : DocLoadState
    data class Success(val document: UnifiedDocument, val isModified: Boolean = false) : DocLoadState
    data class Error(val message: String) : DocLoadState
}

data class MountedLocation(
    val treeUri: Uri,
    val displayName: String,
    val breadcrumbIds: List<Pair<String, String>>
)

data class FileManagerState(
    val isBrowsingSaf: Boolean = false,
    val vaultBreadcrumbs: List<File> = emptyList(),
    val mountedLocation: MountedLocation? = null,
    val items: List<VaultItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val categoryFilter: FileCategory = FileCategory.ALL,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val vaultUsageBytes: Long = 0L
)

data class AudioPlaybackBarState(
    val currentItem: VaultItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class FolioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = VaultRepository(application, db.vaultDao())
    private val settingsStore = SettingsStore(application)

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val recentFiles: StateFlow<List<VaultItem>> = repository.recentFilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mountedFolders: StateFlow<List<MountedFolderEntity>> = repository.mountedFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fileManagerState = MutableStateFlow(
        FileManagerState(vaultBreadcrumbs = listOf(repository.vaultRoot))
    )
    val fileManagerState: StateFlow<FileManagerState> = _fileManagerState.asStateFlow()

    private val _mediaGalleryItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val mediaGalleryItems: StateFlow<List<VaultItem>> = _mediaGalleryItems.asStateFlow()

    private val _activeDocItem = MutableStateFlow<VaultItem?>(null)
    val activeDocItem: StateFlow<VaultItem?> = _activeDocItem.asStateFlow()

    private val _docLoadState = MutableStateFlow<DocLoadState>(DocLoadState.Idle)
    val docLoadState: StateFlow<DocLoadState> = _docLoadState.asStateFlow()

    private val _activeImageItem = MutableStateFlow<VaultItem?>(null)
    val activeImageItem: StateFlow<VaultItem?> = _activeImageItem.asStateFlow()

    private val _activeMediaItem = MutableStateFlow<VaultItem?>(null)
    val activeMediaItem: StateFlow<VaultItem?> = _activeMediaItem.asStateFlow()

    private val _activeMediaQueue = MutableStateFlow<List<VaultItem>>(emptyList())
    val activeMediaQueue: StateFlow<List<VaultItem>> = _activeMediaQueue.asStateFlow()

    private val _converterItem = MutableStateFlow<VaultItem?>(null)
    val converterItem: StateFlow<VaultItem?> = _converterItem.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _lastConvertedItem = MutableStateFlow<VaultItem?>(null)
    val lastConvertedItem: StateFlow<VaultItem?> = _lastConvertedItem.asStateFlow()

    private val _audioBarState = MutableStateFlow(AudioPlaybackBarState())
    val audioBarState: StateFlow<AudioPlaybackBarState> = _audioBarState.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages = _toastMessages.asSharedFlow()

    // Shared lazily initialized Media3 ExoPlayer for ultra-low memory & instant mini-player
    private var _exoPlayer: ExoPlayer? = null

    fun getOrCreatePlayer(): ExoPlayer {
        val existing = _exoPlayer
        if (existing != null) return existing
        val player = ExoPlayer.Builder(getApplication()).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _audioBarState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _audioBarState.update {
                        it.copy(durationMs = duration.coerceAtLeast(0L))
                    }
                }
            })
        }
        _exoPlayer = player
        return player
    }

    init {
        viewModelScope.launch {
            repository.ensureSeededWorkspace()
            refreshFiles()
            refreshMediaGallery()
        }
    }

    fun emitToast(message: String) {
        _toastMessages.tryEmit(message)
    }

    fun refreshFiles() {
        viewModelScope.launch {
            val state = _fileManagerState.value
            _fileManagerState.update { it.copy(isLoading = true) }
            try {
                val items = if (state.isBrowsingSaf && state.mountedLocation != null) {
                    val docId = state.mountedLocation.breadcrumbIds.lastOrNull()?.first
                    repository.listSafTreeChildren(
                        treeUri = state.mountedLocation.treeUri,
                        parentDocumentId = docId,
                        categoryFilter = state.categoryFilter,
                        sortOption = state.sortOption,
                        query = state.searchQuery
                    )
                } else {
                    val currentDir = state.vaultBreadcrumbs.lastOrNull() ?: repository.vaultRoot
                    repository.listVaultDirectory(
                        directory = currentDir,
                        categoryFilter = state.categoryFilter,
                        sortOption = state.sortOption,
                        query = state.searchQuery
                    )
                }
                val usage = repository.computeVaultUsageBytes()
                _fileManagerState.update {
                    it.copy(items = items, isLoading = false, vaultUsageBytes = usage)
                }
            } catch (e: Exception) {
                _fileManagerState.update { it.copy(isLoading = false) }
                emitToast("Failed to list folder: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun refreshMediaGallery() {
        viewModelScope.launch {
            val vaultMedia = repository.listAllVaultMedia()
            val recentMedia = recentFiles.value.filter {
                it.category == FileCategory.IMAGE ||
                    it.category == FileCategory.AUDIO ||
                    it.category == FileCategory.VIDEO
            }
            val merged = (vaultMedia + recentMedia).distinctBy { it.id }
            _mediaGalleryItems.value = merged
        }
    }

    fun setSearchQuery(query: String) {
        _fileManagerState.update { it.copy(searchQuery = query) }
        refreshFiles()
    }

    fun setCategoryFilter(category: FileCategory) {
        _fileManagerState.update { it.copy(categoryFilter = category) }
        refreshFiles()
    }

    fun setSortOption(option: SortOption) {
        _fileManagerState.update { it.copy(sortOption = option) }
        viewModelScope.launch { settingsStore.setSortOption(option) }
        refreshFiles()
    }

    fun openVaultRoot() {
        _fileManagerState.update {
            it.copy(
                isBrowsingSaf = false,
                vaultBreadcrumbs = listOf(repository.vaultRoot),
                mountedLocation = null
            )
        }
        refreshFiles()
    }

    fun openVaultSubfolder(folder: File) {
        _fileManagerState.update { state ->
            val idx = state.vaultBreadcrumbs.indexOfFirst { it.absolutePath == folder.absolutePath }
            val nextCrumbs = if (idx >= 0) {
                state.vaultBreadcrumbs.take(idx + 1)
            } else {
                state.vaultBreadcrumbs + folder
            }
            state.copy(isBrowsingSaf = false, vaultBreadcrumbs = nextCrumbs)
        }
        refreshFiles()
    }

    fun openMountedFolder(entity: MountedFolderEntity) {
        val treeUri = Uri.parse(entity.treeUri)
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        _fileManagerState.update {
            it.copy(
                isBrowsingSaf = true,
                mountedLocation = MountedLocation(
                    treeUri = treeUri,
                    displayName = entity.displayName,
                    breadcrumbIds = listOf(rootDocId to entity.displayName)
                )
            )
        }
        refreshFiles()
    }

    fun enterDirectory(item: VaultItem) {
        if (!item.isDirectory) return
        val state = _fileManagerState.value
        if (state.isBrowsingSaf && state.mountedLocation != null && item.contentUri != null) {
            val docId = DocumentsContract.getDocumentId(item.contentUri)
            _fileManagerState.update {
                val loc = it.mountedLocation!!
                it.copy(
                    mountedLocation = loc.copy(
                        breadcrumbIds = loc.breadcrumbIds + (docId to item.name)
                    )
                )
            }
            refreshFiles()
        } else if (item.filePath != null) {
            openVaultSubfolder(File(item.filePath))
        }
    }

    fun navigateUpDirectory(): Boolean {
        val state = _fileManagerState.value
        if (state.isBrowsingSaf && state.mountedLocation != null) {
            val crumbs = state.mountedLocation.breadcrumbIds
            if (crumbs.size > 1) {
                _fileManagerState.update {
                    it.copy(mountedLocation = state.mountedLocation.copy(breadcrumbIds = crumbs.dropLast(1)))
                }
                refreshFiles()
                return true
            }
            openVaultRoot()
            return true
        } else if (state.vaultBreadcrumbs.size > 1) {
            _fileManagerState.update {
                it.copy(vaultBreadcrumbs = state.vaultBreadcrumbs.dropLast(1))
            }
            refreshFiles()
            return true
        }
        return false
    }

    fun mountSafFolder(uri: Uri) {
        viewModelScope.launch {
            repository.mountFolder(uri)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val display = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Device Folder" }
            openMountedFolder(MountedFolderEntity(uri.toString(), display))
            emitToast("Mounted device folder: $display")
        }
    }

    fun unmountSafFolder(treeUri: String) {
        viewModelScope.launch {
            repository.unmountFolder(treeUri)
            if (_fileManagerState.value.mountedLocation?.treeUri?.toString() == treeUri) {
                openVaultRoot()
            }
            emitToast("Folder unmounted")
        }
    }

    fun importExternalUris(uris: List<Uri>, copyToVault: Boolean = true, openFirst: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var firstItem: VaultItem? = null
            for (uri in uris) {
                try {
                    val item = repository.resolveUriAsVaultItem(uri, importIntoVault = copyToVault)
                    repository.recordOpened(item)
                    if (firstItem == null) firstItem = item
                } catch (e: Exception) {
                    emitToast("Import failed: ${e.message}")
                }
            }
            refreshFiles()
            refreshMediaGallery()
            if (openFirst && firstItem != null) {
                openItem(firstItem)
            } else if (uris.isNotEmpty()) {
                emitToast("Imported ${uris.size} file(s) offline")
            }
        }
    }

    fun createNewFolder(name: String) {
        viewModelScope.launch {
            val parent = _fileManagerState.value.vaultBreadcrumbs.lastOrNull() ?: repository.vaultRoot
            repository.createFolder(parent, name)
            refreshFiles()
            emitToast("Folder created")
        }
    }

    fun createNewDocument(baseName: String, format: DocFormat, onOpened: (VaultItem) -> Unit) {
        viewModelScope.launch {
            val parent = _fileManagerState.value.vaultBreadcrumbs.lastOrNull() ?: repository.vaultRoot
            val item = repository.createBlankDocument(parent, baseName, format)
            repository.recordOpened(item)
            refreshFiles()
            openDocument(item)
            onOpened(item)
        }
    }

    fun renameItem(item: VaultItem, newName: String) {
        viewModelScope.launch {
            val ok = repository.renameVaultItem(item, newName)
            if (ok) {
                refreshFiles()
                refreshMediaGallery()
                emitToast("Renamed to $newName")
            } else {
                emitToast("Rename failed (name conflict or read-only storage)")
            }
        }
    }

    fun duplicateItem(item: VaultItem) {
        viewModelScope.launch {
            val dup = repository.duplicateVaultItem(item)
            if (dup != null) {
                refreshFiles()
                refreshMediaGallery()
                emitToast("Duplicated as ${dup.name}")
            } else {
                emitToast("Duplicate is available for files inside Offline Vault")
            }
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            val ok = repository.deleteVaultItem(item)
            if (ok) {
                refreshFiles()
                refreshMediaGallery()
                emitToast("Deleted ${item.name}")
            } else {
                emitToast("Unable to delete item")
            }
        }
    }

    fun togglePin(item: VaultItem) {
        viewModelScope.launch {
            repository.togglePin(item)
        }
    }

    suspend fun getSha256(item: VaultItem): String {
        return runCatching { repository.computeSha256(item) }.getOrElse { "Unavailable" }
    }

    fun buildShareIntent(item: VaultItem): Intent? {
        return try {
            val uri = when {
                item.filePath != null -> FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    File(item.filePath)
                )
                item.contentUri != null -> item.contentUri
                else -> return null
            }
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---- Routing / Opening ----
    var onNavigateToDocStudio: (() -> Unit)? = null
    var onNavigateToImageStudio: (() -> Unit)? = null
    var onNavigateToMediaPlayer: (() -> Unit)? = null
    var onNavigateToConverter: (() -> Unit)? = null

    fun openItem(item: VaultItem) {
        if (item.isDirectory) {
            enterDirectory(item)
            return
        }
        viewModelScope.launch { repository.recordOpened(item) }
        when (item.category) {
            FileCategory.IMAGE -> {
                _activeImageItem.value = item
                onNavigateToImageStudio?.invoke()
            }
            FileCategory.AUDIO, FileCategory.VIDEO -> {
                openMediaPlayback(item)
                onNavigateToMediaPlayer?.invoke()
            }
            else -> {
                openDocument(item)
                onNavigateToDocStudio?.invoke()
            }
        }
    }

    fun openInConverter(item: VaultItem) {
        _converterItem.value = item
        _lastConvertedItem.value = null
        onNavigateToConverter?.invoke()
    }

    fun setConverterItem(item: VaultItem) {
        _converterItem.value = item
        _lastConvertedItem.value = null
    }

    fun selectConverterUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val item = repository.resolveUriAsVaultItem(uri, importIntoVault = false)
                repository.recordOpened(item)
                _converterItem.value = item
                _lastConvertedItem.value = null
            } catch (e: Exception) {
                emitToast("Unable to open file for conversion: ${e.message}")
            }
        }
    }

    // ---- Document Reader & Editor ----

    fun openDocument(item: VaultItem) {
        _activeDocItem.value = item
        _docLoadState.value = DocLoadState.Loading
        viewModelScope.launch {
            try {
                val doc = DocumentEngine.loadDocument(getApplication(), item)
                _docLoadState.value = DocLoadState.Success(doc, isModified = false)
            } catch (e: Exception) {
                _docLoadState.value = DocLoadState.Error(e.message ?: "Unable to parse document")
            }
        }
    }

    fun updateActiveDocument(updated: UnifiedDocument) {
        _docLoadState.value = DocLoadState.Success(updated, isModified = true)
    }

    fun saveActiveDocument() {
        val item = _activeDocItem.value ?: return
        val state = _docLoadState.value as? DocLoadState.Success ?: return
        viewModelScope.launch {
            try {
                val format = item.docFormat ?: DocFormat.TXT
                if (!format.canWrite) {
                    emitToast("${format.extension.uppercase()} is read-only. Use Convert/Export to save as ${format.extension.uppercase()}X or PDF.")
                    return@launch
                }
                DocumentEngine.saveDocumentToItem(
                    context = getApplication(),
                    item = item,
                    doc = state.document,
                    targetFormat = format,
                    paperSize = settings.value.pdfPaperSize
                )
                _docLoadState.value = state.copy(isModified = false)
                refreshFiles()
                emitToast("Saved ${item.name}")
            } catch (e: Exception) {
                emitToast("Save failed: ${e.message}")
            }
        }
    }

    // ---- Format Conversion Studio ----

    fun convertDocumentToVault(sourceItem: VaultItem, targetFormat: DocFormat) {
        viewModelScope.launch {
            _isConverting.value = true
            try {
                val doc = DocumentEngine.loadDocument(getApplication(), sourceItem)
                val convertedDir = File(repository.vaultRoot, "Converted").apply { mkdirs() }
                val base = SecurityUtils.sanitizeFileName(sourceItem.name.substringBeforeLast('.'))
                val outFile = repository.uniqueFile(convertedDir, "$base.${targetFormat.extension}")
                withContext(Dispatchers.IO) {
                    FileOutputStream(outFile).use { out ->
                        DocumentEngine.writeDocument(doc, targetFormat, out, settings.value.pdfPaperSize)
                    }
                }
                val newItem = VaultItem(
                    id = outFile.absolutePath,
                    name = outFile.name,
                    isDirectory = false,
                    sizeBytes = outFile.length(),
                    lastModified = outFile.lastModified(),
                    extension = targetFormat.extension,
                    mimeType = targetFormat.mimeType,
                    category = targetFormat.category,
                    origin = StorageOrigin.VAULT,
                    filePath = outFile.absolutePath
                )
                repository.recordOpened(newItem)
                _lastConvertedItem.value = newItem
                refreshFiles()
                emitToast("Converted to ${outFile.name}")
            } catch (e: Exception) {
                emitToast("Conversion failed: ${e.message}")
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun convertDocumentToExternalUri(sourceItem: VaultItem, targetFormat: DocFormat, destUri: Uri) {
        viewModelScope.launch {
            _isConverting.value = true
            try {
                val doc = DocumentEngine.loadDocument(getApplication(), sourceItem)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        DocumentEngine.writeDocument(doc, targetFormat, out, settings.value.pdfPaperSize)
                    } ?: throw IllegalStateException("Cannot write to selected destination")
                }
                emitToast("Exported ${targetFormat.extension.uppercase()} to device storage")
            } catch (e: Exception) {
                emitToast("Export failed: ${e.message}")
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun convertImageToVault(
        sourceItem: VaultItem,
        targetFormat: MediaFormat?,
        exportAsPdf: Boolean,
        quality: Int = 90,
        rotationDegrees: Int = 0,
        flipHorizontal: Boolean = false
    ) {
        viewModelScope.launch {
            _isConverting.value = true
            try {
                val decoded = MediaEngine.decodeSubsampledBitmap(getApplication(), sourceItem, 2560)
                    ?: throw IllegalStateException("Could not decode image")
                val transformed = MediaEngine.transformBitmap(decoded, rotationDegrees, flipHorizontal)
                val convertedDir = File(repository.vaultRoot, "Converted").apply { mkdirs() }
                val base = SecurityUtils.sanitizeFileName(sourceItem.name.substringBeforeLast('.'))

                val newItem: VaultItem
                if (exportAsPdf) {
                    val outFile = repository.uniqueFile(convertedDir, "$base.pdf")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(outFile).use { PdfCodec.writeImageBitmapToPdf(transformed, base, it) }
                    }
                    newItem = VaultItem(
                        id = outFile.absolutePath,
                        name = outFile.name,
                        isDirectory = false,
                        sizeBytes = outFile.length(),
                        lastModified = outFile.lastModified(),
                        extension = "pdf",
                        mimeType = "application/pdf",
                        category = FileCategory.DOCUMENT,
                        origin = StorageOrigin.VAULT,
                        filePath = outFile.absolutePath
                    )
                } else {
                    val fmt = targetFormat ?: MediaFormat.PNG
                    val outFile = repository.uniqueFile(convertedDir, "$base.${fmt.extension}")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(outFile).use { MediaEngine.encodeImage(transformed, fmt, quality, it) }
                    }
                    newItem = VaultItem(
                        id = outFile.absolutePath,
                        name = outFile.name,
                        isDirectory = false,
                        sizeBytes = outFile.length(),
                        lastModified = outFile.lastModified(),
                        extension = fmt.extension,
                        mimeType = fmt.mimeType,
                        category = FileCategory.IMAGE,
                        origin = StorageOrigin.VAULT,
                        filePath = outFile.absolutePath
                    )
                }
                if (transformed !== decoded) transformed.recycle()
                decoded.recycle()

                repository.recordOpened(newItem)
                _lastConvertedItem.value = newItem
                refreshFiles()
                refreshMediaGallery()
                emitToast("Saved ${newItem.name}")
            } catch (e: Exception) {
                emitToast("Image conversion failed: ${e.message}")
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun stripImageExif(item: VaultItem, onDone: () -> Unit) {
        viewModelScope.launch {
            val file = item.filePath?.let { File(it) }
            if (file == null) {
                emitToast("Convert/Save to Vault first to strip EXIF metadata")
                return@launch
            }
            try {
                MediaEngine.stripExifFromJpegFile(file)
                emitToast("EXIF & GPS metadata stripped cleanly")
                refreshFiles()
                onDone()
            } catch (e: Exception) {
                emitToast("EXIF strip error: ${e.message}")
            }
        }
    }

    // ---- Media3 Playback ----

    fun openMediaPlayback(item: VaultItem) {
        _activeMediaItem.value = item
        val sameCategoryQueue = _mediaGalleryItems.value
            .filter { it.category == item.category }
            .ifEmpty { listOf(item) }
        _activeMediaQueue.value = sameCategoryQueue

        val player = getOrCreatePlayer()
        val uri = item.contentUri ?: item.filePath?.let { Uri.fromFile(File(it)) } ?: return
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        _audioBarState.update {
            it.copy(
                currentItem = if (item.category == FileCategory.AUDIO) item else null,
                isPlaying = true
            )
        }
    }

    fun toggleMiniPlayerPlayPause() {
        val player = _exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stopMiniPlayer() {
        _exoPlayer?.stop()
        _audioBarState.value = AudioPlaybackBarState()
    }

    fun refreshAudioProgress() {
        val player = _exoPlayer ?: return
        _audioBarState.update {
            it.copy(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L)
            )
        }
    }

    // ---- Settings ----
    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setPdfPaperSize(size: PdfPaperSize) = viewModelScope.launch { settingsStore.setPdfPaperSize(size) }
    fun setAutoStripExif(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoStripExif(enabled) }
    fun setEditorFontSize(sp: Int) = viewModelScope.launch { settingsStore.setEditorFontSize(sp) }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
