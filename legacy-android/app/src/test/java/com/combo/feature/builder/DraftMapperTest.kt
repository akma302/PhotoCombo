package com.combo.feature.builder

import com.combo.core.model.FramingMode
import com.combo.core.model.SwitchMode
import com.combo.core.model.TransitionType
import com.combo.core.render.ContentDraft
import com.combo.core.render.ItemDraft
import com.combo.data.ComboWithItems
import com.combo.data.ItemWithContents
import com.combo.feature.builder.draft.ContentSnap
import com.combo.feature.builder.draft.ItemSnap
import com.combo.feature.builder.draft.toDraft
import com.combo.feature.builder.draft.toDrafts
import com.combo.feature.builder.draft.toEntities
import com.combo.feature.builder.draft.toSnap
import com.combo.ui.comboContentEntity
import com.combo.ui.comboEntity
import com.combo.ui.comboItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Маппер рамок/фото между Room-сущностями, драфтами и снапшотами undo.
 *
 * Три вещи, которые маппер обязан сохранять и которые не видно по типам:
 *
 * 1. круг «драфт → снапшот → драфт» и «драфт → сущности → драфт» не теряет и не подменяет
 *    поля; сверка идёт по всем полям драфта (reflection'ом, не списком в тесте), а набор
 *    полей драфта и снапшота проверяется отдельно;
 * 2. `zIndex` и в загрузке, и в записи берётся по позиции, а не из драфта: дыры в порядке
 *    слоёв после удаления/перестановки рамок не должны ни выживать, ни появляться;
 * 3. у ItemDraft нет structural equals, поэтому равенство драфтов проверяется полями.
 */
class DraftMapperTest {

    @Test
    fun `draft survives a snapshot round trip`() {
        val before = fullDraft("b", zIndex = 0)

        val after = before.toSnap().toDraft()

        assertDraftEquals(before, after, "снапшот")
    }

    /**
     * Обратный путь через базу. Порядок элементов списка выбран так, чтобы `zIndex` драфта
     * совпадал с его позицией: `toEntities()` пишет индекс в список, а `toDrafts()` при
     * загрузке переприсваивает индекс заново. Проверка zIndex «через базу» поэтому имеет
     * смысл только на согласованных значениях — расхождение ловится отдельным тестом ниже.
     */
    @Test
    fun `draft survives an entity round trip`() {
        val before = listOf(
            fullDraft("a", zIndex = 0),
            fullDraft("b", zIndex = 1, rotation = -12.5f),
        )

        val (itemEntities, contentEntities) = before.toEntities("c1")
        val reloaded = ComboWithItems(
            combo = comboEntity("c1"),
            items = itemEntities.map { item ->
                ItemWithContents(item, contentEntities.filter { it.itemId == item.id })
            },
        ).toDrafts()

        assertField("число рамок", before.size, reloaded.size)
        before.zip(reloaded).forEach { (was, now) ->
            assertDraftEquals(was, now, "сущности: ${was.id}")
        }
    }

    /**
     * Защита от «добавил поле в драфт и забыл про маппер»: такое поле молча выпало бы из
     * undo (из снапшота) или из базы (из сущности), и ни один из кругов выше этого не
     * заметил бы — round-trip сравнивает только те поля, которые перечислены руками.
     * Поэтому сверяются сами наборы полей через reflection.
     */
    @Test
    fun `draft and snapshot classes declare the same fields`() {
        assertSameFields(
            what = "рамка",
            expectedCount = 13,
            draft = ItemDraft::class.java,
            snap = ItemSnap::class.java,
        )
        assertSameFields(
            what = "фото",
            expectedCount = 6,
            draft = ContentDraft::class.java,
            snap = ContentSnap::class.java,
        )
    }

    /** Порядок слоёв задаётся zIndex'ом, дыры в нём — мусор: загружаем и перенумеровываем. */
    @Test
    fun `loading renumbers z index even when stored indexes have holes`() {
        val loaded = ComboWithItems(
            combo = comboEntity("c1"),
            items = listOf(
                ItemWithContents(comboItemEntity("b", "c1", zIndex = 9), emptyList()),
                ItemWithContents(
                    comboItemEntity("a", "c1", zIndex = 5),
                    listOf(comboContentEntity("ct1", "a", "p1")),
                ),
            ),
        ).toDrafts()

        assertField("сортировка по zIndex", listOf("a", "b"), loaded.map { it.id })
        assertField("zIndex переприсвоен подряд", listOf(0, 1), loaded.map { it.zIndex })
    }

    // ---------- helpers ----------

    /**
     * Рамка, у которой НИ ОДНО поле не совпадает со значением по умолчанию: rotation (0),
     * switchMode (MANUAL), autoIntervalMs (3000), manualTransition (SLIDE), autoTransition
     * (FADE), framingMode (FREE), координаты/размер, а также scale/pan/orderIndex у трёх
     * фото внутри. Если маппер хоть одно поле потеряет или подменит дефолтом — круг это
     * покажет.
     */
    private fun fullDraft(id: String, zIndex: Int, rotation: Float = 7.25f) = ItemDraft(
        id = id,
        zIndex = zIndex,
        centerX = 321.5f,
        centerY = 654.25f,
        width = 480f,
        height = 720f,
        rotation = rotation,
        switchMode = SwitchMode.MANUAL_AUTO,
        autoIntervalMs = 5000L,
        manualTransition = TransitionType.ZOOM,
        autoTransition = TransitionType.WIPE,
        framingMode = FramingMode.FIT,
        contents = mutableListOf(
            ContentDraft("$id-ct1", "p1", 0, 2.5f, -13f, 17f),
            ContentDraft("$id-ct2", "p2", 1, 0.75f, 3f, -4f),
            ContentDraft("$id-ct3", "p3", 2, 1.25f, 0.5f, -0.5f),
        ),
    )

    /**
     * Полевое равенство драфтов: `ItemDraft` — обычный класс, Equals у него нет, а
     * round-trip теряет ровно одно поле — то, которое забыли скопировать.
     *
     * Поля сверяются ПО ИМЕНАМ, взятым reflection'ом из самого драфта, а не по списку,
     * написанному в тесте. Иначе появился бы круговой обход: добавили поле в драфт и в
     * снапшот, но забыли в маппере — и проверка молча этот случай пропускала бы, потому
     * что «знает» только старые поля.
     */
    private fun assertDraftEquals(expected: ItemDraft, actual: ItemDraft, stage: String) {
        assertFieldsEqual(stage, ItemDraft::class.java, expected, actual, skip = setOf("contents"))
        assertField("$stage: число фото", expected.contents.size, actual.contents.size)
        expected.contents.zip(actual.contents).forEachIndexed { i, (was, now) ->
            assertFieldsEqual("$stage contents[$i]", ContentDraft::class.java, was, now)
        }
    }

    private fun assertSameFields(what: String, expectedCount: Int, draft: Class<*>, snap: Class<*>) {
        val draftFields = stateFields(draft)
        val snapFields = stateFields(snap)
        assertField("набор полей $what — добавил поле в драфт и забыл про маппер?", draftFields, snapFields)
        assertField("число полей $what — reflection отдаёт не то", expectedCount, draftFields.size)
    }

    /** Сверка каждого поля по значению; состав полей берётся из [type], а не из теста. */
    private fun assertFieldsEqual(
        stage: String,
        type: Class<*>,
        expected: Any,
        actual: Any,
        skip: Set<String> = emptySet(),
    ) {
        for (name in stateFields(type).sorted().filterNot { it in skip }) {
            assertField("$stage.$name", fieldOf(type, name).get(expected), fieldOf(type, name).get(actual))
        }
    }

    /**
     * Поле по имени, доступное для чтения. У полей классов они `private`, поэтому без
     * `isAccessible` на JDK 17 чтение бросает `IllegalAccessException` — а не «молча
     * возвращает null».
     */
    private fun fieldOf(type: Class<*>, name: String) =
        type.getDeclaredField(name).apply { isAccessible = true }

    /**
     * Обёртка над JUnit: параметры типизированы как [Any?], поэтому выбирается ровно одна
     * перегрузка `assertEquals(String, Object, Object)` — иначе Int/Float/Set конкурируют
     * с вариантами «с дельтой» и без неё.
     */
    private fun assertField(field: String, expected: Any?, actual: Any?) =
        assertEquals("$field: значения разошлись", expected, actual)

    /**
     * Не-статические поля класса. `$stable` и `Companion` (если их вообще видно) —
     * служебные, компиляторские, частью состояния не являются.
     */
    private fun stateFields(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }
            .filterNot { it == "Companion" || it.startsWith('$') }
            .toSet()
}
