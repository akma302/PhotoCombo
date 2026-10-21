package com.combo

import com.combo.core.background.BackgroundSpec
import com.combo.data.AppDb
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.PhotoEntity
import com.combo.data.createAppDb
import com.combo.domain.ComboRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Репозиторий комбо на настоящей базе: сохранение одним действием, замена рамок при
 * повторном сохранении и сортировка рамок, которую не делает `@Relation`.
 */
class ComboRepositoryTest {

    private lateinit var dir: File
    private lateinit var db: AppDb
    private lateinit var repo: ComboRepositoryImpl

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("photocombo-combo-repo-test").toFile()
        db = createAppDb(File(dir, "test.db"))
        repo = ComboRepositoryImpl(db, db.combos())
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `saveCombo stores the combo with its items at once`() = runBlocking {
        db.photos().insert(photo("p1"))
        db.photos().insert(photo("p2"))

        repo.saveCombo(
            combo = combo("c1"),
            items = listOf(item("i1", "c1", 0, "p1"), item("i2", "c1", 1, "p2")),
        )

        val loaded = repo.get("c1")!!
        assertEquals("Combo c1", loaded.combo.name)
        assertEquals(listOf("i1", "i2"), loaded.items.map { it.id })
        assertEquals(listOf("p1", "p2"), loaded.items.map { it.photoId })
    }

    @Test
    fun `items come back sorted by zIndex`() = runBlocking {
        db.photos().insert(photo("p1"))

        repo.saveCombo(
            combo = combo("c1"),
            items = listOf(item("iHigh", "c1", 1, "p1"), item("iLow", "c1", 0, "p1")),
        )

        assertEquals(listOf("iLow", "iHigh"), repo.get("c1")!!.items.map { it.id })
    }

    @Test
    fun `saving again replaces the previous items`() = runBlocking {
        db.photos().insert(photo("p1"))
        db.photos().insert(photo("p2"))

        repo.saveCombo(combo("c1"), listOf(item("i1", "c1", 0, "p1")))
        repo.saveCombo(combo("c1"), listOf(item("i2", "c1", 0, "p2")))

        val loaded = repo.get("c1")!!
        assertEquals(listOf("i2"), loaded.items.map { it.id })
        assertEquals("p2", loaded.items.single().photoId)
    }

    @Test
    fun `all also sorts items`() = runBlocking {
        db.photos().insert(photo("p1"))

        repo.saveCombo(
            combo("c1"),
            listOf(item("i2", "c1", 2, "p1"), item("i1", "c1", 1, "p1")),
        )

        val loaded = repo.all().first().single()
        assertEquals(listOf("i1", "i2"), loaded.items.map { it.id })
    }

    @Test
    fun `delete removes the combo entirely`() = runBlocking {
        db.photos().insert(photo("p1"))
        repo.saveCombo(combo("c1"), listOf(item("i1", "c1", 0, "p1")))

        repo.delete("c1")

        assertNull(repo.get("c1"))
    }

    private fun photo(id: String) = PhotoEntity(
        id = id, fileName = "$id.jpg", originalName = null, mimeType = "image/jpeg",
        width = 100, height = 100, sizeBytes = 0, sha256 = "sha-$id",
        avgColor = 0xFF000000.toInt(), importedAt = 0,
    )

    private fun combo(id: String) = ComboEntity(
        id = id, name = "Combo $id", canvasWidth = 100, canvasHeight = 100,
        background = BackgroundSpec.Auto(), createdAt = 0, updatedAt = 0,
    )

    private fun item(id: String, comboId: String, z: Int, photoId: String) = ComboItemEntity(
        id = id, comboId = comboId, zIndex = z,
        centerX = 0f, centerY = 0f, width = 10f, height = 10f,
        framingMode = "FREE",
        photoId = photoId, contentScale = 1f, panX = 0f, panY = 0f,
    )
}
