package com.combo

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.platform.DesktopAppDirs
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import com.combo.domain.image.LruBitmapSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Кэш и дедупликация декодирования. Под тестовым диспетчером видно, сколько декодов
 * реально запущено и какие записи вытеснены, — иначе гонки не были бы воспроизводимы.
 */
class LruBitmapSourceTest {

    private val files = FileSystem.SYSTEM
    private val root: java.io.File = Files.createTempDirectory("photocombo-lru-test").toFile()

    private fun photo(id: String, w: Int = 2000, h: Int = 1000) = PhotoEntity(
        id = id, fileName = "$id.jpg", originalName = null, mimeType = "image/jpeg",
        width = w, height = h, sizeBytes = 0, sha256 = "sha-$id",
        avgColor = 0xFF000000.toInt(), importedAt = 0,
    )

    private fun source(
        dao: FakePhotoDao,
        dispatcher: CoroutineDispatcher,
        maxCacheKb: Int = 1024,
        codec: CountingCodec = CountingCodec(),
    ): Pair<LruBitmapSource, CountingCodec> {
        val lru = LruBitmapSource(
            photoDao = dao,
            storage = PhotoStorage(DesktopAppDirs(root.toOkioPath()), files),
            codec = codec,
            maxCacheKb = maxCacheKb,
            decodeScope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        return lru to codec
    }

    @Test
    fun `concurrent requests for one photo decode once`() = runTest {
        val dao = FakePhotoDao().apply { rows["p1"] = photo("p1") }
        val (lru, codec) = source(dao, StandardTestDispatcher(testScheduler))
        codec.result = { ImageBitmap(100, 100) }

        val first = async { lru.get("p1", 100) }
        val second = async { lru.get("p1", 100) }
        runCurrent()
        advanceUntilIdle()

        assertSame(first.await(), second.await())
        assertEquals(1, codec.decodeCalls)
    }

    @Test
    fun `a cached bitmap is returned without another decode`() = runTest {
        val dao = FakePhotoDao().apply { rows["p1"] = photo("p1") }
        val (lru, codec) = source(dao, StandardTestDispatcher(testScheduler))
        codec.result = { ImageBitmap(64, 64) }

        val first = lru.get("p1", 100)
        val second = lru.get("p1", 100)

        assertSame(first, second)
        assertEquals(1, codec.decodeCalls)
    }

    @Test
    fun `bitmaps of different sample sizes are cached separately`() = runTest {
        val dao = FakePhotoDao().apply { rows["p1"] = photo("p1") }
        val (lru, codec) = source(dao, StandardTestDispatcher(testScheduler))
        codec.result = { ImageBitmap(10, 10) }

        lru.get("p1", 100)
        lru.get("p1", 1000)

        // 2000x1000 при requiredPx=100 даёт sample 8, при requiredPx=1000 — sample 1.
        assertEquals(listOf(8, 1), codec.samples)
        assertEquals(listOf("p1:8", "p1:1"), lru.cachedKeys)
    }

    @Test
    fun `the cache evicts the oldest entry when over the limit`() = runTest {
        val dao = FakePhotoDao().apply {
            rows["p1"] = photo("p1")
            rows["p2"] = photo("p2")
        }
        val (lru, codec) = source(dao, StandardTestDispatcher(testScheduler), maxCacheKb = 40)
        codec.result = { ImageBitmap(100, 100) } // 100 * 100 * 4 / 1024 = 39 КБ

        lru.get("p1", 100)
        lru.get("p2", 100)

        assertEquals(listOf("p2:8"), lru.cachedKeys)
        assertEquals(39, lru.currentCacheSizeKb)
        assertEquals(2, codec.decodeCalls)
    }

    @Test
    fun `a tiny entry still counts as one kilobyte`() {
        val dao = FakePhotoDao()
        val (lru, _) = source(dao, StandardTestDispatcher())

        assertEquals(1, lru.cacheSizeKb(ImageBitmap(1, 1)))
        assertEquals(1, lru.cacheSizeKb(ImageBitmap(16, 16)))
        assertEquals(39, lru.cacheSizeKb(ImageBitmap(100, 100)))
    }

    @Test
    fun `an unknown photo id fails the request`() = runTest {
        val (lru, _) = source(FakePhotoDao(), StandardTestDispatcher(testScheduler))

        val failure = runCatching { lru.get("ghost", 100) }

        assertTrue(failure.isFailure)
    }
}
