package com.combo.ui.common

import com.combo.ImageFixtures
import com.combo.domain.image.ThumbnailSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailStateTest {
    @Test
    fun `a late thumbnail cannot reappear after its cell leaves the grid`() = runTest {
        val release = CompletableDeferred<Unit>()
        val loaded = mutableListOf<String>()
        val bitmap = ImageFixtures.patternBitmap()
        val thumbnails = ThumbnailState(this, ThumbnailSource { id ->
            loaded += id
            if (id == "slow") withContext(NonCancellable) { release.await() }
            bitmap
        })

        thumbnails.setVisiblePhotos(setOf("slow", "shared"))
        runCurrent()
        assertEquals(setOf("shared"), thumbnails.images.value.keys)
        thumbnails.setVisiblePhotos(setOf("shared", "nearby"))
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf("shared", "nearby"), thumbnails.images.value.keys)
        assertEquals(listOf("slow", "shared", "nearby"), loaded)
        thumbnails.setVisiblePhotos(emptySet())
        assertTrue(thumbnails.images.value.isEmpty())
    }
}
