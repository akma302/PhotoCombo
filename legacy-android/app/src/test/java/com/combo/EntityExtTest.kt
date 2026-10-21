package com.combo

import com.combo.core.background.BackgroundSpec
import com.combo.core.render.Border
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.data.PhotoEntity
import com.combo.data.backgroundFrames
import com.combo.data.border
import com.combo.data.srcSize
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `data/EntityExt.kt`: преобразование Room-сущностей в термины ядра (рамка, размер фото,
 * вход для фона).
 *
 * [backgroundFrames] — единственное место, где собирается вход для фона комбо; тест
 * фиксирует его правило: в рамку идут цвета ВСЕХ её фото, а не только активного.
 */
class EntityExtTest {

    private fun photo(id: String, w: Int, h: Int, avg: Int) = PhotoEntity(
        id = id,
        fileName = "$id.jpg",
        originalName = "$id.jpg",
        mimeType = "image/jpeg",
        width = w,
        height = h,
        sizeBytes = 1,
        sha256 = "sha-$id",
        avgColor = avg,
        importedAt = 0L,
    )

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `border and srcSize convert entity fields to geometry types`() {
        val item = ComboItemEntity(
            id = "i1", comboId = "c1", zIndex = 0,
            centerX = 400f, centerY = 225f, width = 800f, height = 450f,
            switchMode = "MANUAL", manualTransition = "NONE", autoTransition = "NONE",
            framingMode = "FREE",
        )
        assertEquals(Border(400f, 225f, 800f, 450f), item.border())

        assertEquals(Size(1200f, 800f), photo("p1", 1200, 800, red).srcSize())
    }

    @Test
    fun `backgroundFrames collects colors of all photos of every frame`() {
        val combo = ComboWithItems(
            combo = ComboEntity(
                id = "c1", name = "C", canvasWidth = 1600, canvasHeight = 900,
                background = BackgroundSpec.Auto(), createdAt = 0L, updatedAt = 0L,
            ),
            items = listOf(
                ItemWithContents(
                    item = itemEntity("i1", 0, centerX = 400f),
                    contents = listOf(content("ct1", "i1", "p1"), content("ct2", "i1", "p2")),
                ),
                ItemWithContents(
                    item = itemEntity("i2", 1, centerX = 1200f),
                    contents = listOf(content("ct3", "i2", "p3")),
                ),
            ),
        )
        val photos = mapOf("p1" to photo("p1", 10, 10, red), "p2" to photo("p2", 10, 10, green))

        val frames = combo.backgroundFrames(photos)

        assertEquals(2, frames.size)
        assertEquals(Border(400f, 450f, 800f, 900f), frames[0].first)
        // Не только активное фото: иначе фон мигал бы при смене фото.
        assertEquals(listOf(red, green), frames[0].second)
        assertEquals(Border(1200f, 450f, 800f, 900f), frames[1].first)
        // Недогруженное фото пропускается, а не роняет расчёт фона.
        assertEquals(emptyList<Int>(), frames[1].second)
    }

    private fun itemEntity(id: String, z: Int, centerX: Float) = ComboItemEntity(
        id = id, comboId = "c1", zIndex = z,
        centerX = centerX, centerY = 450f, width = 800f, height = 900f,
        switchMode = "MANUAL", manualTransition = "NONE", autoTransition = "NONE",
        framingMode = "FREE",
    )

    private fun content(id: String, itemId: String, photoId: String) = ComboItemContentEntity(
        id = id, itemId = itemId, photoId = photoId, orderIndex = 0,
        contentScale = 1f, panX = 0f, panY = 0f,
    )
}