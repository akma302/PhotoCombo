package com.combo

import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.DesktopAppDirs
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import com.combo.domain.DeleteResult
import com.combo.domain.PhotoImporter
import com.combo.domain.PhotoRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Репозиторий фото: удаление строки вместе с файлами, блокировка удаления занятого
 * фото, путь миниатюры. Файловая система настоящая, во временном каталоге.
 */
class PhotoRepositoryTest {

    private lateinit var root: java.io.File
    private lateinit var storage: PhotoStorage
    private lateinit var dao: FakePhotoDao
    private lateinit var comboDao: FakeComboDao
    private lateinit var repo: PhotoRepositoryImpl
    private val files = FileSystem.SYSTEM

    @Before
    fun setUp() {
        root = Files.createTempDirectory("photocombo-repo-test").toFile()
        storage = PhotoStorage(DesktopAppDirs(root.toOkioPath()), files)
        dao = FakePhotoDao()
        comboDao = FakeComboDao()
        val logger = FakeLogger()
        repo = PhotoRepositoryImpl(
            photoDao = dao,
            comboDao = comboDao,
            storage = storage,
            files = files,
            importer = PhotoImporter(
                photoDao = dao,
                storage = storage,
                codec = SkiaImageCodec(files, logger),
                files = files,
                logger = logger,
                nowMillis = { 0L },
                newId = { "p0" },
                ioDispatcher = Dispatchers.Unconfined,
            ),
            ioDispatcher = Dispatchers.Unconfined,
        )
        storage.ensureDirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `deleting a free photo removes the row and both files`() = runBlocking {
        val photo = photo("p1")
        dao.rows["p1"] = photo
        createFiles(photo)

        assertEquals(DeleteResult.DELETED, repo.delete("p1"))

        assertNull(dao.rows["p1"])
        assertFalse(files.exists(storage.photoPath(photo.fileName)))
        assertFalse(files.exists(storage.thumbPath(photo.id)))
    }

    @Test
    fun `deleting a photo used in a combo is blocked`() = runBlocking {
        val photo = photo("p1")
        dao.rows["p1"] = photo
        createFiles(photo)
        comboDao.usedPhotoIds = setOf("p1")

        assertEquals(DeleteResult.BLOCKED_IF_USED_IN_COMBO, repo.delete("p1"))

        assertNotNull(dao.rows["p1"])
        assertTrue(files.exists(storage.photoPath(photo.fileName)))
    }

    @Test
    fun `deleting an unknown photo reports deleted`() = runBlocking {
        assertEquals(DeleteResult.DELETED, repo.delete("ghost"))
    }

    @Test
    fun `thumbPath returns the path only when the thumbnail exists`() {
        val photo = photo("p1")
        dao.rows["p1"] = photo

        assertNull(repo.thumbPath("p1"))

        createFiles(photo)
        assertEquals(storage.thumbPath("p1"), repo.thumbPath("p1"))
    }

    @Test
    fun `all emits the rows of the dao`() = runBlocking {
        dao.rows["p1"] = photo("p1")

        assertEquals(listOf("p1"), repo.all().first().map { it.id })
    }

    private fun photo(id: String) = PhotoEntity(
        id = id, fileName = "$id.jpg", originalName = null, mimeType = "image/jpeg",
        width = 10, height = 10, sizeBytes = 0, sha256 = "sha-$id",
        avgColor = 0xFF000000.toInt(), importedAt = 0,
    )

    private fun createFiles(photo: PhotoEntity) {
        files.write(storage.photoPath(photo.fileName)) { writeUtf8("photo") }
        files.write(storage.thumbPath(photo.id)) { writeUtf8("thumb") }
    }
}
