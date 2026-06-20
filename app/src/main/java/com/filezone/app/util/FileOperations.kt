package com.filezone.app.util

import android.os.Build
import android.webkit.MimeTypeMap
import java.io.*
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileOperations {

    /**
     * Formats bytes count to a human-readable size (e.g. 12.34 MB)
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Rename a file or directory
     */
    fun rename(source: File, destination: File): Boolean {
        return try {
            if (source.exists() && !destination.exists()) {
                source.renameTo(destination)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Copy file or folder recursively
     */
    @Throws(IOException::class)
    fun copy(source: File, destination: File) {
        try {
            if (source.isDirectory) {
                if (!destination.exists() && !destination.mkdirs()) {
                    throw IOException("Failed to create directory: ${destination.absolutePath}")
                }
                val children = source.list() ?: return
                for (child in children) {
                    copy(File(source, child), File(destination, child))
                }
            } else {
                val parentFile = destination.parentFile
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }
                FileInputStream(source).use { inStream ->
                    FileOutputStream(destination).use { outStream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: Exception) {
             throw IOException("Error copying file from ${source.absolutePath} to ${destination.absolutePath}", e)
        }
    }

    /**
     * Move file or folder recursively
     */
    @Throws(IOException::class)
    fun move(source: File, destination: File) {
        try {
            val renamed = source.renameTo(destination)
            android.util.Log.d("FileOperations", "renameTo result: $renamed")
            if (renamed) {
                val sourceExists = source.exists()
                val destExists = destination.exists()
                android.util.Log.d("FileOperations", "renameTo validation - sourceExists: $sourceExists, destExists: $destExists")
                if (!destExists || sourceExists) {
                     android.util.Log.e("FileOperations", "renameTo failed post-check: sourceExists=$sourceExists, destExists=$destExists")
                     throw IOException("Move failed: source still exists or dest not found")
                }
                android.util.Log.d("FileOperations", "Move (rename) successful: ${source.absolutePath} -> ${destination.absolutePath}")
                return
            }
            android.util.Log.d("FileOperations", "renameTo returned false, trying copy + delete...")
            copy(source, destination)
            val copiedOk = destination.exists()
            android.util.Log.d("FileOperations", "copy completed - destinationExists: $copiedOk")
            
            val deletedOk = delete(source)
            val sourceStillExists = source.exists()
            android.util.Log.d("FileOperations", "delete completed - deletedOk: $deletedOk, sourceStillExists: $sourceStillExists")
            
            if (sourceStillExists || !copiedOk) {
                 android.util.Log.e("FileOperations", "copy + delete validation failed: sourceStillExists=$sourceStillExists, destExists=$copiedOk")
                 throw IOException("Move failed (copy/delete): source still exists or dest not found")
            }
            android.util.Log.d("FileOperations", "Move (copy/delete) successful: ${source.absolutePath} -> ${destination.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("FileOperations", "Error moving file from ${source.absolutePath} to ${destination.absolutePath}", e)
            throw IOException("Error moving file from ${source.absolutePath} to ${destination.absolutePath}", e)
        }
    }

    /**
     * Delete file or folder recursively
     */
    fun delete(file: File): Boolean {
        try {
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children != null) {
                    for (child in children) {
                        delete(child)
                    }
                }
            }
            val deleted = file.delete()
            if (deleted && file.exists()) {
                android.util.Log.e("FileOperations", "Delete reported success but file still exists: ${file.absolutePath}")
                return false
            }
            if (!deleted && file.exists()) {
                android.util.Log.e("FileOperations", "Delete failed: ${file.absolutePath}")
                return false
            }
            android.util.Log.d("FileOperations", "Delete successful: ${file.absolutePath}")
            return true
        } catch (e: Exception) {
            android.util.Log.e("FileOperations", "Exception during delete: ${file.absolutePath}", e)
            return false
        }
    }

    /**
     * Zips a list of files or directories into a single ZIP archive.
     */
    @Throws(IOException::class)
    fun zip(sourceFiles: List<File>, zipFile: File) {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (file in sourceFiles) {
                    addToZip(file, file.name, zos)
                }
            }
        } catch (e: Exception) {
            throw IOException("Error creating ZIP archive at ${zipFile.absolutePath}", e)
        }
    }

    @Throws(IOException::class)
    private fun addToZip(file: File, relativePath: String, zos: ZipOutputStream) {
        try {
            if (file.isDirectory) {
                val children = file.listFiles() ?: return
                if (children.isEmpty()) {
                    val entry = ZipEntry(relativePath + "/")
                    zos.putNextEntry(entry)
                    zos.closeEntry()
                } else {
                    for (child in children) {
                        addToZip(child, relativePath + "/" + child.name, zos)
                    }
                }
            } else {
                val buffer = ByteArray(8192)
                BufferedInputStream(FileInputStream(file)).use { bis ->
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    var count: Int
                    while (bis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                    zos.closeEntry()
                }
            }
        } catch (e: Exception) {
            throw IOException("Error adding file to ZIP: ${file.absolutePath}", e)
        }
    }

    /**
     * Unzips a ZIP archive into a destination folder.
     */
    @Throws(IOException::class)
    fun unzip(zipFile: File, destinationDir: File) {
        try {
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IOException("Failed to create destination directory: ${destinationDir.absolutePath}")
            }

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    val newFile = File(destinationDir, entry.name)
                    
                    val canonicalDestinationPath = destinationDir.canonicalPath
                    val canonicalNewFilePath = newFile.canonicalPath
                    if (!canonicalNewFilePath.startsWith(canonicalDestinationPath + File.separator)) {
                        throw SecurityException("Path traversal attempted: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        val parent = newFile.parentFile
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs()
                        }
                        
                        BufferedOutputStream(FileOutputStream(newFile)).use { bos ->
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                bos.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
             throw IOException("Error unzipping archive from ${zipFile.absolutePath}", e)
        }
    }

    /**
     * Gets the extension of a file
     */
    fun getExtension(file: File): String {
        val name = file.name
        val lastIndexOf = name.lastIndexOf(".")
        return if (lastIndexOf == -1) "" else name.substring(lastIndexOf + 1).lowercase()
    }

    /**
     * Guesses Mimetype based on extension
     */
    fun getMimeType(file: File): String {
        val ext = getExtension(file)
        if (ext.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    /**
     * Checks if file is an image
     */
    fun isImage(file: File): Boolean {
        val mime = getMimeType(file)
        return mime.startsWith("image/")
    }

    /**
     * Checks if file is a video
     */
    fun isVideo(file: File): Boolean {
        val mime = getMimeType(file)
        return mime.startsWith("video/")
    }

    /**
     * Checks if file is an audio file
     */
    fun isAudio(file: File): Boolean {
        val mime = getMimeType(file)
        return mime.startsWith("audio/")
    }

    /**
     * Checks if file is a text based/document file that can be previewed
     */
    fun isText(file: File): Boolean {
        val ext = getExtension(file)
        val textExtensions = setOf("txt", "log", "json", "xml", "html", "css", "js", "ts", "md", "kt", "java", "py", "c", "cpp", "php", "sh", "yml", "csv")
        if (textExtensions.contains(ext)) return true
        val mime = getMimeType(file)
        return mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")
    }

    /**
     * Checks if file is a ZIP archive or Android split package
     */
    fun isZip(file: File): Boolean {
        val ext = getExtension(file)
        return ext == "zip" || ext == "xapk" || ext == "apks" || ext == "apkm"
    }

    /**
     * Returns list of zip entries inside a file without extracting
     */
    fun previewZipEntries(zipFile: File): List<String> {
        val entries = mutableListOf<String>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }
}
