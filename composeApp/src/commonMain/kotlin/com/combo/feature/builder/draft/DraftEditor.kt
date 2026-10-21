package com.combo.feature.builder.draft

import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemDraft
import com.combo.core.render.LayoutPresets
import com.combo.core.render.effectiveFraming
import com.combo.data.PhotoEntity
import com.combo.data.srcSize
import kotlin.math.min

/**
 * Результат [DraftEditor.newComboDrafts]: рамки и выделение.
 *
 * Отдельный класс, а не `List<ItemDraft>`, потому что вызывающему нужны оба значения сразу:
 * без `selectedItemId` кнопки тулбара остаются выключенными (все они требуют выделенной
 * рамки), и собирать это в вызывающем значило бы переписать здесь же половину логики.
 */
internal data class NewComboDrafts(
    val items: List<ItemDraft>,
    val selectedItemId: String?,
)

/**
 * Результат [DraftEditor.addItems]: новый список рамок (прежние + добавленные) и id
 * добавленных в порядке добавления. Вызывающему нужны оба: список публикуется в состояние,
 * а по id выставляется выделение.
 */
internal data class NewItems(
    val items: List<ItemDraft>,
    val addedIds: List<String>,
)

/**
 * Чистые операции над драфтами редактора: добавить, переставить и удалить рамки, заменить
 * фото рамки, применить пресет, сменить режим рамки, собрать новое комбо.
 *
 * Без Android, без ViewModel, без coroutine — всё проверяется обычным JVM-тестом на
 * голых [ItemDraft]. Перенос сюда убрал из ViewModel смесь правил и мутаций, где
 * «посчитать, что именно меняем» и «изменить» было одним выражением.
 *
 * В рамке всегда ровно одно фото: пустых рамок нет, поэтому рамка создаётся только вместе
 * с фото ([addItems], [newComboDrafts]).
 *
 * Идентификаторы новых рамок выдаёт `newId` — инжектированный генератор вызывающего:
 * в приложении это общий [com.combo.core.platform.IdGenerator], в тестах — счётчик.
 * Собственного источника случайности у редактора нет, иначе тесты комбо получали бы
 * непредсказуемые id.
 *
 * ГЛАВНОЕ ПРАВИЛО — ГДЕ ПРОХОДИТ ГРАНИЦА ИЗМЕНЕНИЯ. Каждая мутирующая операция сначала
 * проверяет ВСЕ предусловия и только потом делает первую запись в драфт; точка, в которую
 * вызывающий обязан поставить `checkpoint`, передаётся лямбдой [beforeApply] (или
 * определяется возвратом `null`/`false`, если менять нечего). Иначе получился бы пустой
 * undo-шаг — лишний Undo, который ничего не отменяет, и `isDirty` у комбо, которое
 * пользователь не менял.
 *
 * Побочный эффект есть ровно один и он по замыслу: [reorder] и [deleteItem]
 * перенумеровывают `zIndex` у переданных объектов [ItemDraft]. Драфты мутабельны и
 * лежат в состоянии по ссылке, копировать их — значит разорвать эту связь; поэтому
 * перенумерация делается на месте, а порядок элементов списка возвращается новым
 * списком (`toList()`), чтобы вызывающий опубликовал его в состоянии.
 */
internal object DraftEditor {

    /**
     * Поменять местами рамку [from] с соседней и вернуть новый упорядоченный список
     * (`null` — переставлять некуда).
     *
     * Порядок слоёв — это [ItemDraft.zIndex], а он перенумеровывается подряд: `toEntities`
     * пишет индекс по позиции в списке, значит список и `zIndex` обязаны совпадать, иначе
     * после сохранения и перезагрузки порядок слоёв тихо поменяется местами.
     *
     * Возвращается НОВЫЙ список: состояние хранит `List<ItemDraft>`, и перестановка должна
     * публиковаться новым объектом, иначе подписчики не увидят изменения.
     */
    fun reorder(
        items: List<ItemDraft>,
        from: Int,
        forward: Boolean,
        beforeApply: () -> Unit = {},
    ): List<ItemDraft>? {
        if (from !in items.indices) return null
        val swapWith = if (forward) from + 1 else from - 1
        if (swapWith !in items.indices) return null

        beforeApply()
        val list = items.toMutableList()
        val tmp = list[from]
        list[from] = list[swapWith]
        list[swapWith] = tmp
        list.forEachIndexed { i, d -> d.zIndex = i }
        return list.toList()
    }

    /**
     * Удалить рамку и вернуть оставшиеся с перенумерованным `zIndex` (`null` — нечего
     * удалять).
     *
     * Последнюю рамку удалить нельзя: комбо без рамок не рисуется, а пользователь без
     * рамки остаётся без единого доступного действия. Неизвестный `id` отвергается ДО
     * [beforeApply] — иначе удаление несуществующей рамки оставило бы пустой undo-шаг.
     *
     * Вызывающий обязан сам снять выделение: `selectedItemId` у него, а не здесь.
     */
    fun deleteItem(
        items: List<ItemDraft>,
        id: String,
        beforeApply: () -> Unit = {},
    ): List<ItemDraft>? {
        if (items.size <= 1) return null
        if (items.none { it.id == id }) return null

        beforeApply()
        val list = items.filter { it.id != id }
        list.forEachIndexed { i, d -> d.zIndex = i }
        return list.toList()
    }

    /**
     * Добавить рамки по одной на каждое фото из [photos] (`null` — добавлять нечего).
     *
     * Лимит [Const.MAX_ITEMS] действует на комбо целиком: лишние фото отбрасываются с
     * конца, и «создать комбо» и «добавить рамки» ограничиваются одинаково. Повторы id
     * внутри одного запроса схлопываются (фото, выбранное дважды, даёт одну рамку); то же
     * фото в разных рамках допустимо.
     *
     * Размер новой рамки — половина холста: она заметно меньше холста (видна целиком и не
     * перекрывает соседние), но достаточно велика для работы жестами. Фото заполняет рамку
     * ([LayoutPresets.fill]). Первая рамка стоит по центру, каждая следующая сдвинута
     * каскадом (на 4% меньшей стороны холста), чтобы рамки одного запроса не слипались в
     * одну; центр зажат на холсте, как и у перетаскивания.
     *
     * `zIndex` — позиция в списке: список новый (`items + added`), прежние рамки не
     * трогаются, а порядок слоёв остаётся сплошным, как его пишет `toEntities`.
     *
     * @param beforeApply единственное правильное место для `checkpointNow()` — после
     *   проверки лимита и до создания рамок; при исчерпанном лимите или пустом запросе не
     *   вызывается вовсе, поэтому пустого undo-шага не появляется.
     */
    fun addItems(
        items: List<ItemDraft>,
        photos: List<PhotoEntity>,
        canvasW: Float,
        canvasH: Float,
        beforeApply: () -> Unit = {},
        newId: () -> String,
    ): NewItems? {
        val room = Const.MAX_ITEMS - items.size
        if (room <= 0) return null
        val plan = photos.distinctBy { it.id }.take(room)
        if (plan.isEmpty()) return null

        beforeApply()
        val step = 0.04f * min(canvasW, canvasH)
        val added = plan.mapIndexed { k, photo ->
            val draft = ItemDraft(
                id = newId(),
                zIndex = items.size + k,
                centerX = (canvasW / 2f + k * step).coerceIn(0f, canvasW),
                centerY = (canvasH / 2f + k * step).coerceIn(0f, canvasH),
                width = canvasW / 2f,
                height = canvasH / 2f,
                photoId = photo.id,
                contentScale = 1f,
                panX = 0f,
                panY = 0f,
            )
            LayoutPresets.fill(draft, draft.border(), photo.srcSize())
            draft
        }
        return NewItems(items = items + added, addedIds = added.map { it.id })
    }

    /**
     * Заменить фото рамки на [photo] (`false` — менять нечего: то же фото).
     *
     * Позиция, размер, поворот и режим рамки сохраняются; положение нового фото внутри
     * рамки сбрасывается на «заполнить» ([LayoutPresets.fill]): масштаб и сдвиг прежнего
     * фото к новому не подходят (другие размеры и пропорции).
     *
     * @param src размер нового фото; вызывающий берёт его из кэша метаданных, а при
     *   отсутствии — из [photo] ([PhotoEntity.srcSize]).
     */
    fun replacePhoto(
        item: ItemDraft,
        photo: PhotoEntity,
        src: Size = photo.srcSize(),
        beforeApply: () -> Unit = {},
    ): Boolean {
        if (item.photoId == photo.id) return false

        beforeApply()
        item.photoId = photo.id
        LayoutPresets.fill(item, item.border(), src)
        return true
    }

    /**
     * Применить пресет к фото рамки (`false` — нечего применять).
     *
     * Порядок обязателен и не переставляемый:
     *
     * 1. найти размер фото — без него пресет считать нечем, и неизвестное фото обязано
     *    тихо ничего не менять (иначе появится пустой undo-шаг);
     * 2. [beforeApply] — checkpoint;
     * 3. выйти из [FramingMode.FIT] с запеканием вписанных значений (см.
     *    [applyFramingMode]): пресет — это явный выбор пользователя, и он важнее
     *    блокировки FIT;
     * 4. применить пресет.
     *
     * @param srcSizeOf размер фото по id.
     * @param photoOf метаданные фото — нужны запеканию на шаге 3.
     */
    fun applyPreset(
        item: ItemDraft,
        preset: FitPreset,
        srcSizeOf: (String) -> Size?,
        photoOf: (String) -> PhotoEntity?,
        beforeApply: () -> Unit = {},
    ): Boolean {
        val src = srcSizeOf(item.photoId) ?: return false

        beforeApply()
        applyFramingMode(item, FramingMode.FREE, photoOf)
        when (preset) {
            FitPreset.FIT -> LayoutPresets.fit(item, item.border(), src)
            FitPreset.FILL -> LayoutPresets.fill(item, item.border(), src)
        }
        return true
    }

    /**
     * Сменить режим рамки, попутно «запекая» текущую картинку в хранимые значения.
     *
     * Запекание нужно ТОЛЬКО на выходе из [FramingMode.FIT]: сохранённые
     * `contentScale`/`panX`/`panY` пока не показывались, и без запекания фото после
     * переключения скакнуло бы к давним значениям. На входе в FIT сохранённые значения,
     * наоборот, не трогаем — иначе режим «переписал» бы то, к чему пользователь вернётся
     * кнопкой AutoFit.
     *
     * Расчёт запекания идёт через [effectiveFraming], то есть ровно той же формулой,
     * которой рисует FIT: «запечённое» обязано совпасть с тем, что было на экране.
     *
     * @return изменился ли режим рамки (`false` — был уже равен `target`, и ничего не
     *   изменилось). Вызывающий решает по этому признаку, ставить ли checkpoint:
     *   [applyPreset] уже поставил его сам, поэтому здесь лямбды нет.
     */
    fun applyFramingMode(
        item: ItemDraft,
        target: FramingMode,
        photoOf: (String) -> PhotoEntity?,
    ): Boolean {
        if (item.framingMode == FramingMode.FIT && target != FramingMode.FIT) {
            val photo = photoOf(item.photoId)
            if (photo != null) {
                val f = effectiveFraming(
                    FramingMode.FIT, item.border(), photo.width, photo.height,
                    item.contentScale, item.panX, item.panY,
                )
                item.contentScale = f.scale
                item.panX = f.panX
                item.panY = f.panY
            }
        }
        val changed = item.framingMode != target
        item.framingMode = target
        return changed
    }

    /**
     * Рамки нового комбо из набора фото: авто-раскладка ячейками, в каждой по одному
     * фото, заполняющему рамку.
     *
     * Больше [Const.MAX_ITEMS] рамок комбо всё равно не создать, поэтому лимит живёт
     * здесь, а не в вызывающем: иначе «создать комбо» и «добавить рамки» ограничивались бы
     * по-разному, и лимит можно было бы обойти одним из двух входов.
     *
     * ПУСТОЙ НАБОР — НЕ ТУПИК. Комбо создаётся без рамок: пустых рамок в модели нет, а
     * кнопка «+Frame» доступна и без выделения, так что пользователь начинает с неё.
     */
    fun newComboDrafts(
        photoMetas: List<PhotoEntity>,
        canvasW: Float,
        canvasH: Float,
        newId: () -> String,
    ): NewComboDrafts {
        val photos = photoMetas.take(Const.MAX_ITEMS)
        if (photos.isEmpty()) return NewComboDrafts(items = emptyList(), selectedItemId = null)
        val items = LayoutPresets.cellBorders(photos.size, canvasW, canvasH).mapIndexed { i, b ->
            val p = photos[i]
            ItemDraft(
                id = newId(),
                zIndex = i,
                centerX = b.centerX,
                centerY = b.centerY,
                width = b.width,
                height = b.height,
                photoId = p.id,
                contentScale = 1f,
                panX = 0f,
                panY = 0f,
            ).also { LayoutPresets.fill(it, b, p.srcSize()) }
        }
        return NewComboDrafts(items = items, selectedItemId = items.first().id)
    }
}
