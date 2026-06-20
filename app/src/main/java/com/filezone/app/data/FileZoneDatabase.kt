package com.filezone.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isFolder: Boolean
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val path: String,
    val name: String,
    val timestamp: Long,
    val size: Long,
    val mimeType: String
)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey val trashPath: String,
    val originalPath: String,
    val name: String,
    val size: Long,
    val isFolder: Boolean,
    val dateDeleted: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val dateAdded: Long
)

@Dao
interface FileZoneDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY name ASC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    suspend fun isBookmarked(path: String): Boolean

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY dateAdded DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    // Recents
    @Query("SELECT * FROM recents ORDER BY timestamp DESC LIMIT 50")
    fun getRecentFiles(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentEntity)

    @Query("DELETE FROM recents WHERE path = :path")
    suspend fun deleteRecentByPath(path: String)

    @Query("DELETE FROM recents")
    suspend fun clearAllRecents()

    // Recycle Bin
    @Query("SELECT * FROM recycle_bin ORDER BY dateDeleted DESC")
    fun getAllRecycleBin(): Flow<List<RecycleBinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleBin(item: RecycleBinEntity)

    @Delete
    suspend fun deleteRecycleBin(item: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE trashPath = :trashPath")
    suspend fun deleteRecycleBinByPath(trashPath: String)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearRecycleBin()
}

@Database(entities = [BookmarkEntity::class, RecentEntity::class, RecycleBinEntity::class, FavoriteEntity::class], version = 3, exportSchema = false)
abstract class FileZoneDatabase : RoomDatabase() {
    abstract fun dao(): FileZoneDao

    companion object {
        @Volatile
        private var INSTANCE: FileZoneDatabase? = null

        fun getDatabase(context: Context): FileZoneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FileZoneDatabase::class.java,
                    "file_zone_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class FileZoneRepository(private val dao: FileZoneDao) {
    val allBookmarks: Flow<List<BookmarkEntity>> = dao.getAllBookmarks()
    val recentFiles: Flow<List<RecentEntity>> = dao.getRecentFiles()
    val recycleBinFiles: Flow<List<RecycleBinEntity>> = dao.getAllRecycleBin()
    val allFavorites: Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    suspend fun addBookmark(path: String, name: String, isFolder: Boolean) {
        dao.insertBookmark(BookmarkEntity(path, name, isFolder))
    }

    suspend fun removeBookmark(path: String) {
        dao.deleteBookmark(BookmarkEntity(path, "", false))
    }

    suspend fun isBookmarked(path: String): Boolean {
        return dao.isBookmarked(path)
    }

    suspend fun addFavorite(path: String, name: String, isFolder: Boolean, size: Long) {
        dao.insertFavorite(FavoriteEntity(path, name, isFolder, size, System.currentTimeMillis()))
    }

    suspend fun removeFavorite(path: String) {
        dao.deleteFavorite(FavoriteEntity(path, "", false, 0, 0))
    }

    suspend fun isFavorite(path: String): Boolean {
        return dao.isFavorite(path)
    }

    suspend fun addRecent(path: String, name: String, size: Long, mimeType: String) {
        dao.insertRecent(RecentEntity(path, name, System.currentTimeMillis(), size, mimeType))
    }

    suspend fun removeRecent(path: String) {
        dao.deleteRecentByPath(path)
    }

    suspend fun clearRecents() {
        dao.clearAllRecents()
    }

    // Recycle Bin Helpers
    suspend fun addToRecycleBin(trashPath: String, originalPath: String, name: String, size: Long, isFolder: Boolean) {
        dao.insertRecycleBin(RecycleBinEntity(trashPath, originalPath, name, size, isFolder, System.currentTimeMillis()))
    }

    suspend fun removeFromRecycleBin(trashPath: String) {
        dao.deleteRecycleBinByPath(trashPath)
    }

    suspend fun clearRecycleBin() {
        dao.clearRecycleBin()
    }
}
