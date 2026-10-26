package com.combo.domain

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.image.ImageSource
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import kotlinx.coroutines.flow.Flow
import okio.Path

data class ImportResult(val added: Int, val duplicates: Int, val failed: Int)

enum class DeleteResult { DELETED, BLOCKED_IF_USED_IN_COMBO }

interface PhotoRepository {
    /** Дедуп по SHA-256, avgColor один раз при импорте, превью-миниатюра на диске. */
    suspend fun importPhotos(sources: List<ImageSource>): ImportResult

    fun all(): Flow<List<PhotoEntity>>

    suspend fun get(id: String): PhotoEntity?

    /** Удаление фото, используемого в комбо — BLOCKED_IF_USED_IN_COMBO. */
    suspend fun delete(id: String): DeleteResult

    /** Миниатюра для списков (путь строит `PhotoStorage`, он же владеет каталогами). */
    fun thumbPath(photoId: String): Path?
}

interface ComboRepository {
    fun all(): Flow<List<ComboWithItems>>

    /** Сортировка items по zIndex здесь. */
    suspend fun get(id: String): ComboWithItems?

    /** ОДНА транзакция на весь конфиг. */
    suspend fun saveCombo(combo: ComboEntity, items: List<ComboItemEntity>)

    suspend fun delete(id: String)
}

interface BitmapSource {
    /** Сэмпленный декод + LRU по (photoId, sampleSize); возвращает кэш, если разрешения достаточно. */
    suspend fun get(photoId: String, requiredPx: Int): ImageBitmap
}
