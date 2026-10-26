package com.combo.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.data.PhotoEntity
import com.combo.domain.BitmapSource
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Разрешение битмапа для полноэкранного просмотра — политика экрана (с запасом на зум
 * до 8x), а не кэша: `PhotoAssets` только исполняет запрос и помнит, что уже загружено.
 */
private const val VIEWER_PX = 2048

class PhotoViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
) : ViewModel() {

    data class State(
        val photos: List<PhotoEntity> = emptyList(),
        val images: Map<String, ImageBitmap> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Битмапы и их учёт — в общем кэше; окно `index±1` и 2048 остаются политикой экрана. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)

    init {
        viewModelScope.launch {
            photoRepo.all().collect { list ->
                _state.update { it.copy(photos = list) }
            }
        }
        // `State.images` — зеркало `assets.bitmaps`: источник правды один, а поле
        // состояния остаётся, потому что его читает пагер. Синхронизация ровно одна.
        viewModelScope.launch {
            assets.bitmaps.collect { imgs -> _state.update { it.copy(images = imgs) } }
        }
    }

    /**
     * Загружает битмапы для страницы [index] и её ближайших соседей и выгружает те, что
     * вышли за это окно. Грузить всю библиотеку (по 2048 px) при открытии просмотра нельзя:
     * это дорого по времени и памяти, а `LruBitmapSource` вытеснил бы большую часть
     * результата раньше, чем пользователь до неё долистает.
     */
    fun ensureLoaded(index: Int) {
        val photos = _state.value.photos
        if (photos.isEmpty()) return
        val keepIds = ((index - 1)..(index + 1)).mapNotNull { photos.getOrNull(it)?.id }.toSet()

        assets.evictExcept(keepIds)

        viewModelScope.launch {
            // Уже загруженные в окне повторно не декодируются: правило пропуска по
            // размеру живёт в PhotoAssets, здесь остаётся только сам обход окна.
            for (id in keepIds) assets.ensureBitmap(id, VIEWER_PX)
        }
    }
}
