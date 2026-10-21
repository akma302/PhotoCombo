package com.combo

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.core.background.BackgroundSpec
import com.combo.core.background.ResolvedBackground
import com.combo.core.render.Border
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemFrame
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.contentArgsOf
import com.combo.core.render.drawComboBackground
import com.combo.core.render.drawItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C11: `drawComboBackground` реально заливает прямоугольник нужным цветом, а Auto-фон
 * пересчитывается, когда у комбо поменялась геометрия рамок (это и ломалось с ключами
 * `remember` в превью списка).
 *
 * Рисуем прямо в android.graphics.Bitmap: `captureToImage()` у Compose под Robolectric
 * не работает (нужен PixelCopy реального окна), а так проверяется сам результат растеризации.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawComboBackgroundTest {

    private fun rasterize(
        size: Int = 100,
        block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp.asImageBitmap())
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(size.toFloat(), size.toFloat()),
            block = block,
        )
        return bmp
    }

    @Test
    fun `solid background fills only the requested rect`() {
        val bmp = rasterize {
            drawComboBackground(
                ResolvedBackground.Solid(0xFF123456.toInt()),
                Offset(10f, 10f),
                Size(40f, 40f),
            )
        }
        assertArgbEquals(Triple(0x12, 0x34, 0x56), bmp.getPixel(30, 30), tolPerChannel = 1)
        assertEquals("вне прямоугольника ничего не рисуется", 0, bmp.getPixel(80, 80) ushr 24)
    }

    /**
     * Проверяем именно вес по площади, а не цвет: одна рамка красит фон в цвет своего фото
     * при любом положении, и такой тест проходил бы и с совсем сломанной геометрией.
     * Две рамки (красная 0..75, синяя 75..100) 50/50 дают (140, 83, 162), 75/25 — (198, 73, 109).
     */
    @Test
    fun `auto background follows frame geometry`() {
        val even = rasterize {
            drawComboBackground(
                comboBackgroundOf(
                    100, 100,
                    BackgroundSpec.Auto(),
                    listOf(
                        Border(25f, 50f, 50f, 100f) to listOf(0xFFFF0000.toInt()),
                        Border(75f, 50f, 50f, 100f) to listOf(0xFF0000FF.toInt()),
                    ),
                ),
                Offset.Zero,
                Size(100f, 100f),
            )
        }
        val skewed = rasterize {
            drawComboBackground(
                comboBackgroundOf(
                    100, 100,
                    BackgroundSpec.Auto(),
                    listOf(
                        Border(37.5f, 50f, 75f, 100f) to listOf(0xFFFF0000.toInt()),
                        Border(87.5f, 50f, 25f, 100f) to listOf(0xFF0000FF.toInt()),
                    ),
                ),
                Offset.Zero,
                Size(100f, 100f),
            )
        }

        assertArgbEquals(Triple(140, 83, 162), even.getPixel(50, 50), tolPerChannel = 1)
        assertArgbEquals(Triple(198, 73, 109), skewed.getPixel(50, 50), tolPerChannel = 1)
        assertNotEquals(even.getPixel(50, 50), skewed.getPixel(50, 50))
    }

    /**
     * Режим рамки AutoFit проверяется по пикселям, а не по структуре: рамка 200×100,
     * квадратное красное фото 100×100. В FIT фото занимает всю высоту (100×100) и
     * центрируется, поэтому слева остаётся подложка (`avgColor` α=0.5) — это и видно
     * по «не чисто красному» пикселю у левого края.
     *
     * Контрольный прогон в FREE с тем же сохранённым `contentScale`, что был бы cover
     * (`2`), доказывает, что разницу создаёт именно режим, а не раскладка теста.
     */
    @Test
    fun `FIT framing letterboxes a square photo in a wide frame`() {
        val photo = solidBitmap(100, 100, 0xFFFF0000.toInt())
        val border = Border(100f, 50f, 200f, 100f)
        val fill = Color(0xFFFF0000)

        val fitted = rasterize(size = 200) {
            drawComboBackground(
                ResolvedBackground.Solid(0xFF000000.toInt()),
                Offset.Zero,
                Size(200f, 100f),
            )
            drawItem(
                ItemFrame(
                    border = border,
                    fillColor = fill,
                    incoming = contentArgsOf(
                        FramingMode.FIT, photo, border, 100, 100,
                        storedScale = 2f, storedPanX = 0f, storedPanY = 0f,
                    ),
                ),
            )
        }

        assertArgbEquals(Triple(255, 0, 0), fitted.getPixel(100, 50), tolPerChannel = 2)
        assertNotEquals(
            "слева должно быть видно подложку, а не фото",
            0xFFFF0000.toInt(),
            fitted.getPixel(10, 50),
        )

        // Тот же кадр в FREE: cover заполняет всю рамку, красным становится и левый край.
        val free = rasterize(size = 200) {
            drawComboBackground(
                ResolvedBackground.Solid(0xFF000000.toInt()),
                Offset.Zero,
                Size(200f, 100f),
            )
            drawItem(
                ItemFrame(
                    border = border,
                    fillColor = fill,
                    incoming = contentArgsOf(
                        FramingMode.FREE, photo, border, 100, 100,
                        storedScale = 2f, storedPanX = 0f, storedPanY = 0f,
                    ),
                ),
            )
        }
        assertArgbEquals(Triple(255, 0, 0), free.getPixel(10, 50), tolPerChannel = 2)
    }

    private fun solidBitmap(w: Int, h: Int, argb: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(argb)
        return bmp.asImageBitmap()
    }
}
