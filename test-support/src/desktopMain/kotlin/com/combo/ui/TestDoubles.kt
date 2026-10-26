package com.combo.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.combo.ImageFixtures
import com.combo.core.background.BackgroundSpec
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.image.ImagePicker
import com.combo.core.image.ImageSource
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.DeleteResult
import com.combo.domain.ImportResult
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path

fun photoEntity(id: String, originalName: String = "$id.jpg") = PhotoEntity(
    id = id,
    fileName = "$id.jpg",
    originalName = originalName,
    mimeType = "image/jpeg",
    width = 1200,
    height = 800,
    sizeBytes = 4096,
    sha256 = "sha-$id",
    avgColor = 0xFF808080.toInt(),
    importedAt = 0L,
)

fun comboEntity(
    id: String,
    name: String = "Combo $id",
    background: BackgroundSpec = BackgroundSpec.Auto(),
) = ComboEntity(
    id = id,
    name = name,
    canvasWidth = 1600,
    canvasHeight = 900,
    background = background,
    createdAt = 0L,
    updatedAt = 0L,
)

/** Рамка комбо с одним фото [photoId]; положение фото — масштаб 1, без сдвига. */
fun comboItemEntity(
    id: String,
    comboId: String,
    zIndex: Int = 0,
    framingMode: String = "FREE",
    photoId: String = "p1",
) = ComboItemEntity(
    id = id,
    comboId = comboId,
    zIndex = zIndex,
    centerX = 800f,
    centerY = 450f,
    width = 1600f,
    height = 900f,
    framingMode = framingMode,
    photoId = photoId,
    contentScale = 1f,
    panX = 0f,
    panY = 0f,
)

/** Репозиторий фото в памяти: тесты ViewModel'ей подсовывают его вместо Room. */
class FakePhotoRepository(initial: List<PhotoEntity> = emptyList()) : PhotoRepository {
    val state = MutableStateFlow(initial)

    /** Доставка нового списка активным коллекторам — как Room отдаёт результат запроса. */
    fun push(photos: List<PhotoEntity>) {
        state.value = photos
    }

    override suspend fun importPhotos(sources: List<ImageSource>): ImportResult =
        ImportResult(added = 0, duplicates = sources.size, failed = 0)

    override fun all(): Flow<List<PhotoEntity>> = state

    override suspend fun get(id: String): PhotoEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun delete(id: String): DeleteResult {
        state.value = state.value.filterNot { it.id == id }
        return DeleteResult.DELETED
    }

    override fun thumbPath(photoId: String): Path? = null
}

class FakeComboRepository(initial: List<ComboWithItems> = emptyList()) : ComboRepository {
    val state = MutableStateFlow(initial)

    var savedCombos: List<Pair<ComboEntity, List<ComboItemEntity>>> = emptyList()
    var failWith: Exception? = null

    /**
     * Если задан, `saveCombo` сначала дождётся его. Нужен, чтобы поймать `saving == true`:
     * обычно окно длится миллисекунды и состояние не наблюдаемо.
     */
    var saveGate: (suspend () -> Unit)? = null

    fun emit(list: List<ComboWithItems>) {
        state.value = list
    }

    override fun all(): Flow<List<ComboWithItems>> = state

    override suspend fun get(id: String): ComboWithItems? = state.value.firstOrNull { it.combo.id == id }

    override suspend fun saveCombo(combo: ComboEntity, items: List<ComboItemEntity>) {
        saveGate?.invoke()
        failWith?.let { throw it }
        savedCombos = savedCombos + (combo to items)
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.combo.id == id }
    }
}

/**
 * Битмап-заглушка для тестов ViewModel: пиксели не важны, ViewModel только хранит
 * ссылку. Настоящий Skia-кадр берётся из [ImageFixtures], поэтому заглушку при
 * необходимости можно и нарисовать.
 */
class StubBitmapSource : BitmapSource {
    override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap =
        ImageFixtures.bitmap(8, 8) {}
}

/** Выбор файлов в UI-тестах: диалог «отменён», импортировать нечего. */
class FakeImagePicker : ImagePicker {
    override suspend fun pickImages(): List<ImageSource> = emptyList()
}

/** Декодер-заглушка для UI-тестов: фейковые репозитории не отдают путей к миниатюрам. */
class StubImageCodec : ImageCodec {
    override fun readBounds(path: Path): IntSize? = null

    override fun readOrientation(path: Path): ExifOrientation = ExifOrientation.NORMAL

    override fun decode(path: Path, sample: Int): ImageBitmap? = null

    override fun encode(path: Path, image: ImageBitmap, format: ImageFormat, quality: Int): Boolean = false
}
