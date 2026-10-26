package com.combo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.combo.core.background.BackgroundSpec
import com.combo.data.AppDb
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.PhotoEntity
import com.combo.data.createAppDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Проверка Room на desktop: файловая БД во временном каталоге, настоящий драйвер SQLite.
 * Покрывает то, на чём держится слой данных: связь рамок с комбо (`@Relation`), каскадное
 * удаление, `RESTRICT` на фото, уникальность хеша и конвертер фона.
 */
class AppDbTest {

    private lateinit var dir: File
    private lateinit var db: AppDb

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("photocombo-db-test").toFile()
        db = createAppDb(File(dir, "test.db"))
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun photo(id: String, sha: String = "sha-$id") = PhotoEntity(
        id = id, fileName = "$id.jpg", originalName = "$id.jpg", mimeType = "image/jpeg",
        width = 1200, height = 800, sizeBytes = 4096, sha256 = sha,
        avgColor = 0xFF808080.toInt(), importedAt = 0L,
    )

    private fun combo(id: String, background: BackgroundSpec = BackgroundSpec.Auto()) = ComboEntity(
        id = id, name = "Combo $id", canvasWidth = 1600, canvasHeight = 900,
        background = background, createdAt = 0L, updatedAt = 0L,
    )

    private fun item(id: String, comboId: String, z: Int, photoId: String = "p1") = ComboItemEntity(
        id = id, comboId = comboId, zIndex = z,
        centerX = 800f, centerY = 450f, width = 400f, height = 300f,
        rotation = 0f, framingMode = "FREE",
        photoId = photoId, contentScale = 1f, panX = 0f, panY = 0f,
    )

    /** Комбо из двух рамок: в первой фото p1, во второй p2. */
    private suspend fun saveSampleCombo(comboId: String = "c1", background: BackgroundSpec = BackgroundSpec.Auto()) {
        db.photos().insert(photo("p1"))
        db.photos().insert(photo("p2"))
        db.combos().upsert(combo(comboId, background))
        db.combos().insertItems(listOf(item("i1", comboId, 0, "p1"), item("i2", comboId, 1, "p2")))
    }

    @Test
    fun `a saved combo is read back with its items and their photos`() = runBlocking {
        saveSampleCombo()

        val loaded = db.combos().get("c1")
        assertNotNull(loaded)
        assertEquals("Combo c1", loaded!!.combo.name)
        assertEquals(setOf("i1", "i2"), loaded.items.map { it.id }.toSet())
        val photoByItem = loaded.items.associate { it.id to it.photoId }
        assertEquals("p1", photoByItem.getValue("i1"))
        assertEquals("p2", photoByItem.getValue("i2"))
    }

    @Test
    fun `the combo list flow emits saved combos`() = runBlocking {
        saveSampleCombo()

        val all = db.combos().all().first()
        assertEquals(listOf("c1"), all.map { it.combo.id })
        assertEquals(2, all.single().items.size)
    }

    @Test
    fun `background spec survives a round trip through the database`() = runBlocking {
        saveSampleCombo("auto", BackgroundSpec.Auto())
        db.combos().upsert(combo("solid", BackgroundSpec.Solid(0xFF112233.toInt())))

        assertEquals(BackgroundSpec.Auto(), db.combos().get("auto")!!.combo.background)
        assertEquals(BackgroundSpec.Solid(0xFF112233.toInt()), db.combos().get("solid")!!.combo.background)
    }

    @Test
    fun `deleting a combo removes its items`() = runBlocking {
        saveSampleCombo()
        assertTrue(db.combos().isPhotoUsed("p1"))

        db.combos().delete("c1")

        assertNull(db.combos().get("c1"))
        assertFalse(db.combos().isPhotoUsed("p1"))
        assertFalse(db.combos().isPhotoUsed("p2"))
    }

    @Test
    fun `a photo used in a combo cannot be deleted`() = runBlocking {
        saveSampleCombo()

        val failed = runCatching { db.photos().delete(photo("p1")) }.isFailure

        assertTrue("удаление фото из комбо должно упасть на RESTRICT", failed)
        assertNotNull(db.photos().get("p1"))
    }

    @Test
    fun `a photo not used in any combo can be deleted`() = runBlocking {
        db.photos().insert(photo("free"))

        db.photos().delete(photo("free"))

        assertNull(db.photos().get("free"))
    }

    @Test
    fun `two photos with the same hash cannot both be stored`() = runBlocking {
        db.photos().insert(photo("a", sha = "same"))

        val failed = runCatching { db.photos().insert(photo("b", sha = "same")) }.isFailure

        assertTrue("sha256 уникален: второй insert должен упасть", failed)
        assertEquals("a", db.photos().findByHash("same")!!.id)
    }

    @Test
    fun `an item cannot reference a photo that does not exist`() = runBlocking {
        db.combos().upsert(combo("c1"))

        val failed = runCatching { db.combos().insertItems(listOf(item("i1", "c1", 0, "ghost"))) }.isFailure

        assertTrue("ссылка на несуществующее фото должна упасть на внешнем ключе", failed)
    }

    /**
     * Транзакция через `useWriterConnection`: именно этим способом репозиторий комбо будет
     * сохранять комбо и его рамки одним действием.
     */
    @Test
    fun `a committed transaction persists all of its changes`() = runBlocking {
        db.photos().insert(photo("p1"))

        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.photos().insert(photo("p2"))
                db.photos().insert(photo("p3"))
            }
        }

        assertNotNull(db.photos().get("p2"))
        assertNotNull(db.photos().get("p3"))
    }

    @Test
    fun `an aborted transaction leaves the database unchanged`() = runBlocking {
        db.photos().insert(photo("p1"))

        val failed = runCatching {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    db.photos().insert(photo("p2"))
                    throw IllegalStateException("rollback")
                }
            }
        }.isFailure

        assertTrue("исключение внутри транзакции должно выйти наружу", failed)
        assertNotNull(db.photos().get("p1"))
        assertNull(db.photos().get("p2"))
    }
}
