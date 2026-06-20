package com.filezone.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filezone.app.data.BookmarkEntity
import com.filezone.app.data.FileZoneDatabase
import com.filezone.app.data.FileZoneRepository
import com.filezone.app.data.RecentEntity
import com.filezone.app.util.FileOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

// UI Model for file item display
data class FileItem(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val childCount: Int = 0,
    val isBookmarked: Boolean = false,
    val isFavorite: Boolean = false
)

sealed interface UIThemeMode {
    object Light : UIThemeMode
    object Dark : UIThemeMode
}

sealed interface UILanguage {
    object English : UILanguage
    object Arabic : UILanguage
}

data class SearchCriteria(
    val extension: String = "",
    val minSize: Long = 0L,
    val maxSize: Long = Long.MAX_VALUE,
    val type: String = "All",
    val minDate: Long = 0L,
    val isRecursive: Boolean = false
)

class FileZoneViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = FileZoneDatabase.getDatabase(context)
    private val repository = FileZoneRepository(db.dao())

    // Roots
    val sandboxRoot = context.filesDir
    
    val externalRoot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
        // Fallback if StorageManager is null or primary volume lacks a directory
        sm?.storageVolumes?.firstOrNull { it.isPrimary }?.directory ?: run {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()
        }
    } else {
        // Fallback for Android 7-10.
        // A complete replacement with SAF/DocumentFile is not possible without rewriting the entire core file operations.
        // The app utilizes native java.io.File for copy, move, zip with MANAGE_EXTERNAL_STORAGE permission.
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory()
    }
    
    val downloadsRoot = File(externalRoot, Environment.DIRECTORY_DOWNLOADS)
    val documentsRoot = File(externalRoot, Environment.DIRECTORY_DOCUMENTS)

    // Preferences
    private val _themeMode = MutableStateFlow<UIThemeMode>(UIThemeMode.Dark)
    val themeMode: StateFlow<UIThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow<UILanguage>(UILanguage.English)
    val language: StateFlow<UILanguage> = _language.asStateFlow()

    // Explorer State
    private val _currentDirectory = MutableStateFlow<File>(sandboxRoot)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _directoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val directoryFiles: StateFlow<List<FileItem>> = _directoryFiles.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    // History and Selection
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    // Clipboard: paths, isMove
    private val _clipboardPaths = MutableStateFlow<Set<String>>(emptySet())
    val clipboardPaths: StateFlow<Set<String>> = _clipboardPaths.asStateFlow()
    
    private val _isMoveOperation = MutableStateFlow(false)
    val isMoveOperation: StateFlow<Boolean> = _isMoveOperation.asStateFlow()

    // UI Dialog State flags
    val showCreateOptionsDialog = MutableStateFlow(false)
    val showCreateFileDialog = MutableStateFlow(false)
    val showCreateFolderDialog = MutableStateFlow(false)
    val showRenameDialog = MutableStateFlow<FileItem?>(null)
    val showDeleteConfirmDialog = MutableStateFlow(false)
    val showZipConfirmDialog = MutableStateFlow(false)

    // Dynamic viewers
    val activeTextFileContent = MutableStateFlow<String?>(null)
    val activeTextFileName = MutableStateFlow<String?>(null)
    val activeImageFile = MutableStateFlow<File?>(null)
    
    // Storage Analyzer State
    val showStorageAnalyzer = MutableStateFlow(false)
    val isAnalyzingStorage = MutableStateFlow(false)
    private val _storageDeviceTotal = MutableStateFlow(1L)
    val storageDeviceTotal: StateFlow<Long> = _storageDeviceTotal.asStateFlow()
    private val _storageDeviceFree = MutableStateFlow(0L)
    val storageDeviceFree: StateFlow<Long> = _storageDeviceFree.asStateFlow()
    
    // Largest files cache
    private val _largestFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val largestFiles: StateFlow<List<FileItem>> = _largestFiles.asStateFlow()

    // --- 1. RECYCLE BIN DATA ---
    val trashDir = File(sandboxRoot, ".trash")
    val recycleBinFiles: StateFlow<List<com.filezone.app.data.RecycleBinEntity>> = repository.recycleBinFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recycleBinStats = repository.recycleBinFiles.map { list ->
        val count = list.size
        val size = list.sumOf { it.size }
        count to size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0L)

    // --- 2. STORAGE ANALYZER PRO DATA ---
    data class FolderSizeItem(val name: String, val path: String, val size: Long, val childCount: Int)
    
    private val _largestFolders = MutableStateFlow<List<FolderSizeItem>>(emptyList())
    val largestFolders: StateFlow<List<FolderSizeItem>> = _largestFolders.asStateFlow()

    private val _duplicateFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val duplicateFiles: StateFlow<List<FileItem>> = _duplicateFiles.asStateFlow()

    private val _emptyFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val emptyFiles: StateFlow<List<FileItem>> = _emptyFiles.asStateFlow()

    private val _unusedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val unusedFiles: StateFlow<List<FileItem>> = _unusedFiles.asStateFlow()

    private val _categoryAllocation = MutableStateFlow<Map<String, Long>>(emptyMap())
    val categoryAllocation: StateFlow<Map<String, Long>> = _categoryAllocation.asStateFlow()

    // --- 3. APK MANAGER PRO DATA ---
    data class AppItem(
        val name: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val sourceDir: String,
        val isSystemApp: Boolean
    )
    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()
    val isScanningApps = MutableStateFlow(false)

    // --- 5. PACKAGE SUPPORT DATA ---
    data class PackagePreviewInfo(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val fileType: String,
        val innerApkCount: Int,
        val icon: android.graphics.drawable.Drawable? = null
    )
    val activePackagePreview = MutableStateFlow<PackagePreviewInfo?>(null)
    val activePackageFile = MutableStateFlow<File?>(null)

    fun runStorageAnalysis() {
        if (isAnalyzingStorage.value) return
        isAnalyzingStorage.value = true
        showStorageAnalyzer.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = externalRoot
                val stat = android.os.StatFs(root.absolutePath)
                _storageDeviceTotal.value = stat.totalBytes
                _storageDeviceFree.value = stat.availableBytes
                
                // Find largest files (limited scan for performance)
                val allFiles = mutableListOf<File>()
                findFilesRecursive(root, allFiles, 10000) // limit to 10k files to prevent ANR/Delay
                
                val topFiles = allFiles.filter { it.isFile }
                    .sortedByDescending { it.length() }
                    .take(10)
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isFolder = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = FileOperations.getExtension(file),
                            mimeType = FileOperations.getMimeType(file)
                        )
                    }
                
                _largestFiles.value = topFiles
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isAnalyzingStorage.value = false
            }
        }
    }

    private fun findFilesRecursive(dir: File, result: MutableList<File>, maxLimit: Int) {
        if (result.size >= maxLimit) return
        try {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (result.size >= maxLimit) return
                if (f.isDirectory) {
                    findFilesRecursive(f, result, maxLimit)
                } else {
                    result.add(f)
                }
            }
        } catch (e: Exception) {
            // Ignore access denied
        }
    }
    val activeVideoFile = MutableStateFlow<File?>(null)
    val activeAudioFile = MutableStateFlow<File?>(null)
    val activePdfFile = MutableStateFlow<File?>(null)
    val activeZipEntries = MutableStateFlow<List<String>>(emptyList())
    val activeZipFile = MutableStateFlow<File?>(null)

    // Flow integration for Db
    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<RecentEntity>> = repository.recentFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<com.filezone.app.data.FavoriteEntity>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCriteria = MutableStateFlow(SearchCriteria())
    val searchCriteria: StateFlow<SearchCriteria> = _searchCriteria.asStateFlow()

    // Bento category element state flows
    private val _imageCount = MutableStateFlow(0)
    val imageCount: StateFlow<Int> = _imageCount.asStateFlow()

    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()

    private val _docCount = MutableStateFlow(0)
    val docCount: StateFlow<Int> = _docCount.asStateFlow()

    private val _zipCount = MutableStateFlow(0)
    val zipCount: StateFlow<Int> = _zipCount.asStateFlow()

    fun updateCategoryCounts() {
        val root = sandboxRoot
        var images = 0
        var videos = 0
        var docs = 0
        var zips = 0

        fun scanDir(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    scanDir(f)
                } else {
                    val ext = f.extension.lowercase()
                    val mime = FileOperations.getMimeType(f)
                    when {
                        ext == "zip" -> zips++
                        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> images++
                        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi") -> videos++
                        mime.startsWith("text/") || ext in listOf("txt", "pdf", "doc", "docx", "xls", "xlsx") -> docs++
                    }
                }
            }
        }
        scanDir(root)
        _imageCount.value = images
        _videoCount.value = videos
        _docCount.value = docs
        _zipCount.value = zips
    }

    // Permission dynamic checking states
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _hasInstallPermission = MutableStateFlow(true)
    val hasInstallPermission: StateFlow<Boolean> = _hasInstallPermission.asStateFlow()

    fun checkAndSetStoragePermission() {
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val writePerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            readPerm == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    writePerm == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasStoragePermission.value = storageGranted

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _hasNotificationPermission.value = notificationGranted

        val installGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
        _hasInstallPermission.value = installGranted
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    init {
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        checkAndSetStoragePermission()
        // Dynamic sandbox population so the user gets an outstanding workspace demo initially!
        viewModelScope.launch(Dispatchers.IO) {
            populateSandboxIfNeeded()
            autoCleanRecycleBin()
            loadCurrentDirectoryFiles()
        }
    }

    fun toggleTheme() {
        _themeMode.value = if (_themeMode.value == UIThemeMode.Dark) UIThemeMode.Light else UIThemeMode.Dark
    }

    fun analyzeStorage(): Map<String, Long> {
        val analysisResult = mutableMapOf<String, Long>()
        val root = externalRoot
        
        // Basic folder size analysis
        root.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                analysisResult[file.name] = file.walk().sumOf { it.length() }
            } else {
                analysisResult["Files"] = analysisResult.getOrDefault("Files", 0L) + file.length()
            }
        }
        return analysisResult
    }

    fun setLanguage(lang: UILanguage) {
        _language.value = lang
    }

    /**
     * File system navigation
     */
    fun navigateTo(directory: File) {
        viewModelScope.launch {
            _currentDirectory.value = directory
            _selectedPaths.value = emptySet()
            _searchQuery.value = ""
            _searchCriteria.value = SearchCriteria()
            loadCurrentDirectoryFiles()
        }
    }

    fun navigateUp(): Boolean {
        val parent = _currentDirectory.value.parentFile
        if (parent != null && _currentDirectory.value.absolutePath != sandboxRoot.parentFile?.absolutePath) {
            navigateTo(parent)
            return true
        }
        return false
    }

    /**
     * Loading Files with filtering
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            loadCurrentDirectoryFiles()
        }
    }

    fun updateSearchCriteria(criteria: SearchCriteria) {
        _searchCriteria.value = criteria
        viewModelScope.launch {
            loadCurrentDirectoryFiles()
        }
    }

    fun setCategoryView(type: String, recursive: Boolean) {
        _currentDirectory.value = externalRoot
        _searchQuery.value = ""
        updateSearchCriteria(SearchCriteria(type = type, isRecursive = recursive))
    }

    suspend fun loadCurrentDirectoryFiles() {
        checkAndSetStoragePermission()
        _isLoadingFiles.value = true
        withContext(Dispatchers.IO) {
            try {
                val dir = _currentDirectory.value
                val criteria = _searchCriteria.value
                val isCategoryView = criteria.type.isNotEmpty()

                val filesList = if (dir.exists()) dir.listFiles() else null
                if (filesList == null && dir.absolutePath != sandboxRoot.absolutePath && !isCategoryView) {
                    _hasStoragePermission.value = false
                }

                // If in category view, recursively compile files from both sandboxRoot, externalFilesDir, and externalRoot (if we have permission)
                val finalFilesList = if (isCategoryView) {
                    val gatheredFiles = mutableListOf<File>()

                    // 1. Walk sandboxRoot (Private Writable Local Sandbox)
                    try {
                        sandboxRoot.walkTopDown()
                            .maxDepth(5)
                            .onEnter { file ->
                                !file.name.startsWith(".") && file.name != "Android" && file.name != "data"
                            }
                            .filter { it.isFile }
                            .forEach { gatheredFiles.add(it) }
                    } catch (e: Exception) {
                        android.util.Log.e("FileZoneViewModel", "Error walking sandboxRoot for category: ${criteria.type}", e)
                    }

                    // 2. Walk externalFilesDir (App’s Private Writable External Directory - doesn't require separate permission)
                    context.getExternalFilesDir(null)?.let { extFilesDir ->
                        try {
                            extFilesDir.walkTopDown()
                                .maxDepth(5)
                                .onEnter { file ->
                                    !file.name.startsWith(".") && file.name != "Android" && file.name != "data"
                                }
                                .filter { it.isFile }
                                .forEach { gatheredFiles.add(it) }
                        } catch (e: Exception) {
                            android.util.Log.e("FileZoneViewModel", "Error walking externalFilesDir for category: ${criteria.type}", e)
                        }
                    }

                    // 3. Walk externalRoot (Global Shared Files - only if storage permission is actually granted)
                    if (_hasStoragePermission.value) {
                        try {
                            externalRoot.walkTopDown()
                                .maxDepth(5)
                                .onEnter { file ->
                                    !file.name.startsWith(".") && file.name != "Android" && file.name != "data"
                                }
                                .filter { it.isFile }
                                .forEach { gatheredFiles.add(it) }
                        } catch (e: Exception) {
                            android.util.Log.e("FileZoneViewModel", "Error walking externalRoot for category: ${criteria.type}", e)
                        }
                    }

                    gatheredFiles.toTypedArray()
                } else {
                    // Normal folder navigation scan
                    if (criteria.isRecursive) {
                        dir.walkTopDown()
                            .maxDepth(5)
                            .onEnter { file ->
                                !file.name.startsWith(".") && file.name != "Android" && file.name != "data"
                            }
                            .filter { it.isFile }
                            .toList()
                            .toTypedArray()
                    } else {
                        filesList ?: emptyArray()
                    }
                }

                // Get bookmarked and favorite status list
                val bookmarkedPaths = bookmarks.value.map { it.path }.toSet()
                val favoritePaths = favorites.value.map { it.path }.toSet()

                val items = finalFilesList.map { file ->
                    val isFolder = file.isDirectory
                    val size = if (isFolder) 0L else file.length()
                    val childCount = if (isFolder) (file.listFiles()?.size ?: 0) else 0
                    
                    FileItem(
                        name = file.name,
                        path = file.absolutePath,
                        isFolder = isFolder,
                        size = size,
                        lastModified = file.lastModified(),
                        extension = FileOperations.getExtension(file),
                        mimeType = FileOperations.getMimeType(file),
                        childCount = childCount,
                        isBookmarked = bookmarkedPaths.contains(file.absolutePath),
                        isFavorite = favoritePaths.contains(file.absolutePath)
                    )
                }.sortedWith(compareBy<FileItem> { !it.isFolder }.thenBy { it.name.lowercase() })

                // Filter based on search query and criteria
                val query = _searchQuery.value
                
                val filteredItems = items.filter { item ->
                    val nameMatch = query.isEmpty() || item.name.lowercase().contains(query.lowercase())
                    val extMatch = criteria.extension.isEmpty() || item.extension.lowercase().contains(criteria.extension.lowercase())
                    val sizeMatch = item.size >= criteria.minSize && item.size <= criteria.maxSize
                    val typeMatch = when (criteria.type) {
                        "Image" -> item.mimeType.startsWith("image/")
                        "Video" -> item.mimeType.startsWith("video/")
                        "Audio" -> item.mimeType.startsWith("audio/")
                        "Document" -> item.mimeType.contains("pdf") || item.mimeType.contains("text") || item.mimeType.contains("word")
                        "APK" -> item.extension.lowercase() in listOf("apk", "xapk", "apks", "apkm") || item.mimeType.contains("vnd.android.package-archive")
                        "Zip" -> item.extension.lowercase() == "zip" || item.mimeType.contains("zip")
                        else -> true
                    }
                    
                    nameMatch && extMatch && sizeMatch && typeMatch
                }

                _directoryFiles.value = filteredItems
                updateCategoryCounts()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingFiles.value = false
            }
        }
    }

    /**
     * Multi Select
     */
    fun togglePathSelected(path: String) {
        val current = _selectedPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedPaths.value = current
    }

    fun clearSelections() {
        _selectedPaths.value = emptySet()
    }

    /**
     * Bookmark Management
     */
    fun toggleBookmark(fileItem: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val isBookmarked = repository.isBookmarked(fileItem.path)
            if (isBookmarked) {
                repository.removeBookmark(fileItem.path)
            } else {
                repository.addBookmark(fileItem.path, fileItem.name, fileItem.isFolder)
            }
            loadCurrentDirectoryFiles()
        }
    }

    fun toggleFavorite(fileItem: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val isFavorite = repository.isFavorite(fileItem.path)
            if (isFavorite) {
                repository.removeFavorite(fileItem.path)
            } else {
                repository.addFavorite(fileItem.path, fileItem.name, fileItem.isFolder, fileItem.size)
            }
            loadCurrentDirectoryFiles()
        }
    }

    /**
     * Clipboard Utilities (Copy / Move mechanics)
     */
    fun copySelectedToClipboard(move: Boolean) {
        _clipboardPaths.value = _selectedPaths.value
        _isMoveOperation.value = move
        _selectedPaths.value = emptySet()
        showToast(if (move) "Selected items cut" else "Selected items copied")
    }

    fun clearClipboard() {
        _clipboardPaths.value = emptySet()
        _isMoveOperation.value = false
    }

    fun pasteClipboard() {
        if (_clipboardPaths.value.isEmpty()) return
        val destDir = _currentDirectory.value

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var errorCount = 0
            _clipboardPaths.value.forEach { path ->
                val srcFile = File(path)
                val destFile = File(destDir, srcFile.name)
                try {
                    if (srcFile.exists()) {
                        if (_isMoveOperation.value) {
                            FileOperations.move(srcFile, destFile)
                            refreshMediaStore(srcFile.absolutePath, destFile.absolutePath)
                        } else {
                            FileOperations.copy(srcFile, destFile)
                            refreshMediaStore(null, destFile.absolutePath)
                        }
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCount++
                }
            }

            clearClipboard()
            loadCurrentDirectoryFiles()

            withContext(Dispatchers.Main) {
                if (errorCount == 0) {
                    Toast.makeText(context, "Operations completed successfully ($successCount files)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Completed with some errors ($successCount success, $errorCount errors)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * File Creation
     */
    fun createFile(name: String) {
        val parent = _currentDirectory.value
        val newFile = File(parent, name.trim())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!newFile.exists()) {
                    val created = newFile.createNewFile()
                    if (created) {
                        showToast("File created: $name")
                    } else {
                        showToast("Failed to create file")
                    }
                } else {
                    showToast("File already exists")
                }
                loadCurrentDirectoryFiles()
                showCreateFileDialog.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Error creating file: ${e.message}")
            }
        }
    }

    /**
     * Folder Creation
     */
    fun createFolder(name: String) {
        if (name.isBlank()) return
        val newDir = File(_currentDirectory.value, name.trim())
        viewModelScope.launch(Dispatchers.IO) {
            if (!newDir.exists()) {
                val created = newDir.mkdirs()
                if (created) {
                    showToast("Folder created: $name")
                } else {
                    showToast("Failed to create folder")
                }
            } else {
                showToast("Folder already exists")
            }
            loadCurrentDirectoryFiles()
            showCreateFolderDialog.value = false
        }
    }

    /**
     * Rename
     */
    fun renameItem(item: FileItem, newName: String) {
        if (newName.isBlank() || newName == item.name) {
            showRenameDialog.value = null
            return
        }
        val file = File(item.path)
        val parent = file.parentFile
        val dest = File(parent, newName.trim())
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists() && !dest.exists()) {
                val renamed = FileOperations.rename(file, dest)
                if (renamed) {
                    refreshMediaStore(file.absolutePath, dest.absolutePath)
                    showToast("Renamed successfully")
                    // If bookmarked, update bookmark
                    if (repository.isBookmarked(item.path)) {
                        repository.removeBookmark(item.path)
                        repository.addBookmark(dest.absolutePath, dest.name, item.isFolder)
                    }
                } else {
                    showToast("Rename failed")
                }
            } else {
                showToast("Item name conflict")
            }
            loadCurrentDirectoryFiles()
            showRenameDialog.value = null
        }
    }

    /**
     * Move files to Recycle Bin (Trash)
     */
    fun deleteSelectedItems() {
        val targets = _selectedPaths.value
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var trashedCount = 0
            if (!trashDir.exists()) {
                trashDir.mkdirs()
            }
            targets.forEach { path ->
                val srcFile = File(path)
                if (srcFile.exists() && srcFile.absolutePath != trashDir.absolutePath) {
                    val uuid = java.util.UUID.randomUUID().toString()
                    val trashFile = File(trashDir, "${uuid}_${srcFile.name}")
                    try {
                        val size = if (srcFile.isDirectory) getFolderSize(srcFile) else srcFile.length()
                        
                        // Attempt to move
                        var moveSuccess = false
                        try {
                            FileOperations.move(srcFile, trashFile)
                            moveSuccess = !srcFile.exists()
                        } catch (e: Exception) {
                            android.util.Log.e("FileZoneViewModel", "Error while moving to trash: ${srcFile.absolutePath}", e)
                        }
                        
                        if (moveSuccess) {
                            refreshMediaStore(srcFile.absolutePath, trashFile.absolutePath)
                            repository.addToRecycleBin(
                                trashPath = trashFile.absolutePath,
                                originalPath = srcFile.absolutePath,
                                name = srcFile.name,
                                size = size,
                                isFolder = srcFile.isDirectory
                            )
                            repository.removeBookmark(path)
                            repository.removeRecent(path)
                            trashedCount++
                            android.util.Log.d("FileZoneViewModel", "Move to trash successful: ${srcFile.absolutePath}")
                        } else {
                            android.util.Log.e("FileZoneViewModel", "Move to trash failed for: ${srcFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FileZoneViewModel", "Exception while deletion process: ${srcFile.absolutePath}", e)
                    }
                }
            }
            if (trashedCount > 0) {
                showToast("Successfully moved $trashedCount items to Recycle Bin")
            } else {
                showToast("Failed to move items to Recycle Bin")
            }
            _selectedPaths.value = emptySet()
            loadCurrentDirectoryFiles()
            showDeleteConfirmDialog.value = false
        }
    }

    /**
     * ZIP compression
     */
    fun compressSelectedToZip(zipName: String) {
        val targets = _selectedPaths.value.map { File(it) }
        if (targets.isEmpty()) return
        val archiveName = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val zipFile = File(_currentDirectory.value, archiveName)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileOperations.zip(targets, zipFile)
                showToast("ZIP Created: $archiveName")
                _selectedPaths.value = emptySet()
                loadCurrentDirectoryFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to create ZIP: ${e.localizedMessage}")
            }
            showZipConfirmDialog.value = false
        }
    }

    /**
     * ZIP Decompression
     */
    fun decompressZip(fileItem: FileItem) {
        val srcFile = File(fileItem.path)
        // Extract in same folder inside a brand new directory named after the zip
        val baseFolder = File(_currentDirectory.value, srcFile.nameWithoutExtension + "_extracted")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileOperations.unzip(srcFile, baseFolder)
                showToast("Extracted to: ${baseFolder.name}")
                loadCurrentDirectoryFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Unzip failed: ${e.localizedMessage}")
            }
        }
    }

    /**
 * Preview files internally and add to Recents database log.
 */
fun openFileItem(item: FileItem) {
    val file = File(item.path)
    if (!file.exists()) return

    if (item.isFolder) {
        navigateTo(file)
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        repository.addRecent(item.path, item.name, item.size, item.mimeType)
    }

    if (FileOperations.isImage(file)) {
        activeImageFile.value = file
    } else if (FileOperations.isVideo(file)) {
        activeVideoFile.value = file
    } else if (FileOperations.isAudio(file)) {
        activeAudioFile.value = file
    } else if (file.extension.lowercase(Locale.ROOT) == "pdf") {
        activePdfFile.value = file
    } else if (file.extension.lowercase(Locale.ROOT) in listOf("apk", "xapk", "apks", "apkm")) {
        parsePackageFile(file)
    } else if (FileOperations.isText(file)) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = file.readText(Charsets.UTF_8)
                activeTextFileName.value = file.name
                activeTextFileContent.value = content
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to load text content")
            }
        }
    } else if (FileOperations.isZip(file)) {
        activeZipFile.value = file
        activeZipEntries.value = FileOperations.previewZipEntries(file)
    } else {
        // Fallback to system-level intent sharing or open
        openWithSystem(file, item.mimeType)
    }
}

private fun openWithSystem(file: File, mimeType: String) {
    viewModelScope.launch(Dispatchers.Main) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open file with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file type directly.", Toast.LENGTH_SHORT).show()
        }
    }
}

    fun clearRecents() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearRecents()
        }
    }

    fun removeRecent(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeRecent(path)
        }
    }

    fun saveTextFile(fileName: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(_currentDirectory.value, fileName)
                file.writeText(content, Charsets.UTF_8)
                activeTextFileContent.value = null
                activeTextFileName.value = null
                loadCurrentDirectoryFiles()
                showToast("Saved successfully")
            } catch (e: Exception) {
                showToast("Failed to save")
            }
        }
    }

    fun shareFileItem(item: FileItem) {
        val file = File(item.path)
        if (!file.exists()) return
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(intent, "Share file").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dynamic Onboarding/Demo Sandbox folder pre-generation
     */
    private fun populateSandboxIfNeeded() {
        try {
            val checkFile = File(sandboxRoot, "Welcome_Guide.txt")
            if (!checkFile.exists()) {
                // Intro Guide
                checkFile.writeText(
                    "Welcome to File Zone!\n\n" +
                    "This is a high-performance utility designed to give you robust and instant file zipping, " +
                    "unzipping, bookmarks, and rich internal visual content previewing built completely with Jetpack Compose.\n\n" +
                    "Key Capabilities Available:\n" +
                    "- Tap and hold any item to activate multi-selection mode.\n" +
                    "- Tap the heart icon to add frequently accessed files or directories to Bookmarks!\n" +
                    "- Deep system capabilities like Copying, Moving, Deleting, and Renaming.\n" +
                    "- Instant preview inside zip files without extracting.\n\n" +
                    "Project created by Senior Android Developers.", 
                    Charsets.UTF_8
                )

                // Subdirectory structure
                val sampleImages = File(sandboxRoot, "Media_Gallery")
                sampleImages.mkdirs()

                // Generate some sample files inside Media_Gallery
                File(sampleImages, "Notes.txt").writeText("Buy groceries\nCall Mom\nFinish Kotlin refactoring!", Charsets.UTF_8)
                File(sampleImages, "config.json").writeText("{\n  \"app_name\": \"File Zone\",\n  \"version\": \"3.0.0\",\n  \"pro_enabled\": true\n}", Charsets.UTF_8)
                File(sampleImages, "index.html").writeText("<html><body><h1>Hello from File Zone!</h1><p>Internal Preview works great.</p></body></html>", Charsets.UTF_8)

                // Let's copy our raw generated visual splash image so the image viewer is loaded instantly on startup!
                val drawableDir = File(context.applicationInfo.dataDir, "files")
                // Search for our generated hero image in files or drawables and write a copy
                val onboardingImage = File(sampleImages, "Onboarding_Visual.jpg")
                
                // We'll write some raw gradient bytes to a mock image inside Media_Gallery just in case,
                // but let's copy the launcher foreground asset or the hero image if we can read it.
                // Since drawables are compiled, we can write a simple mock file or copy resource stream
                val resId = context.resources.getIdentifier("ic_launcher_foreground_asset_1781900142962", "drawable", context.packageName)
                if (resId != 0) {
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(onboardingImage).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    // Save brief illustrative placeholder
                    onboardingImage.writeText("Illustrative Image stream payload.")
                }
                
                // Add default Bookmark
                viewModelScope.launch(Dispatchers.IO) {
                    repository.addBookmark(sampleImages.absolutePath, sampleImages.name, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            val childs = file.listFiles() ?: return 0L
            for (c in childs) {
                size += if (c.isDirectory) getFolderSize(c) else c.length()
            }
        } else {
            size = file.length()
        }
        return size
    }


    /**
     * STORAGE ANALYZER PRO Scanner
     */
    fun runAdvancedStorageAnalysis() {
        if (isAnalyzingStorage.value) return
        isAnalyzingStorage.value = true
        showStorageAnalyzer.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = _currentDirectory.value
                val stat = android.os.StatFs(root.absolutePath)
                _storageDeviceTotal.value = stat.totalBytes
                _storageDeviceFree.value = stat.availableBytes

                val allFiles = mutableListOf<File>()
                val allFolders = mutableListOf<File>()
                findFilesAndFoldersRecursive(root, allFiles, allFolders, 5000)

                // 1. Largest Files
                val topFiles = allFiles
                    .sortedByDescending { it.length() }
                    .take(15)
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isFolder = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = FileOperations.getExtension(file),
                            mimeType = FileOperations.getMimeType(file)
                        )
                    }
                _largestFiles.value = topFiles

                // 2. Largest Folders list
                val topFolders = allFolders
                    .map { folder ->
                        val size = getFolderSize(folder)
                        val count = folder.listFiles()?.size ?: 0
                        FolderSizeItem(folder.name, folder.absolutePath, size, count)
                    }
                    .sortedByDescending { it.size }
                    .take(12)
                _largestFolders.value = topFolders

                // 3. Duplicate Files (grouped by name + size)
                val dupes = allFiles
                    .groupBy { it.name + "_" + it.length() }
                    .filter { it.value.size > 1 }
                    .values
                    .flatten()
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isFolder = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = FileOperations.getExtension(file),
                            mimeType = FileOperations.getMimeType(file)
                        )
                    }
                _duplicateFiles.value = dupes

                // 4. Empty Files (length == 0 bytes)
                val empties = allFiles
                    .filter { it.length() == 0L }
                    .take(30)
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isFolder = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = FileOperations.getExtension(file),
                            mimeType = FileOperations.getMimeType(file)
                        )
                    }
                _emptyFiles.value = empties

                // 5. Unused Files (oldest lastModified date, limit 20)
                val unused = allFiles
                    .sortedBy { it.lastModified() }
                    .take(20)
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isFolder = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            extension = FileOperations.getExtension(file),
                            mimeType = FileOperations.getMimeType(file)
                        )
                    }
                _unusedFiles.value = unused

                // 6. Category allocation (size map)
                var imageBytes = 0L
                var videoBytes = 0L
                var audioBytes = 0L
                var docBytes = 0L
                var zipBytes = 0L
                var apkBytes = 0L
                var otherBytes = 0L

                for (f in allFiles) {
                    val ext = f.extension.lowercase()
                    val len = f.length()
                    when {
                        ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> imageBytes += len
                        ext in listOf("mp4", "mkv", "webm", "avi", "3gp") -> videoBytes += len
                        ext in listOf("mp3", "wav", "ogg", "aac", "m4a", "flac") -> audioBytes += len
                        ext in listOf("zip", "rar", "7z", "tar", "gz") -> zipBytes += len
                        ext in listOf("txt", "pdf", "docx", "doc", "xlsx", "xls", "pptx", "csv", "xml", "json", "html") -> docBytes += len
                        ext in listOf("apk", "xapk", "apks", "apkm") -> apkBytes += len
                        else -> otherBytes += len
                    }
                }
                _categoryAllocation.value = mapOf(
                    "Images" to imageBytes,
                    "Videos" to videoBytes,
                    "Audio" to audioBytes,
                    "Archives (ZIP)" to zipBytes,
                    "Documents" to docBytes,
                    "APK Packages" to apkBytes,
                    "Others" to otherBytes
                )

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isAnalyzingStorage.value = false
            }
        }
    }

    private fun findFilesAndFoldersRecursive(dir: File, files: MutableList<File>, folders: MutableList<File>, limit: Int) {
        if (files.size + folders.size >= limit) return
        try {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (files.size + folders.size >= limit) return
                if (f.isDirectory) {
                    folders.add(f)
                    findFilesAndFoldersRecursive(f, files, folders, limit)
                } else {
                    files.add(f)
                }
            }
        } catch (e: Exception) {
            // Access denied
        }
    }

    /**
     * RECYCLE BIN Operations
     */
    fun restoreTrashItem(item: com.filezone.app.data.RecycleBinEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val trashFile = File(item.trashPath)
            val destFile = File(item.originalPath)
            if (trashFile.exists()) {
                val parent = destFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                var restoreSuccess = trashFile.renameTo(destFile)
                if (!restoreSuccess) {
                    try {
                        FileOperations.move(trashFile, destFile)
                        restoreSuccess = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (restoreSuccess) {
                    refreshMediaStore(trashFile.absolutePath, destFile.absolutePath)
                    repository.removeFromRecycleBin(item.trashPath)
                    showToast("Restored: ${item.name}")
                } else {
                    // Fallback to copy + delete if rename/move fails
                    try {
                        FileOperations.copy(trashFile, destFile)
                        FileOperations.delete(trashFile)
                        refreshMediaStore(trashFile.absolutePath, destFile.absolutePath)
                        repository.removeFromRecycleBin(item.trashPath)
                        showToast("Restored: ${item.name}")
                    } catch (copyErr: Exception) {
                        copyErr.printStackTrace()
                        showToast("Failed to restore: ${item.name}")
                    }
                }
            } else {
                repository.removeFromRecycleBin(item.trashPath)
            }
            loadCurrentDirectoryFiles()
        }
    }

    fun deleteTrashItemPermanently(item: com.filezone.app.data.RecycleBinEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val trashFile = File(item.trashPath)
            if (trashFile.exists()) {
                FileOperations.delete(trashFile)
                refreshMediaStore(trashFile.absolutePath, null)
            }
            repository.removeFromRecycleBin(item.trashPath)
            showToast("Permanently deleted: ${item.name}")
            loadCurrentDirectoryFiles()
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = trashDir.listFiles()
            if (list != null) {
                for (f in list) {
                    FileOperations.delete(f)
                }
            }
            repository.clearRecycleBin()
            showToast("Recycle Pin Cleared")
            loadCurrentDirectoryFiles()
        }
    }


    fun autoCleanRecycleBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = trashDir.listFiles() ?: return@launch
            // Clean elements older than 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            var deletedCount = 0
            
            val trashedDB = db.dao().getAllRecycleBin().firstOrNull() ?: emptyList()
            trashedDB.forEach { item ->
                if (item.dateDeleted < sevenDaysAgo) {
                    val file = File(item.trashPath)
                    if (file.exists()) {
                        FileOperations.delete(file)
                    }
                    repository.removeFromRecycleBin(item.trashPath)
                    deletedCount++
                }
            }
            if (deletedCount > 0) {
                showToast("Auto-cleaned $deletedCount items older than 7 days from Recycle Bin")
            }
        }
    }

    /**
     * APK MANAGER PRO Helpers
     */
    fun loadInstalledApps() {
        if (isScanningApps.value) return
        isScanningApps.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(0)
                val apps = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val packageName = pkg.packageName ?: ""
                    val versionName = pkg.versionName ?: "1.0"
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkg.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pkg.versionCode
                    }
                    val sourceDir = appInfo.sourceDir ?: ""
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (sourceDir.isNotEmpty()) {
                        AppItem(name, packageName, versionName, versionCode, sourceDir, isSystem)
                    } else {
                        null
                    }
                }.sortedBy { it.name.lowercase() }
                
                _installedApps.value = apps
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanningApps.value = false
            }
        }
    }

    fun extractAndBackupApk(app: AppItem, isBackup: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = File(app.sourceDir)
                if (src.exists()) {
                    val destDir = if (isBackup) {
                        File(sandboxRoot, "Backups/Apps")
                    } else {
                        File(downloadsRoot, "Extracted_APKs").apply { if (!exists()) mkdirs() }
                    }
                    if (!destDir.exists()) destDir.mkdirs()
                    
                    val safeName = app.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
                    val destFile = File(destDir, "${safeName}_v${app.versionName}.apk")
                    FileOperations.copy(src, destFile)
                    showToast(if (isBackup) "App Backup Saved: ${destFile.name}" else "APK Extracted to: ${destFile.name}")
                    loadCurrentDirectoryFiles()
                } else {
                    showToast("Source APK not found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Extraction failed: ${e.localizedMessage}")
            }
        }
    }

    fun shareAppApk(app: AppItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = File(app.sourceDir)
                if (src.exists()) {
                    val cacheSharedDir = File(context.cacheDir, "shared_apks").apply { if (!exists()) mkdirs() }
                    val sharedFile = File(cacheSharedDir, "${app.name.replace(" ", "_")}_v${app.versionName}.apk")
                    if (!sharedFile.exists()) {
                        FileOperations.copy(src, sharedFile)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", sharedFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.android.package-archive"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${app.name} APK").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to share app APK")
            }
        }
    }



    /**
     * PACKAGE PREVIEW & INSTALLER (APK/XAPK/APKS/APKM Support)
     */
    fun parsePackageFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = file.extension.lowercase()
                if (ext == "apk") {
                    val pm = context.packageManager
                    val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
                    val appInfo = info?.applicationInfo
                    if (info != null && appInfo != null) {
                        appInfo.sourceDir = file.absolutePath
                        appInfo.publicSourceDir = file.absolutePath
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val packageName = info.packageName
                        val versionName = info.versionName ?: "1.0"
                        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            info.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            info.versionCode
                        }
                        
                        var iconDrawable: android.graphics.drawable.Drawable? = null
                        try {
                            iconDrawable = pm.getApplicationIcon(appInfo)
                        } catch (e: Exception) {}

                        activePackageFile.value = file
                        activePackagePreview.value = PackagePreviewInfo(
                            label = label,
                            packageName = packageName,
                            versionName = versionName,
                            versionCode = versionCode,
                            fileType = "APK",
                            innerApkCount = 1,
                            icon = iconDrawable
                        )
                    } else {
                        showToast("Failed parsing APK bundle info")
                    }
                } else if (ext in listOf("xapk", "apks", "apkm")) {
                    val cacheTemp = File(context.cacheDir, "pkg_cache").apply { if (exists()) deleteRecursively(); mkdirs() }
                    try {
                        FileOperations.unzip(file, cacheTemp)
                        val apkFiles = cacheTemp.listFiles()?.filter { it.extension.lowercase() == "apk" } ?: emptyList()
                        val baseApk = apkFiles.find { it.name.lowercase().contains("base") } ?: apkFiles.firstOrNull()
                        if (baseApk != null) {
                            val pm = context.packageManager
                            val info = pm.getPackageArchiveInfo(baseApk.absolutePath, 0)
                            val appInfo = info?.applicationInfo
                            if (info != null && appInfo != null) {
                                appInfo.sourceDir = baseApk.absolutePath
                                appInfo.publicSourceDir = baseApk.absolutePath
                                val label = pm.getApplicationLabel(appInfo).toString()
                                val packageName = info.packageName
                                val versionName = info.versionName ?: "1.0"
                                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    info.longVersionCode.toInt()
                                } else {
                                    @Suppress("DEPRECATION")
                                    info.versionCode
                                }

                                activePackageFile.value = file
                                activePackagePreview.value = PackagePreviewInfo(
                                    label = "$label (Split)",
                                    packageName = packageName,
                                    versionName = versionName,
                                    versionCode = versionCode,
                                    fileType = ext.uppercase(),
                                    innerApkCount = apkFiles.size
                                )
                            } else {
                                showToast("Failed to parse split base APK")
                            }
                        } else {
                            showToast("No internal APK binaries inside package archive")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showToast("Splits read failed: ${e.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Package parser error: ${e.localizedMessage}")
            }
        }
    }

    fun installActivePackage() {
        val file = activePackageFile.value ?: return
        val ext = file.extension.lowercase()
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (ext == "apk") {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    showToast("Starting installation...")
                } else if (ext in listOf("xapk", "apks", "apkm")) {
                    withContext(Dispatchers.IO) {
                        installSplitApksOffline(file)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Launch installer failed: ${e.localizedMessage}")
            }
        }
    }

    private fun installSplitApksOffline(file: File) {
        try {
            val cacheTemp = File(context.cacheDir, "pkg_install_cache").apply { if (exists()) deleteRecursively(); mkdirs() }
            FileOperations.unzip(file, cacheTemp)
            val apkFiles = cacheTemp.listFiles()?.filter { it.extension.lowercase() == "apk" } ?: emptyList()
            if (apkFiles.isEmpty()) {
                showToast("No installer APK binaries found.")
                return
            }

            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)

            apkFiles.forEach { apk ->
                val size = apk.length()
                val outStream = session.openWrite(apk.name, 0, size)
                apk.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                    session.fsync(outStream)
                }
                outStream.close()
            }

            val intent = Intent(context, com.filezone.app.MainActivity::class.java).apply {
                action = "com.filezone.INSTALL_COMPLETE"
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
            session.close()
            showToast("Native splits installer scheduled successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Integration installer error: ${e.localizedMessage}")
        }
    }

    fun showToast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshMediaStore(vararg paths: String?) {
        val validPaths = paths.filterNotNull().toTypedArray()
        if (validPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, validPaths, null, null)
        }
    }
}
