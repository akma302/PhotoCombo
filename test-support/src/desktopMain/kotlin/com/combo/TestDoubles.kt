package com.combo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.platform.Logger
import com.combo.data.ComboDao
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okio.Path

/** DAO фото в памяти: строки в порядке вставки, сбои записи — по флагу теста. */
class FakePhotoDao : PhotoDao {
    val rows = LinkedHashMap<String, PhotoEntity>()

    /** Следующий insert упадёт: имитация сбоя записи после успешного копирования. */
    var failNextInsert = false

    override fun all(): Flow<List<PhotoEntity>> = flowOf(rows.values.toList())

    override suspend fun get(id: String): PhotoEntity? = rows[id]

    override suspend fun findByHash(hash: String): PhotoEntity? =
        rows.values.firstOrNull { it.sha256 == hash }

    override suspend fun insert(p: PhotoEntity) {
        if (failNextInsert) {
            failNextInsert = false
            throw IllegalStateException("insert failed")
        }
        if (rows.values.any { it.sha256 == p.sha256 }) throw IllegalStateException("duplicate sha256")
        rows[p.id] = p
    }

    override suspend fun delete(p: PhotoEntity) {
        rows.remove(p.id)
    }
}

/** DAO комбо: тестам репозитория фото нужно только «используется ли фото». */
class FakeComboDao : ComboDao {
    var usedPhotoIds: Set<String> = emptySet()

    override fun all(): Flow<List<ComboWithItems>> = flowOf(emptyList())

    override suspend fun get(id: String): ComboWithItems? = null

    override suspend fun upsert(combo: ComboEntity) = error("not needed for this test")

    override suspend fun deleteItems(comboId: String) = error("not needed for this test")

    override suspend fun insertItems(items: List<ComboItemEntity>) = error("not needed for this test")

    override suspend fun delete(comboId: String) = error("not needed for this test")

    override suspend fun isPhotoUsed(photoId: String): Boolean = photoId in usedPhotoIds
}

/** Логгер, запоминающий предупреждения: тесты проверяют, что сбой не проглочен молча. */
class FakeLogger : Logger {
    val warnings = mutableListOf<String>()

    override fun warn(tag: String, message: String, error: Throwable?) {
        warnings += "$tag: $message"
    }
}

/** Кодек для тестов кэша: считает вызовы и запрошенные сэмплы, кадры задаёт тест. */
class CountingCodec : ImageCodec {
    var decodeCalls = 0
    val samples = mutableListOf<Int>()
    var result: (Path) -> ImageBitmap? = { null }

    override fun readBounds(path: Path): IntSize? = error("not needed for this test")

    override fun readOrientation(path: Path): ExifOrientation = error("not needed for this test")

    override fun decode(path: Path, sample: Int): ImageBitmap? {
        decodeCalls++
        samples += sample
        return result(path)
    }

    override fun encode(path: Path, image: ImageBitmap, format: ImageFormat, quality: Int): Boolean =
        error("not needed for this test")
}
