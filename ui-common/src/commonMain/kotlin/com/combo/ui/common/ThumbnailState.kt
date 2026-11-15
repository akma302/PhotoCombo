package com.combo.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.domain.image.ThumbnailSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owned by a ViewModel. Keep both results and requests within the visible grid cells. */
class ThumbnailState(private val scope: CoroutineScope, private val source: ThumbnailSource) {
    private val requests = mutableMapOf<String, Job>()
    private val _images = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val images = _images.asStateFlow()

    /** Called on the main thread by the screen that reports the grid's visible photo IDs. */
    fun setVisiblePhotos(ids: Set<String>) {
        val departed = requests.keys - ids
        departed.forEach { requests.remove(it)?.cancel() }
        if (departed.isNotEmpty()) _images.update { it - departed }

        for (id in ids - requests.keys) {
            // Register the job before allowing an immediately available result to reach the UI.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val image = source.load(id) ?: return@launch
                ensureActive()
                _images.update { it + (id to image) }
            }
            requests[id] = job
            job.start()
        }
    }
}
