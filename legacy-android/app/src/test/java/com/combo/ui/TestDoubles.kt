package com.combo.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.combo.core.background.BackgroundSpec
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
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
import java.io.File

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

fun comboItemEntity(
    id: String,
    comboId: String,
    zIndex: Int = 0,
    framingMode: String = "FREE",
) = ComboItemEntity(
    id = id,
    comboId = comboId,
    zIndex = zIndex,
    centerX = 800f,
    centerY = 450f,
    width = 1600f,
    height = 900f,
    switchMode = "MANUAL",
    manualTransition = "NONE",
    autoTransition = "NONE",
    framingMode = framingMode,
)

fun comboContentEntity(id: String, itemId: String, photoId: String) = ComboItemContentEntity(
    id = id,
    itemId = itemId,
    photoId = photoId,
    orderIndex = 0,
    contentScale = 1f,
    panX = 0f,
    panY = 0f,
)

class FakePhotoRepository(initial: List<PhotoEntity> = emptyList()) : PhotoRepository {
    val state = MutableStateFlow(initial)

    /** Доставка нового списка активным коллекторам — как Room отдаёт результат запроса. */
    fun push(photos: List<PhotoEntity>) {
        state.value = photos
    }

    override fun all(): Flow<List<PhotoEntity>> = state

    override suspend fun get(id: String): PhotoEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun importPhotos(uris: List<Uri>): ImportResult = ImportResult(0, uris.size, 0)

    override suspend fun delete(id: String): DeleteResult {
        state.value = state.value.filterNot { it.id == id }
        return DeleteResult.DELETED
    }

    override fun thumbFile(photoId: String): File? = null
}

class FakeComboRepository(initial: List<ComboWithItems> = emptyList()) : ComboRepository {
    val state = MutableStateFlow(initial)

    var savedCombos: List<Triple<ComboEntity, List<ComboItemEntity>, List<ComboItemContentEntity>>> =
        emptyList()
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

    override suspend fun saveCombo(
        combo: ComboEntity,
        items: List<ComboItemEntity>,
        contents: List<ComboItemContentEntity>,
    ) {
        saveGate?.invoke()
        failWith?.let { throw it }
        savedCombos = savedCombos + Triple(combo, items, contents)
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.combo.id == id }
    }
}

/** Отдаёт валидный (пустой по пикселям) ImageBitmap — рендер в тесте не важен. */
class FakeBitmapSource : BitmapSource {
    override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * То же, но БЕЗ android.graphics — для чистых JVM-тестов ViewModel, где Bitmap.createBitmap
 * недоступен. Рендерить такой битмап нельзя, ViewModel только хранит его.
 */
class StubBitmapSource : BitmapSource {
    override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap =
        StubImageBitmap(8, 8)
}

private class StubImageBitmap(
    override val width: Int,
    override val height: Int,
) : ImageBitmap {
    override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888

    override val colorSpace: ColorSpace get() = ColorSpaces.Srgb

    override val hasAlpha: Boolean get() = true

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}
