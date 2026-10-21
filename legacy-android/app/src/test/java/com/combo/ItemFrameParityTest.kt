package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemFrame
import com.combo.data.ComboEntity
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.data.PhotoEntity
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.combo_viewer.ComboViewerViewModel
import com.combo.ui.FakeBitmapSource
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * ПАРИТЕТ кадра рамки между тремя путями отрисовки: редактор (`ComboBuilderViewModel.frameFor`),
 * плеер (`ComboViewerViewModel.frameFor`) и превью списка (`ComboListViewModel.framesFor`).
 *
 * Все три собирают кадр через общую фабрику `buildItemFrame`, но у каждого свои адаптеры
 * входных данных (драфты или Room-сущности). Тест гарантирует, что при одинаковом комбо
 * они отдают одинаковые данные для рисования: иначе картинка в редакторе, плеере и
 * списке разойдётся (нарушится WYSIWYG).
 *
 * Сравнивается только то, что влияет на рисование: `border`, `fillColor` и
 * `incoming.{srcW, srcH, scale, pan}`. Сами изображения `image` не сравниваются: пути
 * декодируют битмапы с разным `requiredPx`, и это политика загрузки, а не сборки кадра.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ItemFrameParityTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- фикстура: два фото разных пропорций, рамки в разных режимах ----------

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun photo(id: String, w: Int, h: Int, avgColor: Int) = PhotoEntity(
        id = id,
        fileName = "$id.jpg",
        originalName = "$id.jpg",
        mimeType = "image/jpeg",
        width = w,
        height = h,
        sizeBytes = 1,
        sha256 = "sha-$id",
        avgColor = avgColor,
        importedAt = 0L,
    )

    private val photos = listOf(
        photo("p1", 1200, 800, red),
        photo("p2", 900, 900, blue),
    )

    private val comboEntity = ComboEntity(
        id = "c1",
        name = "Parity",
        canvasWidth = 1600,
        canvasHeight = 900,
        background = com.combo.core.background.BackgroundSpec.Auto(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun itemEntity(id: String, z: Int, framing: FramingMode) = ComboItemEntity(
        id = id,
        comboId = "c1",
        zIndex = z,
        centerX = if (id == "i1") 400f else 800f,
        centerY = if (id == "i1") 450f else 675f,
        width = 800f,
        height = if (id == "i1") 900f else 450f,
        switchMode = "MANUAL",
        manualTransition = "SLIDE",
        autoTransition = "FADE",
        framingMode = framing.name,
    )

    private fun contentEntity(id: String, itemId: String, photoId: String, scale: Float, panX: Float, panY: Float) =
        ComboItemContentEntity(
            id = id,
            itemId = itemId,
            photoId = photoId,
            orderIndex = 0,
            contentScale = scale,
            panX = panX,
            panY = panY,
        )

    /**
     * `i1` — FREE с явно «испорченными» хранимыми значениями (должны пройти как есть),
     * `i2` — FIT с заведомо неверными хранимыми значениями (должны быть заменены вписыванием).
     * Так тест различает оба режима, а не просто сравнивает три одинаковых числа.
     */
    private val combo = ComboWithItems(
        combo = comboEntity,
        items = listOf(
            ItemWithContents(
                item = itemEntity("i1", 0, FramingMode.FREE),
                contents = listOf(contentEntity("ct1", "i1", "p1", 0.9f, 12f, -7f)),
            ),
            ItemWithContents(
                item = itemEntity("i2", 1, FramingMode.FIT),
                contents = listOf(contentEntity("ct2", "i2", "p2", 3f, 50f, 60f)),
            ),
        ),
    )

    // ---------- сборка трёх путей ----------

    private fun idleRobolectric(steps: Int = 60) {
        repeat(steps) {
            dispatcher.scheduler.advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
        }
    }

    private fun awaitBitmaps(label: String, bitmaps: () -> Map<String, ImageBitmap>, expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && bitmaps().size < expected) {
            idleRobolectric(1)
        }
        assertEquals("битмапы для $label не загрузились", expected, bitmaps().size)
    }

    private fun builderFrames(): List<ItemFrame?> {
        val repo = FakeComboRepository(listOf(combo))
        val vm = ComboBuilderViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            repo,
            FakePhotoRepository(photos),
            FakeBitmapSource(),
        )
        idleRobolectric()
        vm.refreshBitmaps(immediate = true)
        awaitBitmaps("билдера", { vm.bitmaps.value }, 2)

        val drafts = vm.state.value.items.sortedBy { it.zIndex }
        assertEquals(2, drafts.size)
        return drafts.map { vm.frameFor(it) }
    }

    private fun viewerFrames(): List<ItemFrame?> {
        val vm = ComboViewerViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            FakeComboRepository(listOf(combo)),
            FakePhotoRepository(photos),
            FakeBitmapSource(),
        )
        awaitBitmaps("плеера", { vm.bitmaps.value }, 2)

        val byId = combo.items.associateBy { it.item.id }
        return listOf("i1", "i2").map { id ->
            vm.frameFor(byId.getValue(id).item, byId.getValue(id).contents)
        }
    }

    private fun listFrames(): List<ItemFrame?> {
        val vm = ComboListViewModel(
            FakeComboRepository(listOf(combo)),
            FakePhotoRepository(photos),
            FakeBitmapSource(),
        )
        idleRobolectric()
        vm.ensureAssetsFor(combo)
        awaitBitmaps("списка", { vm.state.value.images }, 2)

        val frames = vm.framesFor(combo)
        assertEquals(2, frames.size)
        return frames
    }

    // ---------- сам тест ----------

    private fun assertSameDrawData(what: String, expected: ItemFrame, actual: ItemFrame) {
        assertEquals("$what: border", expected.border, actual.border)
        assertEquals("$what: fillColor", expected.fillColor, actual.fillColor)
        assertEquals("$what: incoming.srcW", expected.incoming.srcW, actual.incoming.srcW)
        assertEquals("$what: incoming.srcH", expected.incoming.srcH, actual.incoming.srcH)
        assertEquals("$what: incoming.scale", expected.incoming.scale, actual.incoming.scale, 1e-6f)
        assertEquals("$what: incoming.pan.x", expected.incoming.pan.x, actual.incoming.pan.x, 1e-6f)
        assertEquals("$what: incoming.pan.y", expected.incoming.pan.y, actual.incoming.pan.y, 1e-6f)
    }

    @Test
    fun `all three paths produce the same draw data for free and fit frames`() {
        val b = builderFrames()
        val v = viewerFrames()
        val l = listFrames()

        for (i in 0..1) {
            val bf = b[i]
            val vf = v[i]
            val lf = l[i]
            assertNotNull("билдер: кадр $i", bf)
            assertNotNull("плеер: кадр $i", vf)
            assertNotNull("список: кадр $i", lf)

            assertSameDrawData("плеер vs билдер [$i]", bf!!, vf!!)
            assertSameDrawData("список vs билдер [$i]", bf, lf!!)
        }
    }

    /**
     * Содержательная часть паритета: FREE обязан отдавать ХРАНИМЫЕ значения, FIT —
     * ВЫЧИСЛЕННЫЕ (вписывание по ограничивающей стороне, pan = 0). Без этой проверки
     * паритет проходил бы и при «все три пути одинаково всё игнорируют».
     */
    @Test
    fun `free frame keeps stored transform and fit frame is recomputed`() {
        val frames = builderFrames()

        val free = frames[0]!!
        assertEquals("FREE: хранимый scale", 0.9f, free.incoming.scale, 1e-6f)
        assertEquals("FREE: хранимый pan.x", 12f, free.incoming.pan.x, 1e-6f)
        assertEquals("FREE: хранимый pan.y", -7f, free.incoming.pan.y, 1e-6f)

        val fitted = frames[1]!!
        // Рамка 800x450, фото 900x900 -> ограничивает ВЫСОТА: 450/900 (800/900 = 0.89
        // было бы «заполнить по ширине», то есть cover, а не вписывание).
        assertEquals("FIT: вписанный scale по высоте", 450f / 900f, fitted.incoming.scale, 1e-6f)
        assertEquals("FIT: pan всегда нулевой", 0f, fitted.incoming.pan.x, 0f)
        assertEquals(0f, fitted.incoming.pan.y, 0f)
        assertEquals("fillColor берётся из avgColor ВХОДЯЩЕГО фото", Color(blue), fitted.fillColor)
        assertEquals(Offset.Zero, fitted.incoming.pan)
    }

    /** Без анимации все три пути обязаны давать статичный кадр (без transition/outgoing). */
    @Test
    fun `without animation all three paths produce static frames`() {
        val b = builderFrames()
        val v = viewerFrames()
        val l = listFrames()

        for (i in 0..1) {
            assertEquals("билдер [$i]: transition", null, b[i]!!.transition)
            assertEquals("билдер [$i]: outgoing", null, b[i]!!.outgoing)
            assertEquals("плеер [$i]: transition", null, v[i]!!.transition)
            assertEquals("плеер [$i]: outgoing", null, v[i]!!.outgoing)
            assertEquals("список [$i]: transition", null, l[i]!!.transition)
            assertEquals("список [$i]: outgoing", null, l[i]!!.outgoing)
        }
    }
}
