package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.MountedFolderEntity
import com.example.model.FileCategory
import com.example.model.SortOption
import com.example.model.VaultItem
import com.example.ui.components.VaultFileRow
import com.example.viewmodel.FileManagerState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    state: FileManagerState,
    mountedFolders: List<MountedFolderEntity>,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (FileCategory) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onSelectVaultRoot: () -> Unit,
    onSelectVaultCrumb: (File) -> Unit,
    onSelectMountedFolder: (MountedFolderEntity) -> Unit,
    onUnmountFolder: (String) -> Unit,
    onMountDeviceFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateDocument: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onItemMore: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("files_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create or Import")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Document / Sheet / Deck") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = {
                            showFabMenu = false
                            onCreateDocument()
                        }
                    )
                    if (!state.isBrowsingSaf) {
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                            onClick = {
                                showFabMenu = false
                                onCreateFolder()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import Files to Vault") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                        onClick = {
                            showFabMenu = false
                            onImportFiles()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mount Device Folder (SAF)") },
                        leadingIcon = { Icon(Icons.Filled.FolderSpecial, null) },
                        onClick = {
                            showFabMenu = false
                            onMountDeviceFolder()
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top bar with storage roots + sort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if ((state.isBrowsingSaf && (state.mountedLocation?.breadcrumbIds?.size ?: 0) > 1) ||
                        (!state.isBrowsingSaf && state.vaultBreadcrumbs.size > 1)
                    ) {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("files_back_up")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one folder")
                        }
                    }
                    Text(
                        text = if (state.isBrowsingSaf) {
                            state.mountedLocation?.breadcrumbIds?.lastOrNull()?.second ?: "Device Folder"
                        } else {
                            if (state.vaultBreadcrumbs.size <= 1) "Offline Vault"
                            else state.vaultBreadcrumbs.last().name
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("files_sort_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort files")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (opt == state.sortOption) "✓ ${opt.label}" else opt.label
                                    )
                                },
                                onClick = {
                                    showSortMenu = false
                                    onSortChange(opt)
                                }
                            )
                        }
                    }
                }
            }

            // Storage Sources Switcher (Vault + Mounted Device Folders)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !state.isBrowsingSaf,
                    onClick = onSelectVaultRoot,
                    label = { Text("Private Vault") },
                    leadingIcon = { Icon(Icons.Filled.Storage, null, modifier = Modifier.size(16.dp)) }
                )
                mountedFolders.forEach { mounted ->
                    val selected = state.isBrowsingSaf &&
                        state.mountedLocation?.treeUri?.toString() == mounted.treeUri
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMountedFolder(mounted) },
                        label = { Text(mounted.displayName) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Unmount ${mounted.displayName}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onUnmountFolder(mounted.treeUri) }
                            )
                        }
                    )
                }
                AssistChip(
                    onClick = onMountDeviceFolder,
                    label = { Text("+ Mount Folder") }
                )
            }

            // Breadcrumbs when inside Vault subfolders
            if (!state.isBrowsingSaf && state.vaultBreadcrumbs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.vaultBreadcrumbs.forEachIndexed { idx, dir ->
                        val label = if (idx == 0) "Vault" else dir.name
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (idx == state.vaultBreadcrumbs.lastIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectVaultCrumb(dir) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        if (idx < state.vaultBreadcrumbs.lastIndex) {
                            Text(" / ", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter files by name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("files_search_input")
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = state.categoryFilter == cat,
                        onClick = { onCategoryChange(cat) },
                        label = { Text(cat.label) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching files here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap + to create a document, import files, or mount a device folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        VaultFileRow(
                            item = item,
                            onClick = { onOpenItem(item) },
                            onMoreClick = { onItemMore(item) }
                        )
                    }
                }
            }
        }
    }
}
