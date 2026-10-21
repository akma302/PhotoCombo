package com.combo.domain

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import kotlinx.coroutines.flow.Flow

data class ImportResult(val added: Int, val duplicates: Int, val failed: Int)

enum class DeleteResult { DELETED, BLOCKED_IF_USED_IN_COMBO }

interface PhotoRepository {
    /** Дедуп по SHA-256, avgColor один раз при импорте, превью-миниатюра на диске. */
    suspend fun importPhotos(uris: List<Uri>): ImportResult

    fun all(): Flow<List<PhotoEntity>>

    suspend fun get(id: String): PhotoEntity?

    /** Удаление фото, используемого в комбо — BLOCKED_IF_USED_IN_COMBO. */
    suspend fun delete(id: String): DeleteResult

    /** Миниатюра для списков (путь строит `PhotoStorage`, он же владеет каталогами). */
    fun thumbFile(photoId: String): java.io.File?
}

interface ComboRepository {
    fun all(): Flow<List<ComboWithItems>>

    /** Сортировка items/contents здесь. */
    suspend fun get(id: String): ComboWithItems?

    /** ОДНА транзакция на весь конфиг. */
    suspend fun saveCombo(
        combo: ComboEntity,
        items: List<ComboItemEntity>,
        contents: List<ComboItemContentEntity>,
    )

    suspend fun delete(id: String)
}

interface BitmapSource {
    /** Сэмпленный декод + LRU по (photoId, sampleSize); возвращает кэш, если разрешения достаточно. */
    suspend fun get(photoId: String, requiredPx: Int): ImageBitmap
}
