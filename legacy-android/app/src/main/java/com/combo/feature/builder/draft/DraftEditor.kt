package com.combo.feature.builder.draft

import androidx.compose.ui.geometry.Size
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.render.ContentDraft
import com.combo.core.render.ItemDraft
import com.combo.core.render.LayoutPresets
import com.combo.core.render.effectiveFraming
import com.combo.data.PhotoEntity
import com.combo.data.srcSize
import java.util.UUID

/**
 * Результат [DraftEditor.newComboDrafts]: рамки, начальные индексы активного фото и
 * выделение.
 *
 * Отдельный класс, а не `List<ItemDraft>`, потому что вызывающему нужно всё три сразу:
 * без `activeIndex` рамки не переключаются, без `selectedItemId` тулбар остаётся
 * нефункциональным (все его кнопки требуют выделенной рамки), и собирать это в вызывающем
 * значило бы переписать здесь же половину логики.
 */
internal data class NewComboDrafts(
    val items: List<ItemDraft>,
    val activeIndex: Map<String, Int>,
    val selectedItemId: String?,
)

/**
 * Чистые операции над драфтами редактора: добавить/убрать фото, переставить и удалить
 * рамки, применить пресет, сменить режим рамки, собрать новое комбо.
 *
 * Без Android, без ViewModel, без coroutine — всё проверяется обычным JVM-тестом на
 * голых [ItemDraft]. Перенос сюда убрал из ViewModel смесь правил и мутаций, где
 * «посчитать, что именно меняем» и «изменить» было одним выражением.
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
     * Добавить фото в рамку: отсечь дубликаты, уложиться в [Const.MAX_CONTENTS_PER_ITEM]
     * и заполнить новое фото по [LayoutPresets.fill].
     *
     * Порядок фильтров важен: сначала отбрасываются фото, которые уже есть в рамке, и
     * только потом запрос режется по свободному месту. Наоборот было бы хуже: уже
     * добавленные фото занимали бы место в лимите, и в рамку попало бы меньше, чем помещается.
     *
     * @param meta метаданные запрошенных фото; без них фото нельзя ни показать, ни
     *   вписать в рамку, поэтому неизвестные id молча пропускаются.
     * @param srcSizeOf размер фото по id из кэша метаданных редактора. Если в кэше фото ещё
     *   нет (рамка создана «с нуля», а метаданные пришли из библиотеки), берётся размер из
     *   [meta] — [PhotoEntity.srcSize].
     * @param beforeApply ставится ровно между проверкой предусловий и первой мутацией —
     *   единственное правильное место для `checkpointNow()`.
     * @return добавленные контенты в порядке добавления; пустой список означает «ничего не
     *   изменилось» и [beforeApply] НЕ вызывался.
     */
    fun addContents(
        item: ItemDraft,
        requestedPhotoIds: List<String>,
        meta: Map<String, PhotoEntity>,
        srcSizeOf: (String) -> Size?,
        beforeApply: () -> Unit = {},
    ): List<ContentDraft> {
        val capacity = Const.MAX_CONTENTS_PER_ITEM - item.contents.size
        if (capacity <= 0) return emptyList()
        val existing = item.contents.map { it.photoId }.toSet()
        val requested = requestedPhotoIds.filter { it !in existing }.take(capacity)
        if (requested.isEmpty()) return emptyList()
        // Повторы id внутри ОДНОГО запроса схлопываются уже ПОСЛЕ отсечения по лимиту:
        // запрос [p1, p1, p2] при двух свободных местах добавит только p1.
        val plan = requested
            .mapNotNull { pid -> meta[pid]?.let { pid to it } }
            .distinctBy { it.first }
        if (plan.isEmpty()) return emptyList()

        beforeApply()
        return plan.map { (pid, photo) ->
            val src = srcSizeOf(pid) ?: photo.srcSize()
            ContentDraft(
                id = UUID.randomUUID().toString(),
                photoId = pid,
                orderIndex = item.contents.size,
                contentScale = 1f,
                panX = 0f,
                panY = 0f,
            ).also {
                LayoutPresets.fill(it, item.border(), src)
                item.contents.add(it)
            }
        }
    }

    /**
     * Убрать активное фото из рамки и вернуть НОВЫЙ активный индекс (`null` — ничего не
     * изменилось).
     *
     * Удалять нельзя последнее фото: рамка без фото — это уже «пустая рамка» из
     * [newComboDrafts], а не «минус одно фото». На рамке без контента то же самое, а заодно
     * не приходится зажимать индекс в диапазон `0 .. -1`, который бросил бы
     * `IllegalArgumentException`.
     *
     * Индекс считается по УЖЕ удалённому набору: активным становится предыдущее фото, а
     * если удалили последнее — то самое последнее из оставшихся. [ItemDraft.contents]
     * перенумеровывается подряд, потому что `orderIndex` — это и порядок показа, и то,
     * что `toEntities()` пишет в базу.
     *
     * @param activeIndex зажимается в границы набора: вызывающий хранит индекс в состоянии
     *   отдельно от драфтов, и после `restore` он вправе оказаться устаревшим.
     */
    fun removeActiveContent(
        item: ItemDraft,
        activeIndex: Int,
        beforeApply: () -> Unit = {},
    ): Int? {
        if (item.contents.size <= 1) return null
        val idx = activeIndex.coerceIn(0, item.contents.size - 1)

        beforeApply()
        item.contents.removeAt(idx)
        item.contents.forEachIndexed { i, c -> c.orderIndex = i }
        return (idx - 1).coerceIn(0, item.contents.size - 1)
    }

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
     * Вызывающий обязан сам снять выделение и запись об активном фото: у него есть
     * `selectedItemId`/`activeIndex` и аниматор переключения, которых здесь нет.
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
     * Применить пресет к активному фото (`false` — нечего применять).
     *
     * Порядок обязателен и не переставляемый:
     *
     * 1. найти активный контент и размер его фото — без размера пресет считать нечем, и
     *    неизвестное фото обязано тихо ничего не менять (иначе появится пустой undo-шаг);
     * 2. [beforeApply] — checkpoint;
     * 3. выйти из [FramingMode.FIT] с запеканием вписанных значений (см.
     *    [applyFramingMode]): пресет — это явный выбор пользователя по КОНКРЕТНОМУ фото,
     *    и он важнее блокировки FIT. Пресет поверх FIT без запекания оставил бы остальные
     *    фото рамки с устаревшими хранимыми значениями и «дёрнул» бы их при переключении;
     * 4. применить пресет к активному фото.
     *
     * @param srcSizeOf размеры фото активного контента.
     * @param photoOf метаданные всех фото рамки — нужны запеканию на шаге 3.
     */
    fun applyPreset(
        item: ItemDraft,
        activeIndex: Int,
        preset: FitPreset,
        srcSizeOf: (String) -> Size?,
        photoOf: (String) -> PhotoEntity?,
        beforeApply: () -> Unit = {},
    ): Boolean {
        if (item.contents.isEmpty()) return false
        val idx = activeIndex.coerceIn(0, item.contents.size - 1)
        val c = item.contents[idx]
        val src = srcSizeOf(c.photoId) ?: return false

        beforeApply()
        applyFramingMode(item, FramingMode.FREE, photoOf)
        when (preset) {
            FitPreset.FIT -> LayoutPresets.fit(c, item.border(), src)
            FitPreset.FILL -> LayoutPresets.fill(c, item.border(), src)
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
            val border = item.border()
            for (c in item.contents) {
                val photo = photoOf(c.photoId) ?: continue
                val f = effectiveFraming(
                    FramingMode.FIT, border, photo.width, photo.height,
                    c.contentScale, c.panX, c.panY,
                )
                c.contentScale = f.scale
                c.panX = f.panX
                c.panY = f.panY
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
     * здесь, а не в вызывающем: иначе «создать комбо» и «добавить фото» ограничивались бы
     * по-разному, и лимит можно было бы обойти одним из двух входов.
     *
     * ПУСТОЙ НАБОР — НЕ ТУПИК. Создаётся одна пустая рамка на весь холст, сразу
     * активная и выделенная: без этого билдер остался бы полностью нефункциональным, ведь
     * все кнопки тулбара (включая «+Photo») требуют выделенной рамки.
     *
     * Идентификаторы рамок и контентов — случайные `UUID`, поэтому тесты проверяют только
     * отсутствие повторов и непустоту, а не конкретные значения.
     */
    fun newComboDrafts(
        photoMetas: List<PhotoEntity>,
        canvasW: Float,
        canvasH: Float,
    ): NewComboDrafts {
        val photos = photoMetas.take(Const.MAX_ITEMS)
        if (photos.isEmpty()) {
            val b = LayoutPresets.cellBorders(1, canvasW, canvasH).first()
            val blank = ItemDraft(
                id = UUID.randomUUID().toString(),
                zIndex = 0,
                centerX = b.centerX,
                centerY = b.centerY,
                width = b.width,
                height = b.height,
                contents = mutableListOf(),
            )
            return NewComboDrafts(items = listOf(blank), activeIndex = mapOf(blank.id to 0), selectedItemId = blank.id)
        }
        val items = LayoutPresets.cellBorders(photos.size, canvasW, canvasH).mapIndexed { i, b ->
            val p = photos[i]
            val draft = ItemDraft(
                id = UUID.randomUUID().toString(),
                zIndex = i,
                centerX = b.centerX,
                centerY = b.centerY,
                width = b.width,
                height = b.height,
                contents = mutableListOf(
                    ContentDraft(UUID.randomUUID().toString(), p.id, 0, 1f, 0f, 0f),
                ),
            )
            LayoutPresets.fill(draft.contents[0], b, p.srcSize())
            draft
        }
        return NewComboDrafts(
            items = items,
            activeIndex = items.associate { d -> d.id to 0 },
            selectedItemId = items.firstOrNull()?.id,
        )
    }
}