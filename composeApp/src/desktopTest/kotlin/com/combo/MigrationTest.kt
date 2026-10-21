package com.combo

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.combo.core.background.BackgroundSpec
import com.combo.data.AppDb
import com.combo.data.createAppDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Миграция базы 1 → 2 («одна рамка — одно фото»).
 *
 * База версии 1 строится руками, обычным SQL, по схеме из `schemas/.../1.json`: так тест не
 * зависит от того, умеет ли тестовая библиотека Room на desktop проверять миграции, и при
 * этом открывает файл тем же `createAppDb`, что и приложение (миграция подключена там).
 * Если схема после миграции не совпала бы с сущностями, Room отказался бы открывать базу.
 */
class MigrationTest {

    private lateinit var dir: File
    private lateinit var file: File
    private var db: AppDb? = null

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("photocombo-migration-test").toFile()
        file = File(dir, "old.db")
    }

    @After
    fun tearDown() {
        db?.close()
        dir.deleteRecursively()
    }

    /** Файл базы версии 1 с данными: см. комментарии к каждой рамке. */
    private fun createV1Database() {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            V1_DDL.forEach { connection.execSQL(it) }
            connection.execSQL("PRAGMA user_version = 1")
            connection.execSQL(
                "INSERT INTO photos VALUES ('p1','p1.jpg','p1.jpg','image/jpeg',1200,800,10,'sha1',-8355712,0)"
            )
            connection.execSQL(
                "INSERT INTO photos VALUES ('p2','p2.jpg','p2.jpg','image/jpeg',800,1200,10,'sha2',-8355712,0)"
            )
            connection.execSQL(
                "INSERT INTO combos VALUES ('c1','Trip',1600,900,'{\"type\":\"solid\",\"argb\":-16711936}',100,200)"
            )
            // i1: два фото, первым по orderIndex идёт p1 (вставлено вторым), режим FIT.
            connection.execSQL(
                "INSERT INTO combo_items VALUES ('i1','c1',3,400,225,800,450,0,'AUTO',5000,'ZOOM','WIPE','FIT')"
            )
            connection.execSQL("INSERT INTO combo_item_contents VALUES ('k_late','i1','p2',1,2.0,10,20)")
            connection.execSQL("INSERT INTO combo_item_contents VALUES ('k_first','i1','p1',0,1.5,3,4)")
            // i2: рамка без фото — в новой модели таких нет.
            connection.execSQL(
                "INSERT INTO combo_items VALUES ('i2','c1',4,1200,675,800,450,0,'MANUAL',3000,'SLIDE','FADE','FREE')"
            )
            // i3: одно фото.
            connection.execSQL(
                "INSERT INTO combo_items VALUES ('i3','c1',5,800,450,300,300,0,'MANUAL',3000,'NONE','NONE','FREE')"
            )
            connection.execSQL("INSERT INTO combo_item_contents VALUES ('k3','i3','p2',0,0.5,0,0)")
        } finally {
            connection.close()
        }
    }

    private fun open(): AppDb = createAppDb(file).also { db = it }

    @Test
    fun `each frame keeps its first photo with its placement`() = runBlocking {
        createV1Database()

        val loaded = open().combos().get("c1")
        assertNotNull(loaded)

        val i1 = loaded!!.items.single { it.id == "i1" }
        assertEquals("p1", i1.photoId)
        assertEquals(1.5f, i1.contentScale, 0f)
        assertEquals(3f, i1.panX, 0f)
        assertEquals(4f, i1.panY, 0f)
        // Рамка, режим и порядок слоёв не меняются.
        assertEquals("FIT", i1.framingMode)
        assertEquals(3, i1.zIndex)
        assertEquals(400f, i1.centerX, 0f)
        assertEquals(800f, i1.width, 0f)

        val i3 = loaded.items.single { it.id == "i3" }
        assertEquals("p2", i3.photoId)
        assertEquals(0.5f, i3.contentScale, 0f)
    }

    @Test
    fun `a frame without photos is dropped`() = runBlocking {
        createV1Database()

        val loaded = open().combos().get("c1")!!

        assertEquals(setOf("i1", "i3"), loaded.items.map { it.id }.toSet())
    }

    @Test
    fun `the combo itself and the photos survive`() = runBlocking {
        createV1Database()

        val database = open()
        val loaded = database.combos().get("c1")!!

        assertEquals("Trip", loaded.combo.name)
        assertEquals(1600, loaded.combo.canvasWidth)
        assertEquals(BackgroundSpec.Solid(-16711936), loaded.combo.background)
        assertNotNull(database.photos().get("p1"))
        assertNotNull(database.photos().get("p2"))
    }

    @Test
    fun `the old contents table is gone and photo usage follows the items`() = runBlocking {
        createV1Database()

        val database = open()

        assertEquals(true, database.combos().isPhotoUsed("p1"))
        database.combos().delete("c1")
        assertEquals(false, database.combos().isPhotoUsed("p1"))
        assertNull(database.combos().get("c1"))
    }

    private companion object {
        /** Схема версии 1 (`schemas/com.combo.data.AppDb/1.json`), `${TABLE_NAME}` подставлен. */
        val V1_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                "`originalName` TEXT, `mimeType` TEXT NOT NULL, `width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                "`avgColor` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_photos_sha256` ON `photos` (`sha256`)",
            "CREATE TABLE IF NOT EXISTS `combos` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`canvasWidth` INTEGER NOT NULL, `canvasHeight` INTEGER NOT NULL, " +
                "`background` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `combo_items` (`id` TEXT NOT NULL, `comboId` TEXT NOT NULL, " +
                "`zIndex` INTEGER NOT NULL, `centerX` REAL NOT NULL, `centerY` REAL NOT NULL, " +
                "`width` REAL NOT NULL, `height` REAL NOT NULL, `rotation` REAL NOT NULL, " +
                "`switchMode` TEXT NOT NULL, `autoIntervalMs` INTEGER NOT NULL, " +
                "`manualTransition` TEXT NOT NULL, `autoTransition` TEXT NOT NULL, " +
                "`framingMode` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`comboId`) " +
                "REFERENCES `combos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_combo_items_comboId` ON `combo_items` (`comboId`)",
            "CREATE TABLE IF NOT EXISTS `combo_item_contents` (`id` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, `photoId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, " +
                "`contentScale` REAL NOT NULL, `panX` REAL NOT NULL, `panY` REAL NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`itemId`) REFERENCES `combo_items`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`photoId`) " +
                "REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_combo_item_contents_itemId` " +
                "ON `combo_item_contents` (`itemId`)",
            "CREATE INDEX IF NOT EXISTS `index_combo_item_contents_photoId` " +
                "ON `combo_item_contents` (`photoId`)",
        )
    }
}
