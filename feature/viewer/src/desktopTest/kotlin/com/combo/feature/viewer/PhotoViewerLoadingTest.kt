package com.combo.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.combo.domain.BitmapSource
import com.combo.ui.FakePhotoRepository
import com.combo.ui.UiTestBase
import com.combo.ui.photoEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewerLoadingTest : UiTestBase() {
    @Test
    fun `a decode that finishes after a page jump cannot republish old images`() {
        val oldDecode = CompletableDeferred<Unit>()
        val requested = mutableListOf<String>()
        val source = object : BitmapSource {
            override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap {
                requested += photoId
                if (photoId == "p0") withContext(NonCancellable) { oldDecode.await() }
                return ImageBitmap(8, 8)
            }
        }
        val photos = FakePhotoRepository((0..6).map { photoEntity("p$it") })
        val vm = PhotoViewerViewModel(SavedStateHandle(), photos, source)

        vm.ensureLoaded(0)
        try {
            assertEquals(listOf("p0"), requested)
            vm.ensureLoaded(5)
            assertEquals(setOf("p4", "p5", "p6"), vm.state.value.images.keys)
        } finally {
            oldDecode.complete(Unit)
        }

        assertEquals(setOf("p4", "p5", "p6"), vm.state.value.images.keys)
        photos.push(emptyList())
        assertTrue(vm.state.value.images.isEmpty())
    }
}
