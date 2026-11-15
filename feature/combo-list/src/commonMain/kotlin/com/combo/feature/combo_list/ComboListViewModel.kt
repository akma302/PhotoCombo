package com.combo.feature.combo_list

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.domain.GallerySettingsRepository
import com.combo.domain.GalleryKind
import com.combo.ui.common.GalleryScaleState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
private const val PREFETCH_CARDS = 2

class ComboListViewModel(
    private val comboRepo: ComboRepository,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
    settingsRepository: GallerySettingsRepository,
    private val exportPng: suspend (String) -> String?,
) : ViewModel() {
    val galleryScale = GalleryScaleState(viewModelScope, settingsRepository, GalleryKind.Combos)

    data class State(
        val combos: List<ComboWithItems> = emptyList(),
        val photos: Map<String, PhotoEntity> = emptyMap(),
        val images: Map<String, ImageBitmap> = emptyMap(),
        val exportingComboId: String? = null,
        val exportMessage: String? = null,
    ) {
        fun framesFor(combo: ComboWithItems): List<ItemFrame> =
            combo.items.sortedBy { it.zIndex }.mapNotNull { item ->
                val image = images[item.photoId] ?: return@mapNotNull null
                val photo = photos[item.photoId] ?: return@mapNotNull null
                buildItemFrame(
                    border = item.border(),
                    mode = item.framingModeEnum,
                    content = item.frameContent(image, photo),
                )
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Метаданные и битмапы — в общем кэше; [PREVIEW_PX] остаётся политикой превью. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)
    private var visibleCombos = emptySet<String>()
    private var requestedPhotos = emptySet<String>()
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            comboRepo.all().collect { list ->
                _state.update { it.copy(combos = list) }
                updatePreviewWindow()
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

    fun setVisibleCombos(ids: Set<String>) {
        visibleCombos = ids
        updatePreviewWindow()
    }

    private fun updatePreviewWindow() {
        val combos = _state.value.combos
        val visibleIndices = combos.indices.filter { combos[it].combo.id in visibleCombos }
        val nearbyIndices = visibleIndices.flatMap { index ->
            (index - PREFETCH_CARDS).coerceAtLeast(0)..(index + PREFETCH_CARDS).coerceAtMost(combos.lastIndex)
        }.toSet() - visibleIndices.toSet()
        val photoIds = (visibleIndices + nearbyIndices).flatMap { index ->
            combos[index].items.map { it.photoId }
        }.toSet()
        if (photoIds == requestedPhotos) return

        requestedPhotos = photoIds
        previewJob?.cancel()
        assets.evictExcept(photoIds)
        assets.evictMetadataExcept(photoIds)
        previewJob = viewModelScope.launch {
            assets.ensureMeta(photoIds)
            for (id in photoIds) assets.ensureBitmap(id, PREVIEW_PX)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { comboRepo.delete(id) }
    }

    fun export(id: String) {
        if (_state.value.exportingComboId != null) return
        _state.update { it.copy(exportingComboId = id, exportMessage = null) }
        viewModelScope.launch {
            var message: String? = null
            try {
                message = exportPng(id)?.let { "Exported $it" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message?.let { "PNG export failed. $it" } ?: "PNG export failed."
            } finally {
                _state.update { it.copy(exportingComboId = null, exportMessage = message) }
            }
        }
    }
}
