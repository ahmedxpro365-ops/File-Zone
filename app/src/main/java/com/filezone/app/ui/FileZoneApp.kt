@file:Suppress("DEPRECATION")
package com.filezone.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.filezone.app.R
import com.filezone.app.data.BookmarkEntity
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import com.filezone.app.data.FavoriteEntity
import com.filezone.app.data.RecentEntity
import com.filezone.app.ui.theme.*
import com.filezone.app.util.FileOperations
import com.filezone.app.viewmodel.FileItem
import com.filezone.app.viewmodel.FileZoneViewModel
import com.filezone.app.viewmodel.SearchCriteria
import com.filezone.app.viewmodel.UILanguage
import com.filezone.app.viewmodel.UIThemeMode
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileZoneApp(
    viewModel: FileZoneViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    val layoutDirection = if (language == UILanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        val configuration = LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600

        var activeProScreen by remember { mutableStateOf<String?>(null) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val currentDir by viewModel.currentDirectory.collectAsStateWithLifecycle()
        val activePkgPreview by viewModel.activePackagePreview.collectAsStateWithLifecycle()
        val files by viewModel.directoryFiles.collectAsStateWithLifecycle()
        val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
        val clipboardPaths by viewModel.clipboardPaths.collectAsStateWithLifecycle()
        val isMove by viewModel.isMoveOperation.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

        val showCreateOptions by viewModel.showCreateOptionsDialog.collectAsStateWithLifecycle()
        val showCreateFile by viewModel.showCreateFileDialog.collectAsStateWithLifecycle()
        val showCreateFolder by viewModel.showCreateFolderDialog.collectAsStateWithLifecycle()
        val renameTarget by viewModel.showRenameDialog.collectAsStateWithLifecycle()
        val showDeleteConfirm by viewModel.showDeleteConfirmDialog.collectAsStateWithLifecycle()
        val showZipConfirm by viewModel.showZipConfirmDialog.collectAsStateWithLifecycle()

        val viewTextContent by viewModel.activeTextFileContent.collectAsStateWithLifecycle()
        val viewTextName by viewModel.activeTextFileName.collectAsStateWithLifecycle()
        val viewImageUrl by viewModel.activeImageFile.collectAsStateWithLifecycle()
        val viewVideoUrl by viewModel.activeVideoFile.collectAsStateWithLifecycle()
        val viewAudioUrl by viewModel.activeAudioFile.collectAsStateWithLifecycle()
        val viewPdfUrl by viewModel.activePdfFile.collectAsStateWithLifecycle()
        val viewZipEntries by viewModel.activeZipEntries.collectAsStateWithLifecycle()
        val viewZipFile by viewModel.activeZipFile.collectAsStateWithLifecycle()

        val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()

        if (!hasStoragePermission) {
            PermissionOnboardingScreen(
                viewModel = viewModel,
                themeMode = themeMode,
                language = language
            )
        } else {
            ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "File Zone",
                        modifier = Modifier.padding(horizontal = 28.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    NavigationDrawerItem(
                        label = { Text("Internal Storage") },
                        selected = false,
                        onClick = {
                            viewModel.navigateTo(viewModel.sandboxRoot)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Downloads") },
                        selected = false,
                        onClick = {
                            viewModel.navigateTo(viewModel.downloadsRoot)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Images") },
                        selected = false,
                        onClick = {
                            viewModel.setCategoryView("Image", true)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Image, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Videos") },
                        selected = false,
                        onClick = {
                            viewModel.setCategoryView("Video", true)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Documents") },
                        selected = false,
                        onClick = {
                            viewModel.setCategoryView("Document", true)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Description, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Archives") },
                        selected = false,
                        onClick = {
                            viewModel.setCategoryView("Zip", true)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                    NavigationDrawerItem(
                        label = { Text("Tools") },
                        selected = false,
                        onClick = {
                            activeProScreen = "tools"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Recycle Bin") },
                        selected = false,
                        onClick = {
                            activeProScreen = "recycle"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        onClick = {
                            activeProScreen = "settings"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "File Zone",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                    DropdownMenuItem(
                                        text = { Text("Create") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.showCreateOptionsDialog.value = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                                    )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showMenu = false
                                        activeProScreen = "settings"
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                floatingActionButton = {
                    // Create moved to 3-dots menu
                }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        Brush.linearGradient(
                            colors = if (themeMode == UIThemeMode.Dark) {
                                listOf(CharcoalDark, PureMidnight)
                            } else {
                                listOf(WarmWhite, Color(0xFFE2E8F0))
                            }
                        )
                    )
            ) {

                Box(modifier = Modifier.fillMaxSize()) {
                    ExplorerTabScreen(
                        viewModel = viewModel,
                        currentDir = currentDir,
                        files = files,
                        selectedPaths = selectedPaths,
                        clipboardPaths = clipboardPaths,
                        isMove = isMove,
                        searchQuery = searchQuery,
                        isTablet = isTablet,
                        onProScreenSelected = { activeProScreen = it }
                    )

                    // Immersive Pro Screen Overlays
                    androidx.compose.animation.AnimatedVisibility(
                        visible = activeProScreen != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (activeProScreen) {
                            "recycle" -> RecycleBinProScreen(viewModel = viewModel, onClose = { activeProScreen = null })
                            "analyzer" -> StorageAnalyzerProScreen(viewModel = viewModel, onClose = { activeProScreen = null })
                            "apps" -> ApkManagerProScreen(viewModel = viewModel, onClose = { activeProScreen = null })
                            "settings" -> SettingsTabScreen(viewModel = viewModel, onClose = { activeProScreen = null })
                        }
                    }

                    // Native Split Package Preview Panel
                    activePkgPreview?.let { pkg ->
                        PackagePreviewDialog(
                            preview = pkg,
                            onInstall = {
                                viewModel.installActivePackage()
                                viewModel.activePackagePreview.value = null
                            },
                            onClose = {
                                viewModel.activePackagePreview.value = null
                                viewModel.activePackageFile.value = null
                            }
                        )
                    }

                    // Immersive Create Options
                    viewTextContent?.let { content ->
                        TextViewerDialog(
                            title = viewTextName ?: "Document.txt",
                            initialContent = content,
                            onSave = { updatedText ->
                                viewModel.saveTextFile(viewTextName ?: "Document.txt", updatedText)
                            },
                            onClose = {
                                viewModel.activeTextFileContent.value = null
                                viewModel.activeTextFileName.value = null
                            }
                        )
                    }

                    // 2. Image Viewing Dialog
                    viewImageUrl?.let { file ->
                        ImageViewerDialog(
                            file = file,
                            onClose = { viewModel.activeImageFile.value = null }
                        )
                    }

                    // Video Player Overlay
                    viewVideoUrl?.let { file ->
                        VideoPlayerDialog(
                            file = file,
                            onClose = { viewModel.activeVideoFile.value = null }
                        )
                    }

                    // Audio Player Overlay
                    viewAudioUrl?.let { file ->
                        AudioPlayerDialog(
                            file = file,
                            onClose = { viewModel.activeAudioFile.value = null }
                        )
                    }

                    // PDF Viewer Overlay
                    viewPdfUrl?.let { file ->
                        PdfViewerDialog(
                            file = file,
                            onClose = { viewModel.activePdfFile.value = null }
                        )
                    }

                    // 3. ZIP File Entry Listing Dialog
                    viewZipFile?.let { zipFile ->
                        ZipViewerDialog(
                            zipFile = zipFile,
                            entries = viewZipEntries,
                            onExtract = {
                                viewModel.activeZipFile.value = null
                                viewModel.decompressZip(
                                    FileItem(
                                        name = zipFile.name,
                                        path = zipFile.absolutePath,
                                        isFolder = false,
                                        size = zipFile.length(),
                                        lastModified = zipFile.lastModified(),
                                        extension = "zip",
                                        mimeType = "application/zip"
                                    )
                                )
                            },
                            onClose = { viewModel.activeZipFile.value = null }
                        )
                    }

                    if (showCreateOptions) {
                        AlertDialog(
                            onDismissRequest = { viewModel.showCreateOptionsDialog.value = false },
                            title = { Text(stringResource(R.string.dialog_create_options)) },
                            text = {
                                Column {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.dialog_create_folder)) },
                                        leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                        modifier = Modifier.clickable {
                                            viewModel.showCreateOptionsDialog.value = false
                                            viewModel.showCreateFolderDialog.value = true
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.dialog_create_file)) },
                                        leadingContent = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                                        modifier = Modifier.clickable {
                                            viewModel.showCreateOptionsDialog.value = false
                                            viewModel.showCreateFileDialog.value = true
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.showCreateOptionsDialog.value = false }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }

                    if (showCreateFile) {
                        var fileName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { viewModel.showCreateFileDialog.value = false },
                            title = { Text(stringResource(R.string.dialog_create_file)) },
                            text = {
                                OutlinedTextField(
                                    value = fileName,
                                    onValueChange = { fileName = it },
                                    label = { Text(stringResource(R.string.placeholder_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.createFile(fileName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.button_create))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showCreateFileDialog.value = false }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }

                    if (showCreateFolder) {
                        var folderName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { viewModel.showCreateFolderDialog.value = false },
                            title = { Text(stringResource(R.string.dialog_create_folder)) },
                            text = {
                                OutlinedTextField(
                                    value = folderName,
                                    onValueChange = { folderName = it },
                                    label = { Text(stringResource(R.string.placeholder_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.createFolder(folderName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.button_create))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showCreateFolderDialog.value = false }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }

                    renameTarget?.let { item ->
                        var renameName by remember { mutableStateOf(item.name) }
                        AlertDialog(
                            onDismissRequest = { viewModel.showRenameDialog.value = null },
                            title = { Text(stringResource(R.string.dialog_rename)) },
                            text = {
                                OutlinedTextField(
                                    value = renameName,
                                    onValueChange = { renameName = it },
                                    label = { Text(stringResource(R.string.placeholder_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.renameItem(item, renameName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.action_rename))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showRenameDialog.value = null }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { viewModel.showDeleteConfirmDialog.value = false },
                            title = { Text(stringResource(R.string.dialog_delete_confirm)) },
                            text = { Text(stringResource(if (selectedPaths.size > 1) R.string.delete_confirm_multi else R.string.delete_confirm)) },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.deleteSelectedItems() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.action_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showDeleteConfirmDialog.value = false }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }

                    if (showZipConfirm) {
                        var zipArchiveName by remember { mutableStateOf("Archive.zip") }
                        AlertDialog(
                            onDismissRequest = { viewModel.showZipConfirmDialog.value = false },
                            title = { Text(stringResource(R.string.dialog_compress_zip)) },
                            text = {
                                OutlinedTextField(
                                    value = zipArchiveName,
                                    onValueChange = { zipArchiveName = it },
                                    label = { Text(stringResource(R.string.placeholder_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.compressSelectedToZip(zipArchiveName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.button_ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showZipConfirmDialog.value = false }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}
}

// ======================== TAB SCREENS ==============================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolsTabScreen(
    viewModel: FileZoneViewModel,
    onProScreenSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Advanced Tools",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        CategoryBentoCard(
            title = "Storage Analyzer",
            subtitle = "Largest & duplicates",
            emoji = "📊",
            containerColor = Color(0xFF312E81),
            textColor = Color(0xFFE0E7FF),
            onClick = {
                viewModel.runAdvancedStorageAnalysis()
                onProScreenSelected("analyzer")
            }
        )
        
        CategoryBentoCard(
            title = "APK Manager",
            subtitle = "Extract & backup",
            emoji = "📱",
            containerColor = Color(0xFF064E3B),
            textColor = Color(0xFFD1FAE5),
            onClick = {
                viewModel.loadInstalledApps()
                onProScreenSelected("apps")
            }
        )
    }
}

@Composable
fun ExplorerTabScreen(
    viewModel: FileZoneViewModel,
    currentDir: File,
    files: List<FileItem>,
    selectedPaths: Set<String>,
    clipboardPaths: Set<String>,
    isMove: Boolean,
    searchQuery: String,
    isTablet: Boolean,
    onProScreenSelected: (String) -> Unit
) {
    val searchCriteria by viewModel.searchCriteria.collectAsStateWithLifecycle()
    val isAtRoot = currentDir.absolutePath == viewModel.sandboxRoot.absolutePath && searchQuery.isEmpty() && searchCriteria.type == "All"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Bar (Always visible at top)
        FileZoneSearchBar(searchQuery = searchQuery, viewModel = viewModel)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isAtRoot) {
            HomeLayout(viewModel = viewModel, onProScreenSelected = onProScreenSelected)
        } else {
            // Standard File Browser Layout
            FileBrowserLayout(
                viewModel = viewModel,
                currentDir = currentDir,
                files = files,
                selectedPaths = selectedPaths,
                clipboardPaths = clipboardPaths,
                isMove = isMove,
                isTablet = isTablet
            )
        }
    }
}

@Composable
fun FileZoneSearchBar(searchQuery: String, viewModel: FileZoneViewModel) {
    var showFilters by remember { mutableStateOf(false) }
    val searchCriteria by viewModel.searchCriteria.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(stringResource(R.string.search_hint), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("search_input")
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Surface(
            onClick = { showFilters = !showFilters },
            shape = CircleShape,
            color = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = if (showFilters) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    AnimatedVisibility(visible = showFilters) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Advanced Filters", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = searchCriteria.type == "All",
                    onClick = { viewModel.updateSearchCriteria(searchCriteria.copy(type = "All")) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = searchCriteria.type == "Image",
                    onClick = { viewModel.updateSearchCriteria(searchCriteria.copy(type = "Image")) },
                    label = { Text("Images") }
                )
                FilterChip(
                    selected = searchCriteria.type == "Video",
                    onClick = { viewModel.updateSearchCriteria(searchCriteria.copy(type = "Video")) },
                    label = { Text("Videos") }
                )
                FilterChip(
                    selected = searchCriteria.type == "Document",
                    onClick = { viewModel.updateSearchCriteria(searchCriteria.copy(type = "Document")) },
                    label = { Text("Docs") }
                )
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchCriteria.extension,
                    onValueChange = { viewModel.updateSearchCriteria(searchCriteria.copy(extension = it)) },
                    label = { Text("Ext", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = if (searchCriteria.minSize > 0) searchCriteria.minSize.toString() else "",
                    onValueChange = { 
                        val size = it.toLongOrNull() ?: 0L
                        viewModel.updateSearchCriteria(searchCriteria.copy(minSize = size)) 
                    },
                    label = { Text("Min Size (B)", fontSize = 10.sp) },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
fun HomeLayout(viewModel: FileZoneViewModel, onProScreenSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Categories Grid
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryShortCut(
                icon = Icons.Default.Image,
                label = "Images",
                color = Color(0xFF3B82F6),
                onClick = { viewModel.setCategoryView("Image", true) },
                modifier = Modifier.weight(1f)
            )
            CategoryShortCut(
                icon = Icons.Default.VideoLibrary,
                label = "Videos",
                color = Color(0xFFEF4444),
                onClick = { viewModel.setCategoryView("Video", true) },
                modifier = Modifier.weight(1f)
            )
            CategoryShortCut(
                icon = Icons.Default.MusicNote,
                label = "Music",
                color = Color(0xFFA855F7),
                onClick = { viewModel.setCategoryView("Audio", true) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryShortCut(
                icon = Icons.Default.Download,
                label = "Downloads",
                color = Color(0xFFF59E0B),
                onClick = {
                    viewModel.navigateTo(viewModel.downloadsRoot)
                    viewModel.updateSearchCriteria(SearchCriteria(type = "All", isRecursive = false))
                },
                modifier = Modifier.weight(1f)
            )
            CategoryShortCut(
                icon = Icons.Default.Description,
                label = "Docs",
                color = Color(0xFF10B981),
                onClick = { viewModel.setCategoryView("Document", true) },
                modifier = Modifier.weight(1f)
            )
            CategoryShortCut(
                icon = Icons.Default.Android,
                label = "APK",
                color = Color(0xFF6366F1),
                onClick = { viewModel.setCategoryView("APK", true) },
                modifier = Modifier.weight(1f)
            )
        }

        val (binCount, binSize) = viewModel.recycleBinStats.collectAsStateWithLifecycle().value
        
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "RECYCLE BIN",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        
        Card(
            onClick = { onProScreenSelected("recycle") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Recycle Bin", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("$binCount files • ${if(binSize > 1024*1024) "${binSize/(1024*1024)} MB" else "${binSize/1024} KB"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Internal Storage Shortcut
        Text(
            text = "Storage",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            onClick = { viewModel.navigateTo(viewModel.externalRoot) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Internal Storage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Browse all files and directories", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        
        // Scan Storage Action
        Button(
            onClick = {
                // Perform quick analysis
                val results = viewModel.analyzeStorage()
                // Simple feedback for now
                println("Analysis results: $results")
                onProScreenSelected("analyzer") // Navigate to full analyzer for detailed view
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Memory, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan Storage & Clean")
        }
    }
}

@Composable
fun CategoryShortCut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CategoryBentoCard(
    title: String,
    subtitle: String,
    emoji: String,
    containerColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun FileBrowserLayout(
    viewModel: FileZoneViewModel,
    currentDir: File,
    files: List<FileItem>,
    selectedPaths: Set<String>,
    clipboardPaths: Set<String>,
    isMove: Boolean,
    isTablet: Boolean
) {
    Column {
        val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
        val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
        
        Spacer(modifier = Modifier.height(12.dp))

        if (!hasStoragePermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { viewModel.requestStoragePermission() }) {
                    Text(stringResource(R.string.button_grant_perm))
                }
            }
        } else if (isLoadingFiles) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Path Navigator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val root = if (currentDir.absolutePath.startsWith(viewModel.externalRoot.absolutePath)) viewModel.externalRoot else viewModel.sandboxRoot
                TextButton(onClick = { viewModel.navigateTo(root) }) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                
                val relativePath = currentDir.absolutePath.removePrefix(root.absolutePath)
                val parts = relativePath.split("/").filter { it.isNotEmpty() }
                
                var currentPath = root.absolutePath
                parts.forEach { part ->
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    currentPath += "/$part"
                    val pathSnapshot = currentPath
                    TextButton(onClick = { viewModel.navigateTo(File(pathSnapshot)) }) {
                        Text(part, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.empty_dir_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(files) { item ->
                        ListItemFileRow(
                            item = item,
                            isSelected = selectedPaths.contains(item.path),
                            onSelectToggle = { viewModel.togglePathSelected(item.path) },
                            onItemClick = {
                                if (selectedPaths.isNotEmpty()) {
                                    viewModel.togglePathSelected(item.path)
                                } else {
                                    viewModel.openFileItem(item)
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Selection and Clipboard Overlays
            AnimatedVisibility(
                visible = selectedPaths.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hint_items_selected, selectedPaths.size),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row {
                            IconButton(onClick = { viewModel.copySelectedToClipboard(false) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.copySelectedToClipboard(true) }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.showZipConfirmDialog.value = true }) {
                                Icon(Icons.Default.FolderZip, contentDescription = "ZIP", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.showDeleteConfirmDialog.value = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.clearSelections() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = clipboardPaths.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMove) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${clipboardPaths.size} files in clipboard",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row {
                            Button(
                                onClick = { viewModel.pasteClipboard() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.testTag("paste_clipboard_button")
                            ) {
                                Text("Paste Here")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { viewModel.clearClipboard() }) {
                                Icon(Icons.Default.Cancel, contentDescription = "Clear")
                            }
                        }
                    }
                }
            }
        }
    }
}
        
@Composable
fun StandardDirectoriesShortcuts(viewModel: FileZoneViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShortcutCard(
            title = "Sandbox",
            icon = Icons.Default.Dataset,
            containerColor = Color(0xFF2563EB).copy(alpha = 0.2f),
            iconColor = Color(0xFF3B82F6),
            onClick = { viewModel.navigateTo(viewModel.sandboxRoot) }
        )

        ShortcutCard(
            title = "Downloads",
            icon = Icons.Default.Download,
            containerColor = Color(0xFFD97706).copy(alpha = 0.2f),
            iconColor = Color(0xFFF59E0B),
            onClick = { viewModel.navigateTo(viewModel.downloadsRoot) }
        )

        ShortcutCard(
            title = "Documents",
            icon = Icons.Default.DocumentScanner,
            containerColor = Color(0xFF059669).copy(alpha = 0.2f),
            iconColor = Color(0xFF10B981),
            onClick = { viewModel.navigateTo(viewModel.documentsRoot) }
        )

        ShortcutCard(
            title = "Device Storage",
            icon = Icons.Default.SdCard,
            containerColor = Color(0xFF7C3AED).copy(alpha = 0.2f),
            iconColor = Color(0xFF8B5CF6),
            onClick = { viewModel.navigateTo(viewModel.externalRoot) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.3f)),
        modifier = Modifier.widthIn(min = 120.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListItemFileRow(
    item: FileItem,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onItemClick: () -> Unit,
    viewModel: FileZoneViewModel
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onSelectToggle
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                val iconResources = getFileIconAndColor(item)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconResources.second.copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconResources.first,
                        contentDescription = null,
                        tint = iconResources.second
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isFolder) {
                        "${item.childCount} items"
                    } else {
                        "${FileOperations.formatFileSize(item.size)}  ·  ${formatDate(item.lastModified)}"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleBookmark(item) }) {
                    Icon(
                        imageVector = if (item.isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Bookmark",
                        tint = if (item.isBookmarked) TechPurple else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                }

                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = {
                                expandedMenu = false
                                viewModel.shareFileItem(item)
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            onClick = {
                                expandedMenu = false
                                viewModel.showRenameDialog.value = item
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )

                        if (item.extension == "zip") {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_unzip)) },
                                onClick = {
                                    expandedMenu = false
                                    viewModel.decompressZip(item)
                                },
                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = {
                                expandedMenu = false
                                viewModel.togglePathSelected(item.path)
                                viewModel.showDeleteConfirmDialog.value = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridItemFileRow(
    item: FileItem,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onItemClick: () -> Unit,
    viewModel: FileZoneViewModel
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val iconResources = getFileIconAndColor(item)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onSelectToggle
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                if (isSelected) {
                    Checkbox(checked = true, onCheckedChange = { onSelectToggle() })
                } else {
                    IconButton(
                        onClick = { viewModel.toggleBookmark(item) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (item.isBookmarked) TechPurple else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconResources.second.copy(alpha = 0.12f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconResources.first,
                    contentDescription = null,
                    tint = iconResources.second,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (item.isFolder) "${item.childCount} items" else FileOperations.formatFileSize(item.size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            IconButton(
                onClick = { expandedMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "Menu")
            }

            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    onClick = {
                        expandedMenu = false
                        viewModel.shareFileItem(item)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = {
                        expandedMenu = false
                        viewModel.showRenameDialog.value = item
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expandedMenu = false
                        viewModel.togglePathSelected(item.path)
                        viewModel.showDeleteConfirmDialog.value = true
                    }
                )
            }
        }
    }
}

fun getFileIconAndColor(item: FileItem): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return if (item.isFolder) {
        Pair(Icons.Default.Folder, Color(0xFFF59E0B))
    } else {
        val ext = item.extension.lowercase(java.util.Locale.ROOT)
        when {
            ext in listOf("apk", "xapk", "apks", "apkm") -> Pair(Icons.Default.Android, Color(0xFF3DDC84))
            ext in listOf("zip", "rar", "7z", "tar", "gzip", "gz") -> Pair(Icons.Default.Inventory, Color(0xFFEF4444))
            item.mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic") -> Pair(Icons.Default.Image, Color(0xFF06B6D4))
            item.mimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp") -> Pair(Icons.Default.VideoLibrary, Color(0xFF10B981))
            item.mimeType.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "ogg", "flac", "m4a") -> Pair(Icons.Default.MusicNote, Color(0xFF8B5CF6))
            ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "csv") -> Pair(Icons.Default.Article, Color(0xFF3B82F6))
            ext in listOf("html", "css", "js", "ts", "json", "xml", "java", "kt", "py", "c", "cpp", "php") -> Pair(Icons.Default.Code, Color(0xFFF97316))
            item.mimeType.startsWith("text/") || ext == "txt" -> Pair(Icons.Default.Description, Color(0xFF64748B))
            else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF94A3B8))
        }
    }
}

fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// ======================== RECENTS TAB SCREEN ==============================

@Composable
fun RecentsTabScreen(viewModel: FileZoneViewModel, showTitle: Boolean = true) {
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    Column(
        modifier = if (showTitle) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxWidth()
    ) {
        if (showTitle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_recents),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (recents.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearRecents() }) {
                        Text("Clear All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (recents.isEmpty()) {
            if (showTitle) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.empty_recents_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.empty_recents_desc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text("No recent files", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            if (showTitle) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(recents) { item ->
                        RecentRowLayout(item = item, viewModel = viewModel)
                    }
                }
            } else {
                // Non-lazy version for nested scroll
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.take(5).forEach { item ->
                        RecentRowLayout(item = item, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentRowLayout(item: RecentEntity, viewModel: FileZoneViewModel) {
    val file = File(item.path)
    val ext = FileOperations.getExtension(file)
    val dummyItem = FileItem(
        name = item.name,
        path = item.path,
        isFolder = false,
        size = item.size,
        lastModified = item.timestamp,
        extension = ext,
        mimeType = item.mimeType
    )

    Card(
        onClick = { viewModel.openFileItem(dummyItem) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconProperties = getFileIconAndColor(dummyItem)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconProperties.second.copy(alpha = 0.15f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = iconProperties.first, contentDescription = null, tint = iconProperties.second)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Viewed: ${formatDate(item.timestamp)}  ·  ${FileOperations.formatFileSize(item.size)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { viewModel.removeRecent(item.path) }) {
                Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ======================== BOOKMARKS TAB SCREEN ==============================

@Composable
fun BookmarksTabScreen(viewModel: FileZoneViewModel) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_bookmarks),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.empty_bookmarks_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.empty_bookmarks_desc),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(bookmarks) { item ->
                    BookmarkRowLayout(item = item, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun BookmarkRowLayout(item: BookmarkEntity, viewModel: FileZoneViewModel) {
    val file = File(item.path)
    val dummyItem = FileItem(
        name = item.name,
        path = item.path,
        isFolder = item.isFolder,
        size = if (item.isFolder) 0L else file.length(),
        lastModified = file.lastModified(),
        extension = FileOperations.getExtension(file),
        mimeType = FileOperations.getMimeType(file)
    )

    Card(
        onClick = {
            if (item.isFolder) {
                viewModel.navigateTo(file)
            } else {
                viewModel.openFileItem(dummyItem)
            }
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconProperties = getFileIconAndColor(dummyItem)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconProperties.second.copy(alpha = 0.15f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = iconProperties.first, contentDescription = null, tint = iconProperties.second)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isFolder) "Bookmark Folder" else "Bookmark File",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { viewModel.toggleBookmark(dummyItem) }) {
                Icon(Icons.Default.Favorite, contentDescription = "Active bookmark", tint = TechPurple)
            }
        }
    }
}

// ======================== SETTINGS (WITH PREMIUM HERO ART) ========================

@Composable
fun SettingsTabScreen(viewModel: FileZoneViewModel, onClose: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.nav_settings),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .background(PureMidnight),
            contentAlignment = Alignment.BottomStart
        ) {
            AsyncImage(
                model = R.drawable.filezone_hero_1781900126422,
                contentDescription = "File Zone Premium Onboarding",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "File Zone Utility",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "High speed ZIP compression and file management module active",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(stringResource(R.string.settings_theme_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Switch(
                    checked = themeMode == UIThemeMode.Dark,
                    onCheckedChange = { viewModel.toggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = TechPurple)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_lang), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(stringResource(R.string.settings_lang_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    val activeColor = MaterialTheme.colorScheme.primary
                    val idleColor = Color.Transparent

                    TextButton(
                        onClick = { viewModel.setLanguage(UILanguage.English) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (language == UILanguage.English) activeColor else idleColor,
                            contentColor = if (language == UILanguage.English) Color.Black else MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("language_toggle_en")
                    ) {
                        Text("EN", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { viewModel.setLanguage(UILanguage.Arabic) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (language == UILanguage.Arabic) activeColor else idleColor,
                            contentColor = if (language == UILanguage.Arabic) Color.Black else MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("language_toggle_ar")
                    ) {
                        Text("AR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val showStorageAnalyzer = viewModel.showStorageAnalyzer.collectAsStateWithLifecycle().value
        Card(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.runStorageAnalysis() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Storage Analyzer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Check large files and disk usage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Icon(Icons.Default.Storage, contentDescription = null, tint = TechPurple)
            }
        }
        
        if (showStorageAnalyzer) {
            StorageAnalyzerDialog(viewModel)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Version 1.0.0 (Senior Build)\nEngine: Kotlin Coroutines & ZipOutputStream",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StorageAnalyzerDialog(viewModel: FileZoneViewModel) {
    val isAnalyzing by viewModel.isAnalyzingStorage.collectAsStateWithLifecycle()
    val total by viewModel.storageDeviceTotal.collectAsStateWithLifecycle()
    val free by viewModel.storageDeviceFree.collectAsStateWithLifecycle()
    val largestFiles by viewModel.largestFiles.collectAsStateWithLifecycle()
    
    val used = total - free
    val percent = if (total > 0L) (used.toFloat() / total.toFloat()) else 0f

    androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.showStorageAnalyzer.value = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Storage Analyzer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { viewModel.showStorageAnalyzer.value = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isAnalyzing) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TechPurple)
                    }
                } else {
                    Text("Storage Usage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Box(modifier = Modifier.fillMaxWidth(percent).fillMaxHeight().background(TechPurple))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Used: ${FileOperations.formatFileSize(used)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text("Total: ${FileOperations.formatFileSize(total)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text("Top Ranked Largest Files", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(largestFiles) { file ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.showStorageAnalyzer.value = false
                                viewModel.openFileItem(file)
                            }, verticalAlignment = Alignment.CenterVertically) {
                                val iconRes = getFileIconAndColor(file)
                                Box(modifier = Modifier.size(40.dp).background(iconRes.second.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = iconRes.first, contentDescription = null, tint = iconRes.second)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = file.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = FileOperations.formatFileSize(file.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================== CUSTOM DETAIL VIEWER OVERLAYS ==============================

@Composable
fun TextViewerDialog(
    title: String,
    initialContent: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var textContent by remember { mutableStateOf(initialContent) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, TechPurple.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.button_cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(textContent) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewerDialog(
    file: File,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun ZipViewerDialog(
    zipFile: File,
    entries: List<String>,
    onExtract: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = TechPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = zipFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ZIP Contents Previewer (Scroll inside entries)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.endsWith("/")) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.endsWith("/")) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.button_cancel))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onExtract,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract All")
                    }
                }
            }
        }
    }
}

// ======================== RECYCLE BIN PRO SCREEN ==============================

@Composable
fun RecycleBinProScreen(
    viewModel: FileZoneViewModel,
    onClose: () -> Unit
) {
    val trashList by viewModel.recycleBinFiles.collectAsStateWithLifecycle()
    val trashStats by viewModel.recycleBinStats.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RECYCLE BIN PRO",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Items are auto-deleted after 7 days",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            if (trashList.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Bin", tint = Color(0xFFEF4444))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (trashList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🗑️", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Your Recycle Bin is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Files deleted from File Zone will appear here securely",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Stats summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL PROTECTION SIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = FileOperations.formatFileSize(trashStats.second),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("${trashList.size} items protected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trashList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (item.isFolder) Color(0xFFFEF3C7) else Color(0xFFE0F2FE),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (item.isFolder) "📁" else "📄")
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Orig: ${item.originalPath}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileOperations.formatFileSize(item.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Restore Button
                            IconButton(onClick = { viewModel.restoreTrashItem(item) }) {
                                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore", tint = Color(0xFF10B981))
                            }

                            // Delete Forever Button
                            IconButton(onClick = { viewModel.deleteTrashItemPermanently(item) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("This will permanently delete all protected items. This operation cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyRecycleBin()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ======================== STORAGE ANALYZER PRO SCREEN ==============================

@Composable
fun StorageAnalyzerProScreen(
    viewModel: FileZoneViewModel,
    onClose: () -> Unit
) {
    val totalSize by viewModel.storageDeviceTotal.collectAsStateWithLifecycle()
    val freeSize by viewModel.storageDeviceFree.collectAsStateWithLifecycle()
    val largestFiles by viewModel.largestFiles.collectAsStateWithLifecycle()
    val largestFolders by viewModel.largestFolders.collectAsStateWithLifecycle()
    val duplicateFiles by viewModel.duplicateFiles.collectAsStateWithLifecycle()
    val emptyFiles by viewModel.emptyFiles.collectAsStateWithLifecycle()
    val unusedFiles by viewModel.unusedFiles.collectAsStateWithLifecycle()
    val categoryAllocation by viewModel.categoryAllocation.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingStorage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("categories") } // categories, files_dirs, anomalies

    val usedSize = totalSize - freeSize
    val totalDisplayGb = totalSize / (1024f * 1024f * 1024f)
    val usedDisplayGb = usedSize / (1024f * 1024f * 1024f)
    val progress = if (totalSize > 0) (usedSize.toFloat() / totalSize) else 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "STORAGE ANALYZER PRO",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Deep device analysis & duplicate cleaner",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Disk Space Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("USED DEVICE SPACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = String.format(Locale.US, "%.1f GB of %.1f GB Used", usedDisplayGb, totalDisplayGb),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(text = String.format(Locale.US, "%.0f%% Used", progress * 100f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        TabRow(
            selectedTabIndex = when (activeTab) {
                "categories" -> 0
                "files_dirs" -> 1
                else -> 2
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = activeTab == "categories", onClick = { activeTab = "categories" }) {
                Text("Categories", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            }
            Tab(selected = activeTab == "files_dirs", onClick = { activeTab = "files_dirs" }) {
                Text("Large Files", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            }
            Tab(selected = activeTab == "anomalies", onClick = { activeTab = "anomalies" }) {
                Text("Duplicates", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isAnalyzing) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Analyzing your media repository...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (activeTab) {
                    "categories" -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryAllocation.forEach { (cat, bSize) ->
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when (cat) {
                                                    "Images" -> "🖼️"
                                                    "Videos" -> "🎬"
                                                    "Audio" -> "🎵"
                                                    "Archives (ZIP)" -> "📦"
                                                    "Documents" -> "📑"
                                                    "APK Packages" -> "📱"
                                                    else -> "📁"
                                                },
                                                fontSize = 24.sp
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(cat, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Size: " + FileOperations.formatFileSize(bSize), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "files_dirs" -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("LARGEST FILES IN SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (largestFiles.isEmpty()) {
                                item { Text("No files scanned yet.", fontSize = 12.sp, color = Color.Gray) }
                            }
                            items(largestFiles) { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📄", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(file.path, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(FileOperations.formatFileSize(file.size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("LARGEST DIRECTORIES IN SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (largestFolders.isEmpty()) {
                                item { Text("No directories scanned yet.", fontSize = 12.sp, color = Color.Gray) }
                            }
                            items(largestFolders) { folder ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📁", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folder.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${folder.childCount} entries", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text(FileOperations.formatFileSize(folder.size), fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("DUPLICATED FILE CLONES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                            if (duplicateFiles.isEmpty()) {
                                item { Text("No duplicate clones found in this directory.", fontSize = 12.sp, color = Color.Gray) }
                            }
                            items(duplicateFiles) { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("👯", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(file.path, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(FileOperations.formatFileSize(file.size), fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 12.sp)
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("EMPTY BLANK FILES (0 BYTES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (emptyFiles.isEmpty()) {
                                item { Text("No zero-byte empty files.", fontSize = 12.sp, color = Color.Gray) }
                            }
                            items(emptyFiles) { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📄", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(file.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("0 B", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================== APK MANAGER PRO SCREEN ==============================

@Composable
fun ApkManagerProScreen(
    viewModel: FileZoneViewModel,
    onClose: () -> Unit
) {
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningApps.collectAsStateWithLifecycle()

    var activeAppTab by remember { mutableStateOf("user") } // user, system
    var query by remember { mutableStateOf("") }

    val filteredApps = apps.filter {
        val matchesTab = if (activeAppTab == "user") !it.isSystemApp else it.isSystemApp
        matchesTab && (it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "APK MANAGER PRO", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Extract, backup and package split systems", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            IconButton(onClick = { viewModel.loadInstalledApps() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh applications")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search text field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search installed packages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tab switches
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { activeAppTab = "user" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeAppTab == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (activeAppTab == "user") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("User Apps")
            }
            Button(
                onClick = { activeAppTab = "system" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeAppTab == "system") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (activeAppTab == "system") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("System Apps")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isScanning) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Scanning packages database...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🤖", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(app.packageName, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("v${app.versionName} (${app.versionCode})", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            // EXTRACT TO DOWNLOADS
                            IconButton(onClick = { viewModel.extractAndBackupApk(app, isBackup = false) }) {
                                Icon(Icons.Default.Download, contentDescription = "Extract APK", tint = MaterialTheme.colorScheme.primary)
                            }

                            // BACKUP TO SANDBOX
                            IconButton(onClick = { viewModel.extractAndBackupApk(app, isBackup = true) }) {
                                Icon(Icons.Default.Backup, contentDescription = "Backup APK", tint = Color(0xFFF59E0B))
                            }

                            // SHARE APK
                            IconButton(onClick = { viewModel.shareAppApk(app) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share App", tint = Color(0xFF10B981))
                            }
                        }
                    }
                }
            }
        }
    }
}


// ======================== PACKAGE PREVIEW DIALOG (XAPK/APK Support) ==============================

@Composable
fun PackagePreviewDialog(
    preview: com.filezone.app.viewmodel.FileZoneViewModel.PackagePreviewInfo,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁 ", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Package Installer V3", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = preview.label,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Application ID:", fontSize = 12.sp, color = Color.Gray)
                    Text(preview.packageName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Version Number:", fontSize = 12.sp, color = Color.Gray)
                    Text("${preview.versionName} (${preview.versionCode})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Installer Format:", fontSize = 12.sp, color = Color.Gray)
                    Text(preview.fileType, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Inner APK splits count:", fontSize = 12.sp, color = Color.Gray)
                    Text("${preview.innerApkCount} binaries", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This premium package format (XAPK/APKS/APKM) will be unpacked and dynamically installed on this Android environment.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Justify
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Install Package")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateOptionsDialog(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = onCreateFolder,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Create Folder", fontWeight = FontWeight.Medium)
                    }
                }

                Surface(
                    onClick = onCreateFile,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Create File", fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun VideoPlayerDialog(
    file: File,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name, color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(file.absolutePath)
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { it.start() }
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f)
            )
        }
    }
}

@Composable
fun AudioPlayerDialog(
    file: File,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    DisposableEffect(file) {
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            duration = this.duration
            setOnCompletionListener { isPlaying = false }
        }
        mediaPlayer = mp
        onDispose {
            mp.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer?.currentPosition ?: 0
            kotlinx.coroutines.delay(500)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Audio Preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(file.name, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { 
                        currentPosition = it.toInt()
                        mediaPlayer?.seekTo(it.toInt())
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPosition.toLong()), fontSize = 12.sp)
                    Text(formatTime(duration.toLong()), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (isPlaying) {
                    mediaPlayer?.pause()
                } else {
                    mediaPlayer?.start()
                }
                isPlaying = !isPlaying
            }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        }
    )
}

@Composable
fun PdfViewerDialog(
    file: File,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val (renderer, descriptor) = remember(file) {
        val d = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(d)
        Pair(r, d)
    }

    DisposableEffect(file) {
        onDispose {
            renderer.close()
            descriptor.close()
        }
    }
    
    val pageCount = renderer.pageCount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pageCount) { index ->
                    PdfPage(renderer, index)
                }
            }
        }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, index: Int) {
    val bitmap = remember(index) {
        val page = renderer.openPage(index)
        val b = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        b
    }
    
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Page ${index + 1}",
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun FavoritesTabScreen(viewModel: FileZoneViewModel) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Your Favorites",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Quick access to your most important items",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No favorites yet", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites) { fav ->
                    val file = File(fav.path)
                    ListItem(
                        headlineContent = { Text(fav.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(if (fav.isFolder) "Folder" else FileOperations.formatFileSize(fav.size)) },
                        leadingContent = {
                            Icon(
                                imageVector = if (fav.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (fav.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.toggleFavorite(FileItem(fav.name, fav.path, fav.isFolder, fav.size, 0, "", "", 0, false, true)) }) {
                                Icon(Icons.Default.Favorite, contentDescription = "Remove", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (fav.isFolder) {
                                    viewModel.navigateTo(file)
                                } else {
                                    viewModel.openFileItem(FileItem(fav.name, fav.path, fav.isFolder, fav.size, 0, "", "", 0, false, true))
                                }
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingScreen(
    viewModel: FileZoneViewModel,
    themeMode: UIThemeMode,
    language: UILanguage
) {
    val isArabic = language == UILanguage.Arabic

    val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val hasInstallPermission by viewModel.hasInstallPermission.collectAsStateWithLifecycle()

    var showInitialDialog by rememberSaveable { mutableStateOf(true) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.checkAndSetStoragePermission()
    }

    if (showInitialDialog) {
        AlertDialog(
            onDismissRequest = { showInitialDialog = false },
            title = {
                Text(
                    text = if (isArabic) "مطلوب تفعيل الصلاحيات" else "Permissions Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = if (isArabic) {
                        "يتطلب تطبيق فايل زون الصلاحيات التالية لتعمل جميع المزايا بشكل دائم ودون انقطاع:\n\n" +
                                "1. إذن الوصول للملفات (إلزامي لتصفح وتعديل الملفات)\n" +
                                "2. إذن الإشعارات (لمعرفة تقدم عمليات النسخ والضغط)\n" +
                                "3. إذن تثبيت التطبيقات (لتثبيت ملفات APK مباشرة)\n\n" +
                                "عند النقر فوق 'حسناً'، يرجى النقر على زر 'تفعيل' لكل إذن مفقود."
                    } else {
                        "File Zone requires the following permissions to function permanently and flawlessly:\n\n" +
                                "1. Storage Permission (Required for managing files)\n" +
                                "2. Notifications Permission (To show progress of tasks)\n" +
                                "3. Install App Permission (To install APKs directly)\n\n" +
                                "Tap 'OK' then click 'GRANT' for any pending permissions."
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInitialDialog = false
                        // Prefill prompt sequentially where possible
                        if (!hasStoragePermission) {
                            viewModel.requestStoragePermission()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!hasInstallPermission) {
                            viewModel.requestInstallPermission()
                        }
                    }
                ) {
                    Text(
                        text = if (isArabic) "حسناً، فهمت" else "OK, I understand",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (themeMode == UIThemeMode.Dark) Color(0xFF1E293B) else Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (themeMode == UIThemeMode.Dark) {
                        listOf(CharcoalDark, PureMidnight)
                    } else {
                        listOf(WarmWhite, Color(0xFFE2E8F0))
                    }
                )
            )
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // App Logo Icon
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp)
            )
        }

        Text(
            text = if (isArabic) "مركز إدارة صلاحيات التطبيق" else "Permissions Management Center",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (isArabic) {
                "لكي يعمل التطبيق كمدير ملفات متكامل واحترافي (مثل ZArchiver)، يرجى تفعيل الصلاحيات الأساسية اللازمة أدناه بشكل دائم وعام."
            } else {
                "To operate as a full-featured file manager (similar to ZArchiver), please review and grant the required system permissions below."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Storage Permission Card
        PremiumPermissionCard(
            title = if (isArabic) "إذن الوصول لجميع الملفات" else "All Files Storage Access",
            description = if (isArabic) "إلزامي للبحث، الحذف، النقل، الضغط والتعديل على ذاكرة الهاتف بشكل دائم." else "Required to permanently browse, search, delete, copy, paste and compress files.",
            isGranted = hasStoragePermission,
            icon = Icons.Default.FolderOpen,
            isArabic = isArabic,
            onGrantClick = { viewModel.requestStoragePermission() }
        )

        // 2. Notifications Permission Card
        PremiumPermissionCard(
            title = if (isArabic) "إذن إشعارات النظام" else "System Notifications",
            description = if (isArabic) "لعرض تقدم عمليات النقل والنسخ وضغط الملفات في الخلفية بنجاح وبشكل دائم." else "Displays persistent status bar updates of copy, cut and zip tasks.",
            isGranted = hasNotificationPermission,
            icon = Icons.Default.Notifications,
            isArabic = isArabic,
            onGrantClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.checkAndSetStoragePermission()
                }
            }
        )

        // 3. Install Unknown Packages Card
        PremiumPermissionCard(
            title = if (isArabic) "إذن تثبيت تطبيقات APK" else "Install Packages (APKs)",
            description = if (isArabic) "لتثبيت ملفات تطبيقات الـ APK المستلمة أو المخزنة في جهازك مباشرة دون قيود." else "Allows installing custom package setups and APKs from the storage viewer.",
            isGranted = hasInstallPermission,
            icon = Icons.Default.SystemUpdate,
            isArabic = isArabic,
            onGrantClick = { viewModel.requestInstallPermission() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Action to manually re-evaluate and proceed
        Button(
            onClick = {
                viewModel.checkAndSetStoragePermission()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_proceed_btn"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasStoragePermission) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasStoragePermission) {
                    if (isArabic) "متابعة ودخول التطبيق" else "PROCEED TO APP"
                } else {
                    if (isArabic) "إعادة التحقق من الصلاحيات" else "RE-CHECK ALL PERMISSIONS"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun PremiumPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isArabic: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon space
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text space
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action or Status status
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isArabic) "مفعّل" else "Active",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = if (isArabic) "تفعيل" else "Grant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
