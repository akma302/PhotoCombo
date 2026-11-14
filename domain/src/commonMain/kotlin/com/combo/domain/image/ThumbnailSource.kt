package com.combo.domain.image

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.image.ImageCodec
import com.combo.data.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

fun interface ThumbnailSource {
    /** Returns null when the thumbnail file is missing or unreadable. */
    suspend fun load(photoId: String): ImageBitmap?
}

class FileThumbnailSource(
    private val storage: PhotoStorage,
    private val codec: ImageCodec,
    private val ioDispatcher: CoroutineDispatcher,
) : ThumbnailSource {
    override suspend fun load(photoId: String): ImageBitmap? = withContext(ioDispatcher) {
        codec.decode(storage.thumbPath(photoId))
    }
}
