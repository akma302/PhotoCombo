package com.combo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.core.render.ItemFrame
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.StubBitmapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * ПАРИТЕТ кадра рамки между двумя путями отрисовки: редактор (`ComboBuilderViewModel.frameFor`)
 * и превью списка (`ComboListViewModel.framesFor`).
 *
 * Оба собирают кадр через общую фабрику `buildItemFrame`, но у каждого свои адаптеры
 * входных данных (драфты или Room-сущности). Тест гарантирует, что при одинаковом комбо
 * они отдают одинаковые данные для рисования: иначе картинка в редакторе и списке
 * разойдётся (нарушится WYSIWYG).
 *
 * Сравнивается только то, что влияет на рисование: `border`, `fillColor` и
 * `content.{srcW, srcH, scale, pan}`. Сами изображения `image` не сравниваются: пути
 * декодируют битмапы с разным `requiredPx`, и это политика загрузки, а не сборки кадра.
 */
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
        background = BackgroundSpec.Auto(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun itemEntity(
        id: String,
        z: Int,
        framing: FramingMode,
        photoId: String,
        scale: Float,
        panX: Float,
        panY: Float,
    ) = ComboItemEntity(
        id = id,
        comboId = "c1",
        zIndex = z,
        centerX = if (id == "i1") 400f else 800f,
        centerY = if (id == "i1") 450f else 675f,
        width = 800f,
        height = if (id == "i1") 900f else 450f,
        framingMode = framing.name,
        photoId = photoId,
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
            itemEntity("i1", 0, FramingMode.FREE, "p1", 0.9f, 12f, -7f),
            itemEntity("i2", 1, FramingMode.FIT, "p2", 3f, 50f, 60f),
        ),
    )

    // ---------- сборка двух путей ----------

    /** Битмапы грузятся в реальном `Dispatchers.IO`: ждём, прокручивая очередь теста. */
    private fun awaitBitmaps(label: String, bitmaps: () -> Map<String, ImageBitmap>, expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && bitmaps().size < expected) {
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(5)
        }
        assertEquals("битмапы для $label не загрузились", expected, bitmaps().size)
    }

    private var nextId = 0

    private fun builderFrames(): List<ItemFrame?> {
        val repo = FakeComboRepository(listOf(combo))
        val vm = ComboBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo = repo,
            photoRepo = FakePhotoRepository(photos),
            bitmapSource = StubBitmapSource(),
            ids = IdGenerator { "gen-${nextId++}" },
            clock = WallClock { 1_000L },
            ioDispatcher = Dispatchers.IO,
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.refreshBitmaps(immediate = true)
        awaitBitmaps("билдера", { vm.bitmaps.value }, 2)

        val drafts = vm.state.value.items.sortedBy { it.zIndex }
        assertEquals(2, drafts.size)
        return drafts.map { vm.frameFor(it) }
    }

    private fun listFrames(): List<ItemFrame?> {
        val vm = ComboListViewModel(
            FakeComboRepository(listOf(combo)),
            FakePhotoRepository(photos),
            StubBitmapSource(),
            settingsRepository = com.combo.ui.FakeGallerySettingsRepository(),
        )
        dispatcher.scheduler.advanceUntilIdle()
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
        assertEquals("$what: content.srcW", expected.content.srcW, actual.content.srcW)
        assertEquals("$what: content.srcH", expected.content.srcH, actual.content.srcH)
        assertEquals("$what: content.scale", expected.content.scale, actual.content.scale, 1e-6f)
        assertEquals("$what: content.pan.x", expected.content.pan.x, actual.content.pan.x, 1e-6f)
        assertEquals("$what: content.pan.y", expected.content.pan.y, actual.content.pan.y, 1e-6f)
    }

    @Test
    fun `both paths produce the same draw data for free and fit frames`() {
        val b = builderFrames()
        val l = listFrames()

        for (i in 0..1) {
            val bf = b[i]
            val lf = l[i]
            assertNotNull("билдер: кадр $i", bf)
            assertNotNull("список: кадр $i", lf)
            assertSameDrawData("список vs билдер [$i]", bf!!, lf!!)
        }
    }

    /**
     * Содержательная часть паритета: FREE обязан отдавать ХРАНИМЫЕ значения, FIT —
     * ВЫЧИСЛЕННЫЕ (вписывание по ограничивающей стороне, pan = 0). Без этой проверки
     * паритет проходил бы и при «оба пути одинаково всё игнорируют».
     */
    @Test
    fun `free frame keeps stored transform and fit frame is recomputed`() {
        val frames = builderFrames()

        val free = frames[0]!!
        assertEquals("FREE: хранимый scale", 0.9f, free.content.scale, 1e-6f)
        assertEquals("FREE: хранимый pan.x", 12f, free.content.pan.x, 1e-6f)
        assertEquals("FREE: хранимый pan.y", -7f, free.content.pan.y, 1e-6f)

        val fitted = frames[1]!!
        // Рамка 800x450, фото 900x900 -> ограничивает ВЫСОТА: 450/900 (800/900 = 0.89
        // было бы «заполнить по ширине», то есть cover, а не вписывание).
        assertEquals("FIT: вписанный scale по высоте", 450f / 900f, fitted.content.scale, 1e-6f)
        assertEquals("FIT: pan всегда нулевой", 0f, fitted.content.pan.x, 0f)
        assertEquals(0f, fitted.content.pan.y, 0f)
        assertEquals("fillColor берётся из avgColor фото рамки", Color(blue), fitted.fillColor)
        assertEquals(Offset.Zero, fitted.content.pan)
    }
}
