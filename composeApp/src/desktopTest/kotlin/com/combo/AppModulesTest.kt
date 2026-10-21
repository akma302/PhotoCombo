package com.combo

import com.combo.core.image.FileImageSource
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImagePicker
import com.combo.core.platform.AppDirs
import com.combo.core.platform.Logger
import com.combo.core.platform.WallClock
import com.combo.data.AppDb
import com.combo.di.appModule
import com.combo.di.desktopModule
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import java.nio.file.Files

/**
 * Сборка графа зависимостей целиком: модули Koin на реальных desktop-реализациях.
 * Проверяет и то, что все интерфейсы домена и платформы разрешаются, и сквозной путь
 * «выбрали файл → импорт → битмап из кэша» на временном каталоге.
 */
class AppModulesTest {

    private val files = FileSystem.SYSTEM

    @Test
    fun `the graph resolves every domain and platform interface`() {
        val dir = Files.createTempDirectory("photocombo-koin-test")
        val app = koinApplication { modules(appModule, desktopModule(dir.toOkioPath())) }
        try {
            assertEquals(dir.toOkioPath(), app.koin.get<AppDirs>().dataDir)
            assertNotNull(app.koin.get<FileSystem>())
            assertNotNull(app.koin.get<Logger>())
            assertNotNull(app.koin.get<ImageCodec>())
            assertNotNull(app.koin.get<ImagePicker>())
            assertNotNull(app.koin.get<WallClock>())
            assertNotNull(app.koin.get<PhotoRepository>())
            assertNotNull(app.koin.get<ComboRepository>())
            assertNotNull(app.koin.get<BitmapSource>())
            assertNotNull(app.koin.get<AppDb>())
        } finally {
            app.close()
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a photo imported through the graph lands in the data directory`() = runBlocking {
        val dir = Files.createTempDirectory("photocombo-koin-test")
        val app = koinApplication { modules(appModule, desktopModule(dir.toOkioPath())) }
        try {
            val dataDir = dir.toOkioPath()
            val sourcePath = dataDir / "incoming.png"
            files.write(sourcePath) { write(ImageFixtures.pngBytes(ImageFixtures.patternBitmap())) }

            val repo = app.koin.get<PhotoRepository>()
            val result = repo.importPhotos(listOf(FileImageSource(sourcePath, files)))

            assertEquals(1, result.added)
            val photo = repo.all().first().single()
            assertTrue(files.exists(dataDir / "photos" / photo.fileName))
            assertTrue(files.exists(dataDir / "thumbs" / "${photo.id}.jpg"))

            val bitmap = app.koin.get<BitmapSource>().get(photo.id, 64)
            assertEquals(4, bitmap.width)
            assertEquals(2, bitmap.height)

            assertTrue(app.koin.get<ComboRepository>().all().first().isEmpty())
        } finally {
            app.close()
            dir.toFile().deleteRecursively()
        }
    }
}
