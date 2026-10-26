package com.combo

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import com.combo.core.image.ExifOrientation
import com.combo.core.image.FileImageSource
import com.combo.core.image.ImageSource
import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.DesktopAppDirs
import com.combo.data.PhotoStorage
import com.combo.domain.ImportResult
import com.combo.domain.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.math.abs

/**
 * Импорт на настоящем кодеке Skia и настоящей файловой системе: дедуп, уборка файлов
 * при сбоях, нормализация EXIF, миниатюра и строка в БД — всё вместе, как в приложении.
 */
class PhotoImporterTest {

    private lateinit var root: java.io.File
    private lateinit var storage: PhotoStorage
    private lateinit var codec: SkiaImageCodec
    private lateinit var logger: FakeLogger
    private lateinit var dao: FakePhotoDao
    private lateinit var importer: PhotoImporter
    private val files = FileSystem.SYSTEM
    private var idCounter = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("photocombo-import-test").toFile()
        storage = PhotoStorage(DesktopAppDirs(root.toOkioPath()), files)
        logger = FakeLogger()
        codec = SkiaImageCodec(files, logger)
        dao = FakePhotoDao()
        idCounter = 0
        importer = PhotoImporter(
            photoDao = dao,
            storage = storage,
            codec = codec,
            files = files,
            logger = logger,
            nowMillis = { 1000L },
            newId = { "p${idCounter++}" },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `an imported photo gets a row, a file and a thumbnail`() = runBlocking {
        val bytes = ImageFixtures.jpegBytes(ImageFixtures.patternBitmap())

        val result = importer.importPhotos(listOf(source("one.jpg", bytes)))

        assertEquals(ImportResult(added = 1, duplicates = 0, failed = 0), result)
        val photo = dao.rows.values.single()
        assertEquals("p0", photo.id)
        assertEquals("p0.jpg", photo.fileName)
        assertEquals("src_one.jpg", photo.originalName)
        assertEquals("image/jpeg", photo.mimeType)
        assertEquals(4, photo.width)
        assertEquals(2, photo.height)
        assertEquals(bytes.size.toLong(), photo.sizeBytes)
        assertEquals(PhotoImporter.sha256Hex(Buffer().write(bytes)), photo.sha256)
        assertEquals(0xFF, photo.avgColor ushr 24)
        assertEquals(1000L, photo.importedAt)

        val storedPhoto = storage.photoPath(photo.fileName)
        val storedThumb = storage.thumbPath(photo.id)
        assertTrue(files.exists(storedPhoto))
        assertTrue(files.exists(storedThumb))
        assertEquals(IntSize(4, 2), codec.readBounds(storedThumb))
    }

    @Test
    fun `importing the same file twice stores it once`() = runBlocking {
        val bytes = ImageFixtures.pngBytes(ImageFixtures.patternBitmap())

        val result = importer.importPhotos(listOf(source("a.png", bytes), source("b.png", bytes)))

        assertEquals(ImportResult(added = 1, duplicates = 1, failed = 0), result)
        assertEquals(1, dao.rows.size)
        assertEquals(1, files.list(storage.photosDir()).size)
        assertEquals(1, files.list(storage.thumbsDir()).size)
    }

    @Test
    fun `a png keeps its extension and mime type`() = runBlocking {
        importer.importPhotos(
            listOf(source("photo.png", ImageFixtures.pngBytes(ImageFixtures.patternBitmap())))
        )

        val photo = dao.rows.values.single()
        assertEquals("p0.png", photo.fileName)
        assertEquals("image/png", photo.mimeType)
    }

    @Test
    fun `a file that is not an image leaves no trace`() = runBlocking {
        val result = importer.importPhotos(listOf(source("bad.jpg", "not an image".toByteArray())))

        assertEquals(ImportResult(added = 0, duplicates = 0, failed = 1), result)
        assertTrue(files.list(storage.photosDir()).isEmpty())
        assertTrue(files.list(storage.thumbsDir()).isEmpty())
    }

    @Test
    fun `a source that cannot be read counts as failed`() = runBlocking {
        val broken = object : ImageSource {
            override val name = "broken.jpg"
            override val mimeType = "image/jpeg"
            override fun openSource(): Source = throw IOException("файл исчез")
        }

        val result = importer.importPhotos(listOf(broken))

        assertEquals(ImportResult(added = 0, duplicates = 0, failed = 1), result)
        assertTrue(logger.warnings.isNotEmpty())
        assertTrue(files.list(storage.photosDir()).isEmpty())
    }

    @Test
    fun `exif orientation is normalized away on import`() = runBlocking {
        val stored = ImageFixtures.halvesBitmap(8, 4)
        val bytes = ImageFixtures.jpegWithOrientation(6, stored)

        importer.importPhotos(listOf(source("rotated.jpg", bytes)))

        val photo = dao.rows.values.single()
        assertEquals("ширина после поворота", 4, photo.width)
        assertEquals("высота после поворота", 8, photo.height)

        val file = storage.photoPath(photo.fileName)
        assertEquals(IntSize(4, 8), codec.readBounds(file))
        assertEquals(ExifOrientation.NORMAL, codec.readOrientation(file))

        // Поворот на 90° по часовой: левая (красная) половина оказалась сверху.
        val decoded = codec.decode(file)!!
        assertColorNear(ImageFixtures.RED, decoded.asSkiaBitmap().getColor(0, 0))
        assertColorNear(ImageFixtures.BLUE, decoded.asSkiaBitmap().getColor(0, 7))
    }

    @Test
    fun `an insert failure removes the copied files`() = runBlocking {
        dao.failNextInsert = true

        val result = importer.importPhotos(
            listOf(source("x.png", ImageFixtures.pngBytes(ImageFixtures.patternBitmap())))
        )

        assertEquals(ImportResult(added = 0, duplicates = 0, failed = 1), result)
        assertTrue(logger.warnings.isNotEmpty())
        assertTrue(files.list(storage.photosDir()).isEmpty())
        assertTrue(files.list(storage.thumbsDir()).isEmpty())
    }

    @Test
    fun `sha256Hex matches the known digest of hello`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            PhotoImporter.sha256Hex(Buffer().writeUtf8("hello")),
        )
    }

    @Test
    fun `extensionForMime maps known image types`() {
        assertEquals("png", PhotoImporter.extensionForMime("image/png"))
        assertEquals("webp", PhotoImporter.extensionForMime("image/webp"))
        assertEquals("jpg", PhotoImporter.extensionForMime("image/jpeg"))
        assertEquals("jpg", PhotoImporter.extensionForMime("image/heic"))
    }

    @Test
    fun `sampleSizeForThumbnail fits the long side into 512`() {
        assertEquals(1, PhotoImporter.sampleSizeForThumbnail(512, 300))
        assertEquals(2, PhotoImporter.sampleSizeForThumbnail(513, 300))
        assertEquals(8, PhotoImporter.sampleSizeForThumbnail(4096, 3072))
        assertEquals(1, PhotoImporter.sampleSizeForThumbnail(100, 100))
    }

    private fun source(name: String, bytes: ByteArray): FileImageSource {
        val path = java.io.File(root, "src_$name").toOkioPath()
        files.write(path) { write(bytes) }
        return FileImageSource(path, files)
    }

    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int = 48) {
        for (shift in intArrayOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(
                "канал со сдвигом $shift: ожидали ~$e, получили $a",
                abs(e - a) <= tolerance,
            )
        }
    }
}
