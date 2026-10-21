package com.combo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY importedAt DESC")
    fun all(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE sha256 = :hash LIMIT 1")
    suspend fun findByHash(hash: String): PhotoEntity?

    @Insert
    suspend fun insert(p: PhotoEntity)

    @Delete
    suspend fun delete(p: PhotoEntity)
}

@Dao
interface ComboDao {
    @Transaction
    @Query("SELECT * FROM combos ORDER BY updatedAt DESC")
    fun all(): Flow<List<ComboWithItems>>

    @Transaction
    @Query("SELECT * FROM combos WHERE id = :id")
    suspend fun get(id: String): ComboWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(combo: ComboEntity)

    @Query("DELETE FROM combo_items WHERE comboId = :comboId")
    suspend fun deleteItems(comboId: String)

    @Insert
    suspend fun insertItems(items: List<ComboItemEntity>)

    @Query("DELETE FROM combos WHERE id = :comboId")
    suspend fun delete(comboId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM combo_items WHERE photoId = :photoId)")
    suspend fun isPhotoUsed(photoId: String): Boolean
}
