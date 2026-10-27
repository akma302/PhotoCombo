package com.combo

import com.combo.core.platform.Logger
import com.combo.data.*
import com.combo.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import okio.IOException
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GallerySettingsRepositoryTest {
    private val logger = object : Logger { override fun warn(tag: String, message: String, error: Throwable?) {} }

    @Test fun serializedUpdatesPreserveBothTabsAndRetryUsesCurrentValues() = runTest {
        var saved = GallerySettings(80f, 120f)
        var fail = false
        val writes = mutableListOf<GallerySettings>()
        val storage = object : GallerySettingsStorage {
            override fun read() = saved
            override fun write(settings: GallerySettings) {
                if (fail) throw IOException("fixture")
                saved = settings
                writes += settings
            }
        }
        val repo = GallerySettingsRepositoryImpl(storage, StandardTestDispatcher(testScheduler), logger)
        assertEquals(saved, repo.load())
        launch { assertTrue(repo.saveScale(GalleryKind.Photos, 50f)) }
        launch { assertTrue(repo.saveScale(GalleryKind.Combos, 200f)) }
        advanceUntilIdle()
        assertEquals(listOf(GallerySettings(50f, 120f), GallerySettings(50f, 200f)), writes)
        fail = true
        assertFalse(repo.saveScale(GalleryKind.Photos, 150f))
        assertEquals(GallerySettings(150f, 200f), repo.load())
        assertEquals(GallerySettings(50f, 200f), saved)
        fail = false
        assertTrue(repo.saveScale(GalleryKind.Combos, 100f))
        assertEquals(GallerySettings(150f, 100f), saved)
        assertEquals(saved, GallerySettingsRepositoryImpl(storage, StandardTestDispatcher(testScheduler), logger).load())
    }

    @Test fun unreadableSettingsDoNotPreventOpeningGallery() = runTest {
        val storage = object : GallerySettingsStorage {
            override fun read(): GallerySettings = throw IOException("fixture")
            override fun write(settings: GallerySettings) {}
        }
        val repo = GallerySettingsRepositoryImpl(storage, StandardTestDispatcher(testScheduler), logger)
        assertEquals(GallerySettings(), repo.load())
        assertTrue(repo.saveScale(GalleryKind.Photos, 125f))
    }
}
