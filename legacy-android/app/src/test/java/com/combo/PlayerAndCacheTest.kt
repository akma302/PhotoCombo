package com.combo

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.combo.core.model.Direction
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import com.combo.domain.image.LruBitmapSource
import com.combo.feature.combo_viewer.ComboViewerViewModel
import com.combo.feature.combo_viewer.hitTestItem
import com.combo.ui.FakeBitmapSource
import com.combo.ui.FakeComboRepository
import com.combo.ui.FakePhotoRepository
import com.combo.ui.comboContentEntity
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import com.combo.ui.photoEntity
import java.time.Duration
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Кэш битмапов и плеер: `LruBitmapSource` (единицы `sizeOf`), автосмена по таймеру и
 * hit-test при перекрытии рамок.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerAndCacheTest {

    private val STEP_MS = 10L

    // ---------- LruBitmapSource: sizeOf в КБ ----------

    @Test
    fun `cache entry size is reported in kilobytes`() {
        val source = LruBitmapSource(
            object : PhotoDao {
                override fun all() = emptyFlow<List<PhotoEntity>>()
                override suspend fun get(id: String): PhotoEntity? = null
                override suspend fun findByHash(hash: String): PhotoEntity? = null
                override suspend fun insert(p: PhotoEntity) = Unit
                override suspend fun delete(p: PhotoEntity) = Unit
            },
            PhotoStorage(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ),
        )
        // 512x512 ARGB8888 = 1 МиБ -> 1024 КБ
        assertEquals(
            1024,
            source.cacheSizeKb(
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).asImageBitmap(),
            ),
        )
        // Крошечный битмап не должен давать 0 (тогда запись не вытесняется никогда)
        assertTrue(
            source.cacheSizeKb(
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap(),
            ) >= 1,
        )

        // Главное: сам LruCache обязан считать записи в тех же единицах, что и ёмкость.
        // При sizeOf в байтах 1 МиБ «весит» 1 048 576 при потолке ~1 048 576 и вытесняется
        // немедленно (cache.size() == 0).
        source.cache.put(
            "probe",
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).asImageBitmap(),
        )
        assertEquals("запись должна остаться в кэше и весить 1024 КБ", 1024, source.cache.size())
    }

    // ---------- Плеер C: hit-test отдаёт верхнюю рамку ----------

    @Test
    fun `hit test picks topmost overlapping frame`() {
        val combo = ComboWithItems(
            combo = comboEntity("c1"),
            items = listOf(
                // Порядок как из репозитория: по возрастанию zIndex.
                ItemWithContents(comboItemEntity("bottom", "c1", 0), emptyList()),
                ItemWithContents(comboItemEntity("middle", "c1", 1), emptyList()),
                ItemWithContents(comboItemEntity("top", "c1", 2), emptyList()),
            ),
        )
        // Один пункт на весь холст 1600x900 -> попадают все три рамки.
        val inside = Offset(800f, 450f)
        assertEquals("top", hitTestItem(combo, inside))
        // Вне холста — ничего.
        assertEquals(null, hitTestItem(combo, Offset(-10f, -10f)))
    }

    // ---------- Плеер B: автосмена по таймеру реально срабатывает ----------

    @Test
    fun `auto timer switches photo without manual swipe`() {
        val item = comboItemEntity("i1", "c1", 0).copy(
            switchMode = "AUTO",
            // Длинный интервал: за 400 мс виртуального времени срабатывает РОВНО один раз,
            // иначе автосмена успеет вернуться на 0 и проверка станет бессмысленной.
            autoIntervalMs = 3000L,
            manualTransition = "FADE",
            autoTransition = "FADE",
        )
        val contents = listOf(
            comboContentEntity("ct1", "i1", "p1"),
            comboContentEntity("ct2", "i1", "p2").copy(orderIndex = 1),
        )
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "Auto"),
                    items = listOf(ItemWithContents(item, contents)),
                ),
            ),
        )
        val photos = listOf(photoEntity("p1"), photoEntity("p2"))
        val vm = ComboViewerViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo,
            FakePhotoRepository(photos),
            FakeBitmapSource(),
        )

        // Тикер обязан пережить «момент тишины» (due() пуст, анимаций нет) и дождаться
        // первого автосрабатывания: интервал 3 с, прокручиваем 3.5 с — так срабатывание
        // ровно одно (второе было бы на 6 с).
        idleMain(vm, 3_500)

        assertEquals("автосмена должна была сработать", 1, vm.animator.currentIndex("i1"))
    }

    // ---------- A1: тикер спит, но ручной свайп обязан его разбудить ----------

    private fun player(
        switchMode: String,
        intervals: Long = 3000L,
        contents: List<com.combo.data.ComboItemContentEntity>,
    ): ComboViewerViewModel {
        val item = comboItemEntity("i1", "c1", 0).copy(
            switchMode = switchMode,
            autoIntervalMs = intervals,
            manualTransition = "SLIDE",
            autoTransition = "FADE",
        )
        val comboRepo = FakeComboRepository(
            listOf(
                ComboWithItems(
                    combo = comboEntity("c1", "P"),
                    items = listOf(ItemWithContents(item, contents)),
                ),
            ),
        )
        val vm = ComboViewerViewModel(
            SavedStateHandle(mapOf("comboId" to "c1")),
            comboRepo,
            FakePhotoRepository(listOf(photoEntity("p1"), photoEntity("p2"))),
            FakeBitmapSource(),
        )
        idleMain(vm, 200)
        return vm
    }

    /**
     * Прокручивает виртуальное время Robolectric ровно на [virtualMs]. Важно: цикл
     * ограничен числом итераций, а не реальным временем — иначе «крутим пока не выйдет»
     * прокручивает минуты виртуальных миллисекунд за доли секунды.
     */
    private fun idleMain(vm: ComboViewerViewModel, virtualMs: Long) {
        repeat((virtualMs / STEP_MS).toInt().coerceAtLeast(1)) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
        }
    }

    @Test
    fun `manual swipe wakes sleeping ticker and repaints immediately`() {
        val vm = player(
            "AUTO",
            contents = listOf(
                comboContentEntity("ct1", "i1", "p1"),
                comboContentEntity("ct2", "i1", "p2").copy(orderIndex = 1),
            ),
        )
        val revisionBefore = vm.state.value.revision
        assertFalse("предусловие: анимаций нет", vm.animator.hasActive())

        // Тикер сейчас спит до ближайшего таймера (3 с). Свайп должен разбудить его.
        vm.manualSwitch("i1", Direction.UP)
        idleMain(vm, 100)

        assertTrue("анимация перехода должна начаться сразу", vm.animator.hasActive())
        assertTrue(
            "revision должен вырасти без ожидания автотаймера",
            vm.state.value.revision > revisionBefore,
        )
    }

    @Test
    fun `idle player without auto frames does not repaint`() {
        val vm = player(
            "MANUAL",
            contents = listOf(
                comboContentEntity("ct1", "i1", "p1"),
                comboContentEntity("ct2", "i1", "p2").copy(orderIndex = 1),
            ),
        )
        val revisionBefore = vm.state.value.revision
        idleMain(vm, 300)
        assertEquals(
            "B1: без анимаций и таймеров перерисовок быть не должно",
            revisionBefore,
            vm.state.value.revision,
        )
    }

    /**
     * Тот же инвариант, но на AUTO-камке, где тикер НЕ выходит сразу (у MANUAL-комбо
     * `bump()` мог быть вызван на выходе из цикла, и проверка ничего не значила).
     * Условие `animator.hasActive() || due.isNotEmpty()` вокруг `bump()` обязано быть
     * единственным, что держит перерисовку: снять его — и падают оба теста ниже.
     */
    @Test
    fun `sleeping ticker with auto frame does not repaint until the timer fires`() {
        val vm = player(
            "AUTO",
            contents = listOf(
                comboContentEntity("ct1", "i1", "p1"),
                comboContentEntity("ct2", "i1", "p2").copy(orderIndex = 1),
            ),
        )
        // player() уже прокрутил 200 мс, до первого автосрабатывания (3 с) остаётся 2.8 с.
        val revisionBefore = vm.state.value.revision
        assertTrue("предусловие: таймер запланирован", vm.scheduler.hasRunning())

        idleMain(vm, 2_500)
        assertEquals(
            "тикер спит до ближайшего таймера — перерисовок быть не должно",
            revisionBefore,
            vm.state.value.revision,
        )
        assertEquals(0, vm.animator.currentIndex("i1"))

        idleMain(vm, 1_000)
        assertEquals("таймер должен был сработать", 1, vm.animator.currentIndex("i1"))
        assertTrue(
            "срабатывание таймера обязано перерисовать",
            vm.state.value.revision > revisionBefore,
        )
    }

    /**
     * Сон сам по себе проверку не ловит: тело цикла тикера за итерацию сна просто не
     * выполняется, поэтому безусловный `bump()` не дал бы лишних перерисовок, пока тикер
     * спит. Настоящий носитель регрессии — ПРОБУЖДЕНИЕ: `resume()` будит тикер через
     * wake-канал, и вот тут безусловный `bump()` перерисовал бы кадр на каждый чих.
     */
    @Test
    fun `waking the sleeping ticker without a due timer does not repaint`() {
        val vm = player(
            "AUTO",
            contents = listOf(
                comboContentEntity("ct1", "i1", "p1"),
                comboContentEntity("ct2", "i1", "p2").copy(orderIndex = 1),
            ),
        )
        val revisionBefore = vm.state.value.revision
        assertFalse("предусловие: анимаций нет", vm.animator.hasActive())

        vm.resume()
        idleMain(vm, 300)

        assertEquals(
            "пробуждение без «пора» и без анимации не должно перерисовывать",
            revisionBefore,
            vm.state.value.revision,
        )
        assertEquals("и переключать фото тоже не должно", 0, vm.animator.currentIndex("i1"))
    }

    @Test
    fun `auto frame with single photo is not scheduled`() {
        val vm = player(
            "AUTO",
            contents = listOf(comboContentEntity("ct1", "i1", "p1")),
        )
        assertFalse(
            "переключаться нечем — слот создавать нельзя, иначе тикер «вечно пора»",
            vm.scheduler.hasRunning(),
        )
        val revisionBefore = vm.state.value.revision
        idleMain(vm, 300)
        assertEquals(revisionBefore, vm.state.value.revision)
    }
}
