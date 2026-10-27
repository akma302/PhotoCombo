package com.combo.ui.common

import com.combo.data.GallerySettings
import com.combo.domain.*
import com.combo.ui.FakeGallerySettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryScaleStateTest {
    @Test fun loadingGatesInputAndChangesPersistOnlyAtCompletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeGallerySettingsRepository().apply { settings = GallerySettings(80f, 130f) }
        val repo = object : GallerySettingsRepository by fake {
            override suspend fun load(): GallerySettings { gate.await(); return fake.load() }
        }
        val photos = GalleryScaleState(backgroundScope, repo, GalleryKind.Photos)
        val combos = GalleryScaleState(backgroundScope, repo, GalleryKind.Combos)
        photos.change(200f)
        photos.commit()
        runCurrent()
        assertFalse(photos.state.value.loaded)
        assertEquals(0, fake.writes)
        gate.complete(Unit)
        runCurrent()
        assertEquals(80f, photos.state.value.percent)
        assertEquals(130f, combos.state.value.percent)
        photos.change(50f)
        photos.change(175f)
        assertEquals(0, fake.writes)
        photos.commit()
        runCurrent()
        assertEquals(GallerySettings(175f, 130f), fake.settings)
        assertEquals(130f, combos.state.value.percent)
        fake.failWrites = true
        combos.change(200f)
        combos.commit()
        runCurrent()
        assertTrue(combos.state.value.saveFailed)
        assertEquals(200f, combos.state.value.percent)
        fake.failWrites = false
        combos.reset()
        runCurrent()
        assertFalse(combos.state.value.saveFailed)
        assertEquals(GallerySettings(175f, 100f), fake.settings)
        val reopened = GalleryScaleState(backgroundScope, repo, GalleryKind.Photos)
        runCurrent()
        assertEquals(175f, reopened.state.value.percent)
    }
}
