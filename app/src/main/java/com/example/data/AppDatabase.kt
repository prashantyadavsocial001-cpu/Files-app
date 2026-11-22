package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val origin: String,
    val filePath: String?,
    val uriString: String?,
    val isPinned: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mounted_folders")
data class MountedFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val mountedAt: Long = System.currentTimeMillis()
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM recent_files ORDER BY isPinned DESC, lastOpenedAt DESC LIMIT 40")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entity: RecentFileEntity)

    @Query("UPDATE recent_files SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("SELECT isPinned FROM recent_files WHERE id = :id")
    suspend fun isPinned(id: String): Boolean?

    @Query("DELETE FROM recent_files WHERE id = :id")
    suspend fun deleteRecent(id: String)

    @Query("SELECT * FROM mounted_folders ORDER BY mountedAt DESC")
    fun observeMountedFolders(): Flow<List<MountedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMountedFolder(folder: MountedFolderEntity)

    @Query("DELETE FROM mounted_folders WHERE treeUri = :treeUri")
    suspend fun removeMountedFolder(treeUri: String)
}

@Database(
    entities = [RecentFileEntity::class, MountedFolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "folioflow_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
