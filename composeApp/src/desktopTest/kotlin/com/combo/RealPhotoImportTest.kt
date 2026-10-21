package com.combo

import com.combo.core.image.ExifOrientation
import com.combo.core.image.FileImageSource
import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.DesktopAppDirs
import com.combo.data.PhotoStorage
import com.combo.domain.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

private val REAL_PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * Импорт настоящих фотографий из каталога, заданного свойством `photocombo.realphotos`
 * (через `-Prealphotos="<папка>"`). Без свойства тест пропускается: на машинах без фото
 * он не должен падать.
 *
 * Ловит то, чего не ловят синтетические фикстуры: камерные JPEG с EXIF, у которых
 * после импорта ориентация обязана стать NORMAL, а размеры — соответствовать
 * «правильному» виду кадра.
 */
class RealPhotoImportTest {

    private var photoDir: java.io.File? = null

    @Before
    fun readPhotoDir() {
        val raw = System.getProperty("photocombo.realphotos")
        Assume.assumeTrue("photocombo.realphotos не задан — тест пропущен", raw != null)
        val dir = java.io.File(raw!!)
        Assume.assumeTrue("каталог с фото не найден: $raw", dir.isDirectory)
        photoDir = dir
    }

    @Test
    fun `every real photo imports with normal orientation and expected dimensions`() = runBlocking {
        val dir = photoDir!!
        val imageFiles = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.substringAfterLast('.', "").lowercase() in REAL_PHOTO_EXTENSIONS }
            .sortedBy { it.name }
        Assume.assumeTrue("в каталоге нет фотографий", imageFiles.isNotEmpty())

        val root = Files.createTempDirectory("photocombo-realphotos").toFile()
        try {
            val files = FileSystem.SYSTEM
            val storage = PhotoStorage(DesktopAppDirs(root.toOkioPath()), files)
            val codec = SkiaImageCodec(files, FakeLogger())
            val dao = FakePhotoDao()
            var counter = 0
            val importer = PhotoImporter(
                photoDao = dao,
                storage = storage,
                codec = codec,
                files = files,
                logger = FakeLogger(),
                nowMillis = { 0L },
                newId = { "r${counter++}" },
                ioDispatcher = Dispatchers.IO,
            )

            // Ожидаемые размеры — как кадр выглядит с учётом EXIF-ориентации:
            // после импорта фото обязано иметь именно их.
            val expected = imageFiles.mapNotNull { file ->
                val path = file.toOkioPath()
                val bounds = codec.readBounds(path) ?: return@mapNotNull null
                val orientation = codec.readOrientation(path)
                val w = if (orientation.swapsDimensions) bounds.height else bounds.width
                val h = if (orientation.swapsDimensions) bounds.width else bounds.height
                path to (w to h)
            }

            val result = importer.importPhotos(expected.map { (path, _) -> FileImageSource(path, files) })

            assertEquals("ничего не импортировалось", expected.size, result.added + result.duplicates)
            assertEquals("сбоев импорта: ${result.failed}", 0, result.failed)
            assertEquals(dao.rows.size, result.added)
            for (photo in dao.rows.values) {
                val dims = expected.first { it.first.name == photo.originalName }.second
                assertEquals("ширина ${photo.originalName}", dims.first, photo.width)
                assertEquals("высота ${photo.originalName}", dims.second, photo.height)
                assertEquals(
                    "ориентация ${photo.originalName} после импорта",
                    ExifOrientation.NORMAL,
                    codec.readOrientation(storage.photoPath(photo.fileName)),
                )
                assertTrue(files.exists(storage.photoPath(photo.fileName)))
                assertTrue(files.exists(storage.thumbPath(photo.id)))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
