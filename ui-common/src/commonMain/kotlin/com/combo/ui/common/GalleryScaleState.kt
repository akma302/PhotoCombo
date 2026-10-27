package com.combo.ui.common

import com.combo.data.normalizedGalleryPercent
import com.combo.domain.GalleryKind
import com.combo.domain.GallerySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryScaleUiState(
    val percent: Float = 100f,
    val loaded: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Живёт вместе с ViewModel вкладки; перетаскивание меняет только UI, завершение сохраняет выбор. */
class GalleryScaleState(
    private val scope: CoroutineScope,
    private val repository: GallerySettingsRepository,
    private val kind: GalleryKind,
) {
    private val mutableState = MutableStateFlow(GalleryScaleUiState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            val settings = repository.load()
            mutableState.value = GalleryScaleUiState(
                percent = when (kind) {
                    GalleryKind.Photos -> settings.photosPercent
                    GalleryKind.Combos -> settings.combosPercent
                },
                loaded = true,
            )
        }
    }

    fun change(percent: Float) {
        if (mutableState.value.loaded) mutableState.update { it.copy(percent = normalizedGalleryPercent(percent)) }
    }

    fun commit() {
        val state = mutableState.value
        if (!state.loaded) return
        scope.launch {
            val saved = repository.saveScale(kind, state.percent)
            mutableState.update { it.copy(saveFailed = !saved) }
        }
    }

    fun reset() {
        change(100f)
        commit()
    }
}
