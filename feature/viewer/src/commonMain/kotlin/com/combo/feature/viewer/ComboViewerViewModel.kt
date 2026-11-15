package com.combo.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.data.ComboWithItems
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val VIEWER_PX = 1200
private const val NEIGHBOR_PX = 384

class ComboViewerViewModel(
    comboRepo: ComboRepository,
    photoRepo: PhotoRepository,
    bitmapSource: BitmapSource,
) : ViewModel() {
    private val _combos = MutableStateFlow<List<ComboWithItems>?>(null)
    internal val combos = _combos.asStateFlow()

    private val assets = PhotoAssets(photoRepo, bitmapSource)
    internal val photos = assets.photos
    internal val images = assets.bitmaps

    private var requestedPage: Int? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            comboRepo.all().collect { list ->
                _combos.value = list
                requestedPage?.let(::ensureLoaded)
            }
        }
    }

    internal fun ensureLoaded(index: Int) {
        requestedPage = index
        val list = _combos.value.orEmpty()
        val page = index.coerceIn(0, list.lastIndex.coerceAtLeast(0))
        val current = list.getOrNull(page).photoIds()
        val neighbors = (list.getOrNull(page - 1).photoIds() + list.getOrNull(page + 1).photoIds()) - current
        val keep = current + neighbors

        loadJob?.cancel()
        assets.evictExcept(keep)
        assets.evictMetadataExcept(keep)
        loadJob = viewModelScope.launch {
            assets.ensureMeta(current)
            for (id in current) assets.ensureBitmap(id, VIEWER_PX)
            assets.ensureMeta(neighbors)
            for (id in neighbors) assets.ensureBitmap(id, NEIGHBOR_PX)
        }
    }
}

private fun ComboWithItems?.photoIds(): Set<String> = this?.items.orEmpty().map { it.photoId }.toSet()
