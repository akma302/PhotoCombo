package com.combo.feature.combo_list

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.core.render.ItemFrame
import com.combo.core.render.buildItemFrame
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.data.border
import com.combo.data.frameContent
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Разрешение битмапа для превью — политика экрана (thumbnails), а не кэша: 800 пикселей
 * с запасом хватает ячейке списка, а декодировать всю библиотеку в полном размере
 * список не должен.
 */
private const val PREVIEW_PX = 800

class ComboListViewModel(
    private val comboRepo: ComboRepository,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
) : ViewModel() {

    data class State(
        val combos: List<ComboWithItems> = emptyList(),
        val photos: Map<String, PhotoEntity> = emptyMap(),
        val images: Map<String, ImageBitmap> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Метаданные и битмапы — в общем кэше; [PREVIEW_PX] остаётся политикой превью. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)

    init {
        viewModelScope.launch {
            comboRepo.all().collect { list ->
                _state.update { it.copy(combos = list) }
            }
        }
        // `State.photos`/`State.images` — зеркала `assets`. Источник правды один
        // (PhotoAssets), а поля состояния остаются: от них зависят ключи `remember` в
        // превью, фон и `framesFor`. Синхронизация ровно одна и ровно здесь.
        viewModelScope.launch {
            assets.photos.collect { meta -> _state.update { it.copy(photos = meta) } }
        }
        viewModelScope.launch {
            assets.bitmaps.collect { imgs -> _state.update { it.copy(images = imgs) } }
        }
    }

    /**
     * Подготовка превью одного комбо: метаданные (`avgColor`, нужны для фона) и битмапы
     * фото всех рамок. Битмап декодируется в [PREVIEW_PX], а не в полном размере: список не
     * должен декодировать всю библиотеку.
     */
    fun ensureAssetsFor(combo: ComboWithItems) {
        viewModelScope.launch {
            val photoIds = combo.items.map { it.photoId }.distinct()
            assets.ensureMeta(photoIds)
            for (id in photoIds) assets.ensureBitmap(id, PREVIEW_PX)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { comboRepo.delete(id) }
    }

    fun framesFor(combo: ComboWithItems): List<ItemFrame> {
        val s = _state.value
        return combo.items.sortedBy { it.zIndex }.mapNotNull { item ->
            val img = s.images[item.photoId] ?: return@mapNotNull null
            val photo = s.photos[item.photoId] ?: return@mapNotNull null
            buildItemFrame(
                border = item.border(),
                mode = item.framingModeEnum,
                content = item.frameContent(img, photo),
            )
        }
    }
}
