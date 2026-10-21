package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.Const
import com.combo.core.model.Direction
import com.combo.core.model.FramingMode
import com.combo.core.model.TransitionType
import com.combo.core.playback.AutoScheduler
import com.combo.core.playback.PlaybackEngine
import com.combo.core.playback.SwitchAnimator
import com.combo.core.playback.cycleIndex
import com.combo.core.playback.nextIndex
import com.combo.core.render.ContentDraft
import com.combo.core.render.ItemDraft
import com.combo.core.render.ItemFrame
import com.combo.core.render.ItemGestures
import com.combo.core.render.Viewport
import com.combo.core.render.buildItemFrame
import com.combo.core.render.minBorderSize
import com.combo.core.render.relayout
import com.combo.data.ComboEntity
import com.combo.data.PhotoEntity
import com.combo.data.srcSize
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.DraftEditor
import com.combo.feature.builder.draft.FitPreset
import com.combo.feature.builder.draft.frameContent
import com.combo.feature.builder.draft.toDraft
import com.combo.feature.builder.draft.toDrafts
import com.combo.feature.builder.draft.toEntities
import com.combo.feature.builder.draft.toSnap
import com.combo.feature.builder.history.DraftHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Состояние редактора комбо: тонкий диспетчер событий поверх уже вынесенных правил.
 *
 * Что где живёт: контракт (события, состояние, фазы жеста, testTag'и) — `BuilderContract.kt`,
 * фазы жеста и undo-стек — [DraftHistory], операции над драфтами и раскладка нового комбо —
 * [DraftEditor], зум вида — `Viewport.zoomedAround`, сборка кадра — `buildItemFrame`, тикер
 * анимации — [PlaybackEngine], метаданные и битмапы — [PhotoAssets]. Здесь осталось только
 * то, что знает про состояние экрана: `revision`, `isDirty`, флаги undo и синхронизация
 * [animator] с восстановленными индексами.
 */
@HiltViewModel
class ComboBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comboRepo: ComboRepository,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    /** Метаданные и битмапы живут в общем кэше; размеры и debounce — политика этого экрана. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)

    val bitmaps: StateFlow<Map<String, ImageBitmap>> = assets.bitmaps

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Единственный владелец «текущего» фото рамки во время анимации перехода. */
    val animator = SwitchAnimator()

    /**
     * Тикер редактора — тот же движок, что и в плеере, но БЕЗ расписания: пустой
     * [AutoScheduler] не создаёт ни одного слота, поэтому `due()` всегда пуст, `onDue` не
     * вызывается и цикл живёт ровно пока идёт анимация. Отличается только политика:
     * «жива задача» = идёт анимация, а «кадр» = [emitRevision].
     */
    private val engine = PlaybackEngine(
        scope = viewModelScope,
        animator = animator,
        scheduler = AutoScheduler(),
        isAlive = { animator.hasActive() },
        onDue = { itemId -> autoSwitch(itemId) },
        onFrame = { emitRevision() },
    )

    /**
     * История правок холста. Класс ничего не знает ни про ViewModel, ни про состояние
     * экрана: снимок и откат приходят лямбдами, поэтому фазы жеста проверяются обычным
     * JVM-тестом. Про `isDirty`/`revision` знает только вызывающий — по [DraftHistory.end].
     */
    private val history = DraftHistory(snapshot = ::snapshot, restore = ::restore)

    private var createdAt: Long = 0
    private var bitmapJob: Job? = null

    init {
        val comboId: String? = savedStateHandle["comboId"]
        val photoIds: List<String> = savedStateHandle.get<String>("photos")
            ?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        // `BuilderUiState.photos` — зеркало `assets.photos`. Источник правды один
        // (PhotoAssets), а поле состояния остаётся: его читают экран (цвета фото для
        // фона), frameFor(), photoOf() и тесты. Синхронизация ровно одна и ровно здесь.
        viewModelScope.launch {
            assets.photos.collect { meta -> _state.update { it.copy(photos = meta) } }
        }
        viewModelScope.launch {
            photoRepo.all().collect { lib ->
                _state.update { it.copy(libraryPhotos = lib) }
            }
        }
        viewModelScope.launch {
            if (comboId != null) loadExisting(comboId) else createNew(photoIds)
            refreshBitmaps(immediate = true)
        }
    }

    // ---------- loading / creation ----------

    private suspend fun loadExisting(comboId: String) {
        val loaded = comboRepo.get(comboId) ?: return
        createdAt = loaded.combo.createdAt
        val items = loaded.toDrafts()
        ensurePhotoMeta(items)
        _state.update {
            it.copy(
                comboId = comboId,
                name = loaded.combo.name,
                canvasW = loaded.combo.canvasWidth.toFloat(),
                canvasH = loaded.combo.canvasHeight.toFloat(),
                background = loaded.combo.background,
                items = items,
                activeIndex = items.associate { d -> d.id to 0 },
                isDirty = false,
                loaded = true,
            )
        }
    }

    private suspend fun createNew(photoIds: List<String>) {
        val canvasW = 1600f
        val canvasH = 900f
        // Лимит рамок дублирует [Const.MAX_ITEMS] из DraftEditor: не даём прочитать из базы
        // метаданные фото, которые в комбо всё равно не поместятся.
        val photos = photoIds.take(Const.MAX_ITEMS).mapNotNull { photoRepo.get(it) }
        // Раскладка (включая пустой набор — одна пустая выделенная рамка на весь холст), а
        // также начальные activeIndex и выделение целиком принадлежат редактору драфтов.
        val drafts = DraftEditor.newComboDrafts(photos, canvasW, canvasH)
        ensurePhotoMeta(drafts.items)
        _state.update {
            it.copy(
                name = "Combo",
                canvasW = canvasW,
                canvasH = canvasH,
                items = drafts.items,
                activeIndex = drafts.activeIndex,
                selectedItemId = drafts.selectedItemId,
                isDirty = true,
                loaded = true,
            )
        }
    }

    private suspend fun ensurePhotoMeta(items: List<ItemDraft>) {
        // Без метаданных нечем ни построить кадр, ни посчитать размер фото для пресетов.
        // Кэш сам пропускает уже известные id, поэтому повторный вызов при перезагрузке
        // не ходит в базу заново.
        val ids = items.flatMap { d -> d.contents.map { it.photoId } }.distinct()
        assets.ensureMeta(ids)
    }

    // ---------- события ----------

    /**
     * Тонкий диспетчер. Фазы жеста — в [gesture], правила мутаций драфтов — в
     * [DraftEditor] (обёрнуты в узкие хелперы ниже), зум вида — в `zoomedAround`, undo — в
     * [history]. Здесь остаётся политика экрана: что публиковать в состояние и когда
     * перезагружать битмапы.
     *
     * Тела мутаций жестов намеренно остаются здесь, а не в helper'ах: видно, где именно
     * происходит [emitRevision], и видно, что при отсутствии активного фото не будет ни
     * мутации, ни ревизии.
     */
    fun onEvent(e: BuilderEvent) {
        when (e) {
            is BuilderEvent.ItemTapped -> _state.update {
                it.copy(selectedItemId = e.id, revision = it.revision + 1)
            }
            BuilderEvent.CanvasTapped -> _state.update {
                it.copy(selectedItemId = null, revision = it.revision + 1)
            }
            BuilderEvent.ToggleMode -> _state.update {
                it.copy(mode = if (it.mode == EditMode.EDIT) EditMode.BORDER else EditMode.EDIT)
            }
            is BuilderEvent.ItemDrag -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val s = _state.value
                    ItemGestures.dragItem(item, e.delta, currentVs(), s.canvasW, s.canvasH)
                    emitRevision()
                }
            }
            is BuilderEvent.ItemPinch -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val minSize = minBorderSize(_state.value.canvasW, _state.value.canvasH)
                    ItemGestures.pinchItem(item, e.factor, minSize)
                    emitRevision()
                }
            }
            is BuilderEvent.ContentPan -> withItem(e.id) { item ->
                // В режиме FIT масштаб и смещение фото вычисляются при отрисовке, поэтому
                // сдвигать в данных нечего. Смена фото флингом и ресайз за грань идут
                // отдельными событиями и сюда не попадают.
                if (item.framingMode == FramingMode.FIT) return@withItem
                gesture(e.phase) {
                    activeContent(item)?.let { c ->
                        val src = _state.value.photos[c.photoId]?.srcSize() ?: return@gesture
                        ItemGestures.panContent(c, e.delta, currentVs(), item.border(), src)
                        emitRevision()
                    }
                }
            }
            is BuilderEvent.ContentZoom -> withItem(e.id) { item ->
                if (item.framingMode == FramingMode.FIT) return@withItem
                // Перезагрузка битмапов только на END: зум контента меняет нужный сэмпл,
                // а на каждом CHANGE это лишний декод.
                gesture(e.phase, refreshOnEnd = true) {
                    activeContent(item)?.let { c ->
                        val src = _state.value.photos[c.photoId]?.srcSize() ?: return@gesture
                        ItemGestures.zoomContent(c, e.factor, item.border(), src, currentVs())
                        emitRevision()
                    }
                }
            }
            is BuilderEvent.BorderResize -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val minSize = minBorderSize(_state.value.canvasW, _state.value.canvasH)
                    ItemGestures.resizeBorder(item, e.edge, e.delta, currentVs(), minSize)
                    emitRevision()
                }
            }
            is BuilderEvent.ContentFling -> withItem(e.id) { item ->
                // Переключать есть что только при двух и более фото; на пустой рамке
                // cycleIndex вернул бы null, а coerceIn(0, -1) бросил бы исключение.
                if (item.contents.size < 2) return@withItem
                val next = cycleIndex(activeIndexOf(item), item.contents.size, e.dir) ?: return@withItem
                performSwitch(item.id, next, item.manualTransition, e.dir)
            }
            is BuilderEvent.SetActive -> performSwitch(e.id, e.index, manualTransitionFor(e.id), Direction.UP)
            is BuilderEvent.AddPhotos -> withItem(e.id) { addPhotos(it, e.photoIds) }
            is BuilderEvent.RemoveActiveContent -> withItem(e.id) { removeActiveContent(it) }
            is BuilderEvent.ApplyPreset -> withItem(e.id) { applyPreset(it, e.preset) }
            is BuilderEvent.SetFramingMode -> withItem(e.id) { setFramingMode(it, e.mode) }
            is BuilderEvent.ReorderItem -> reorderItem(e.id, e.forward)
            is BuilderEvent.DeleteItem -> deleteItem(e.id)
            is BuilderEvent.SetSwitchMode -> withItem(selected()) { withCheckpoint { it.switchMode = e.mode } }
            is BuilderEvent.SetManualTransition ->
                withItem(selected()) { withCheckpoint { it.manualTransition = e.type } }
            is BuilderEvent.SetAutoTransition ->
                withItem(selected()) { withCheckpoint { it.autoTransition = e.type } }
            is BuilderEvent.SetAspect -> {
                checkpointForEdit()
                val s = _state.value
                // Рамки следуют за холстом пропорционально, а фото внутри FREE-рамок
                // сохраняют приближение относительно заполнения. Один чистый relayout,
                // чтобы undo/redo и отрисовка видели одно и то же.
                relayout(s.items, s.canvasW, s.canvasH, e.w.toFloat(), e.h.toFloat(), ::photoSrcSize)
                _state.update { it.copy(canvasW = e.w.toFloat(), canvasH = e.h.toFloat()) }
                markDirtyAndEmit()
            }
            is BuilderEvent.SetBackground -> withCheckpoint {
                // Фон теперь входит в undo (см. ComboSnap.background).
                _state.update { it.copy(background = e.spec) }
            }
            is BuilderEvent.SetName -> _state.update { it.copy(name = e.name, isDirty = true) }
            is BuilderEvent.ViewportPan -> _state.update {
                it.copy(viewport = it.viewport.copy(pan = it.viewport.pan + e.delta))
            }
            // Формула зума вокруг точки, границы зума и панорама от ПРИМЕНЁННОГО фактора —
            // в zoomedAround; здесь только нормализация размера экрана (до первого
            // измерения вьюхи он нулевой) и битмапы под новый масштаб.
            is BuilderEvent.ViewportZoom -> _state.update { s ->
                s.copy(
                    viewport = s.viewport.zoomedAround(
                        factor = e.factor,
                        focus = e.focus,
                        screen = Size(s.screenW.coerceAtLeast(1f), s.screenH.coerceAtLeast(1f)),
                        canvas = Size(s.canvasW, s.canvasH),
                    ),
                )
            }.also { refreshBitmaps() }
            // Сдвиг и зум вида в Undo не входят: это состояние редактора, а не холста.
            BuilderEvent.ResetViewport -> _state.update { it.copy(viewport = Viewport()) }
                .also { refreshBitmaps() }
            is BuilderEvent.SetScreenSize -> _state.update { it.copy(screenW = e.w, screenH = e.h) }
            // Пустой стек — не ошибка: событие тогда не публикует ревизию и не помечает
            // комбо изменённым, поэтому проверка раннего выхода остаётся в [onEvent].
            BuilderEvent.Undo -> if (history.canUndo) undoRedo(history::undo)
            BuilderEvent.Redo -> if (history.canRedo) undoRedo(history::redo)
            BuilderEvent.Save -> save()
        }
    }

    // ---------- операции над драфтами ----------

    /**
     * Добавить фото в рамку. Что именно добавляется, решает [DraftEditor] (дубликаты,
     * лимит, наличие метаданных) и только после этого ставит checkpoint; поэтому пустой
     * AddPhotos не оставляет ни undo-шага, ни `isDirty`.
     *
     * Метаданные известны синхронно (из кэша или библиотеки), поэтому в состояние они
     * попадают сразу: пресеты и frameFor() не должны ждать запроса. Источник правды при
     * этом PhotoAssets — ensureMeta его дозагрузит, а синхронизатор из init приведёт
     * state.photos к тому же.
     */
    private fun addPhotos(item: ItemDraft, photoIds: List<String>) {
        val meta = knownPhotos(photoIds)
        val added = DraftEditor.addContents(item, photoIds, meta, ::photoSrcSize, ::checkpointForEdit)
        if (added.isEmpty()) return
        // В состояние — только метаданные реально добавленных фото: запрошенные, но
        // отсечённые дубликаты и переполнение рамки сюда не попадают.
        val addedMeta = added.mapNotNull { c -> meta[c.photoId]?.let { c.photoId to it } }.toMap()
        _state.update { it.copy(photos = it.photos + addedMeta) }
        viewModelScope.launch { assets.ensureMeta(addedMeta.keys) }
        markDirtyAndEmit()
        refreshBitmaps(immediate = true)
    }

    /** Убрать активное фото: индекс пересчитывает [DraftEditor], аниматор — здесь. */
    private fun removeActiveContent(item: ItemDraft) {
        val newIdx = DraftEditor.removeActiveContent(item, activeIndexOf(item), ::checkpointForEdit)
            ?: return
        _state.update { s -> s.copy(activeIndex = s.activeIndex + (item.id to newIdx)) }
        animator.cancel(item.id)
        // Синхронизация второго хранилища индекса: без неё animator продолжил бы считать
        // текущим удалённое фото, и следующий свайп не проиграл бы переход (см.
        // SwitchAnimator.setCurrent).
        animator.setCurrent(item.id, newIdx)
        markDirtyAndEmit()
    }

    /** Пресет к активному фото. Порядок «запекание FIT → пресет» — внутри [DraftEditor]. */
    private fun applyPreset(item: ItemDraft, preset: FitPreset) {
        val changed = DraftEditor.applyPreset(
            item, activeIndexOf(item), preset, ::photoSrcSize, ::photoOf, ::checkpointForEdit,
        )
        if (changed) markDirtyAndEmit()
    }

    /**
     * Сменить режим рамки (FREE ↔ FIT).
     *
     * Запрос на режим, который у рамки уже стоит, — не правка: он не ставит checkpoint и не
     * помечает комбо изменённым. Иначе в истории появился бы пустой шаг — «Отменить», который
     * ничего не отменяет. Из тулбара такой запрос не приходит (кнопка AutoFit всегда
     * переключает на противоположный режим), но контракт события от этого не зависит.
     *
     * «Запекание» вписанных значений при выходе из FIT делает [DraftEditor.applyFramingMode].
     */
    private fun setFramingMode(item: ItemDraft, mode: FramingMode) {
        if (item.framingMode == mode) return
        checkpointForEdit()
        DraftEditor.applyFramingMode(item, mode, ::photoOf)
        markDirtyAndEmit()
    }

    private fun reorderItem(id: String, forward: Boolean) {
        val s = _state.value
        val next = DraftEditor.reorder(
            s.items, s.items.indexOfFirst { it.id == id }, forward, ::checkpointForEdit,
        ) ?: return
        _state.update { it.copy(items = next) }
        markDirtyAndEmit()
    }

    private fun deleteItem(id: String) {
        val s = _state.value
        val left = DraftEditor.deleteItem(s.items, id, ::checkpointForEdit) ?: return
        animator.cancel(id)
        // Рамки больше нет — индекс в animator сбрасываем в 0, а не удаляем запись:
        // наблюдаемо это ровно то же самое (currentIndex для отсутствующего ключа и так
        // отдаёт 0), но не требует второго метода у SwitchAnimator. Главное — animator
        // перестаёт считать текущим последнее фото несуществующей рамки.
        animator.setCurrent(id, 0)
        _state.update {
            it.copy(
                items = left,
                selectedItemId = if (it.selectedItemId == id) null else it.selectedItemId,
                activeIndex = it.activeIndex - id,
            )
        }
        markDirtyAndEmit()
    }

    /** Undo/Redo: откат, затем флаги и публикация. */
    private fun undoRedo(apply: () -> Unit) {
        apply()
        publishUndoFlags()
        markDirtyAndEmit()
    }

    /** Checkpoint перед дискретной мутацией, сама мутация, затем публикация. */
    private inline fun withCheckpoint(edit: () -> Unit) {
        checkpointForEdit()
        edit()
        markDirtyAndEmit()
    }

    // ---------- жесты: фазы ----------

    /**
     * Фазы жеста в одном месте: START → снимок, CHANGE → мутация ([onChange]),
     * END → checkpoint, CANCEL → откат. Все события жестов различаются только телом
     * мутации.
     *
     * [refreshOnEnd] — перезагрузить битмапы после жеста: зум контента меняет
     * требуемое разрешение.
     */
    private fun gesture(
        phase: GesturePhase,
        refreshOnEnd: Boolean = false,
        onChange: () -> Unit,
    ) {
        when (phase) {
            GesturePhase.START -> history.begin()
            GesturePhase.CHANGE -> onChange()
            GesturePhase.END -> finishGesture(refreshOnEnd)
            GesturePhase.CANCEL -> {
                history.cancel()
                emitRevision()
            }
        }
    }

    /**
     * END: undo-шаг, ревизия и `isDirty` — только если жест что-то изменил; пустой шаг
     * уже отфильтрован в [DraftHistory.end]. Перезагрузка битмапов — политика экрана,
     * поэтому [refreshOnEnd] проверяется здесь, а не в истории.
     */
    private fun finishGesture(refreshOnEnd: Boolean) {
        if (history.end()) {
            publishUndoFlags()
            emitRevision()
            markDirty()
        }
        if (refreshOnEnd) refreshBitmaps()
    }

    /** Checkpoint перед дискретной мутацией холста: снимок «до» + обновление флагов undo. */
    private fun checkpointForEdit() {
        history.checkpoint()
        publishUndoFlags()
    }

    private fun publishUndoFlags() = _state.update {
        it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
    }

    private fun markDirty() = _state.update { it.copy(isDirty = true) }

    private fun markDirtyAndEmit() = _state.update {
        it.copy(isDirty = true, revision = it.revision + 1)
    }

    private fun emitRevision() = _state.update { it.copy(revision = it.revision + 1) }

    // ---------- switching / playback preview ----------

    private fun performSwitch(itemId: String, to: Int, type: TransitionType, dir: Direction) {
        val item = _state.value.items.firstOrNull { it.id == itemId } ?: return
        // На рамке без контента (или с одним) переключать нечего; при contents.size == 0
        // coerceIn(0, -1) бросил бы IllegalArgumentException.
        if (item.contents.size < 2) return
        val target = to.coerceIn(0, item.contents.size - 1)
        animator.switch(itemId, target, type, dir)
        _state.update { s -> s.copy(activeIndex = s.activeIndex + (itemId to target)) }
        startTickerIfNeeded()
    }

    private fun manualTransitionFor(itemId: String): TransitionType =
        _state.value.items.firstOrNull { it.id == itemId }?.manualTransition ?: TransitionType.NONE

    private fun startTickerIfNeeded() = engine.startIfNeeded()

    /**
     * Автосмена для движка воспроизведения ([PlaybackEngine]). В редакторе планировщик
     * пуст, поэтому сюда не приходит ни одного id: фото в редакторе меняет только пользователь
     * ([BuilderEvent.ContentFling], [BuilderEvent.SetActive]). Обработчик всё равно
     * реализован полностью, потому что движок общий с плеером и не должен зависеть от того,
     * какой экран его использует.
     */
    private fun autoSwitch(itemId: String) {
        val item = _state.value.items.firstOrNull { it.id == itemId } ?: return
        if (item.contents.size < 2) return
        val target = nextIndex(activeIndexOf(item), item.contents.size) ?: return
        performSwitch(itemId, target, item.autoTransition, Direction.UP)
    }

    // ---------- frames for rendering ----------

    fun frameFor(item: ItemDraft): ItemFrame? {
        val s = _state.value
        val idx = activeIndexOf(item)
        if (idx !in item.contents.indices) return null
        val incomingC = item.contents[idx]
        val inPhoto = s.photos[incomingC.photoId] ?: return null
        val inImg = assets.bitmaps.value[incomingC.photoId] ?: return null

        val anim = animator.frame(item.id)
        if (anim == null) {
            return buildItemFrame(
                border = item.border(),
                mode = item.framingMode,
                incoming = incomingC.frameContent(inImg, inPhoto),
            )
        }
        val (active, progress) = anim
        // Нет исходящего контента (переход уже нечего показывать) — статичный кадр:
        // анимация без `outgoing` в `drawItem` всё равно рисует только входящий.
        val fromC = item.contents.getOrNull(active.from)
        val outgoingPhoto = fromC?.let { s.photos[it.photoId] }
        val outgoingImg = fromC?.let { assets.bitmaps.value[it.photoId] }
        return buildItemFrame(
            border = item.border(),
            mode = item.framingMode,
            incoming = incomingC.frameContent(inImg, inPhoto),
            outgoing = if (fromC != null && outgoingPhoto != null && outgoingImg != null) {
                fromC.frameContent(outgoingImg, outgoingPhoto)
            } else {
                null
            },
            transition = active.type,
            progress = progress,
            direction = active.dir,
        )
    }

    fun activeIndexOf(item: ItemDraft): Int {
        val raw = _state.value.activeIndex[item.id] ?: 0
        return if (item.contents.isEmpty()) 0 else raw.coerceIn(0, item.contents.size - 1)
    }

    // ---------- bitmaps ----------

    fun refreshBitmaps(immediate: Boolean = false) {
        bitmapJob?.cancel()
        bitmapJob = viewModelScope.launch {
            // Задержка перед загрузкой: во время жеста зум/пан меняется десятками кадров,
            // а декод крупного сэмпла на каждом шаге — это лишняя работа впустую.
            if (!immediate) delay(300)
            val s = _state.value
            val maxBorder = s.items.maxOfOrNull { maxOf(it.width, it.height) } ?: return@launch
            // Политика размера — здесь: запас x2 на зум, дальше потолки из Const/экрана.
            val requiredPx = (maxBorder * s.viewport.zoom * 2).toInt().coerceIn(128, 4096)
            val ids = s.items.flatMap { d -> d.contents.map { it.photoId } }.distinct()
            // Пропуск «уже есть и этого размера достаточно» — забота PhotoAssets.
            for (id in ids) assets.ensureBitmap(id, requiredPx)
        }
    }

    // ---------- snapshots / undo ----------

    /** Холст целиком: размеры, фон и рамки. Поля рамок — через `ItemDraft.toSnap()`. */
    private fun snapshot() = ComboSnap(
        canvasW = _state.value.canvasW,
        canvasH = _state.value.canvasH,
        background = _state.value.background,
        items = _state.value.items.map { it.toSnap() },
    )

    private fun restore(snap: ComboSnap) {
        val items = snap.items.map { it.toDraft() }
        // activeIndex в снимок не входит, поэтому берётся из ТЕКУЩЕГО состояния и лишь
        // подгоняется под размер восстановленного набора. Считаем его до update: то же
        // значение ниже передаётся в SwitchAnimator и должно совпасть с состоянием.
        // У рамки может быть 0 контентов (комбо создано без фото): coerceIn(0, -1)
        // бросил бы IllegalArgumentException на любом undo/redo/cancel, поэтому верхняя
        // граница не меньше нуля.
        val activeIndex = _state.value.activeIndex.mapValues { (id, v) ->
            items.firstOrNull { it.id == id }
                ?.let { v.coerceIn(0, maxOf(0, it.contents.size - 1)) } ?: v
        }
        _state.update { s ->
            s.copy(
                canvasW = snap.canvasW,
                canvasH = snap.canvasH,
                background = snap.background,
                items = items,
                activeIndex = activeIndex,
                // После undo/redo выделенной рамки может не быть в наборе: сбрасываем
                // выделение, чтобы selectedItemId не указывал на несуществующую рамку.
                selectedItemId = s.selectedItemId?.takeIf { sel -> items.any { it.id == sel } },
            )
        }
        // SwitchAnimator хранит собственный «текущий» индекс, а драфты только что
        // пересобраны: старый индекс мог указать на другое фото или за границу набора.
        // Синхронизируем его с activeIndex, иначе следующий свайп стартует из неверного
        // `from` и не проиграет переход.
        // Рамки, которых нет в восстановленном наборе, не трогаем.
        items.forEach { d -> activeIndex[d.id]?.let { animator.setCurrent(d.id, it) } }
    }

    // ---------- save ----------

    private fun save() {
        val s = _state.value
        if (s.saving || !s.loaded) return
        // Пустые рамки (New combo без фото, рамка без контента) в базу не пишем: они
        // ничего не рисуют, но занимают место и ломают превью. zIndex перенумеровывается
        // подряд, потому что хранить дыры в порядке слоёв незачем.
        val savedItems = s.items.filter { it.contents.isNotEmpty() }
        // Молчаливый return был хуже сообщения: пользователь ждал «сохранено», а редактор
        // оставался открытым — и непонятно было, что именно не так.
        if (savedItems.isEmpty()) {
            viewModelScope.launch { _messages.send(MSG_NOTHING_TO_SAVE) }
            return
        }
        _state.update { it.copy(saving = true) }
        // Драфты мутабельны и мутируются на главном потоке — читаем их ЗДЕСЬ, до ухода
        // на IO, иначе в комбо может попасть полу-изменённая рамка.
        val id = s.comboId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val created = if (createdAt > 0) createdAt else now
        val combo = ComboEntity(
            id = id,
            name = s.name.ifBlank { "Combo" },
            canvasWidth = s.canvasW.toInt(),
            canvasHeight = s.canvasH.toInt(),
            background = s.background,
            createdAt = created,
            updatedAt = now,
        )
        // Поля рамок и фото собирает `toEntities()`: enum'ы — через `.name` (enum'ы в базе
        // хранятся строками), zIndex — по индексу в списке.
        val items = savedItems.toEntities(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                comboRepo.saveCombo(combo, items.first, items.second)
                _saved.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Показываем ошибку пользователю: необработанное исключение в
                // корутине уронило бы процесс.
                _state.update { it.copy(saving = false) }
                _messages.send("Failed to save combo: ${e.message ?: e::class.java.simpleName}")
            }
        }
    }

    // ---------- misc ----------

    private fun selected(): String? = _state.value.selectedItemId

    private inline fun withItem(id: String?, block: (ItemDraft) -> Unit) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        block(item)
    }

    private fun activeContent(item: ItemDraft): ContentDraft? =
        item.contents.getOrNull(activeIndexOf(item))

    /** Размеры фото — единственный источник для пресетов и `relayout` (кэш метаданных). */
    private fun photoSrcSize(photoId: String): Size? = _state.value.photos[photoId]?.srcSize()

    private fun photoOf(photoId: String): PhotoEntity? = _state.value.photos[photoId]

    /**
     * Метаданные запрошенных фото: сначала кэш, потом библиотека. Для item'а, созданного
     * «с нуля», метаданных в кэше ещё нет, а без них фото нечем ни построить для кадра,
     * ни посчитать размер для пресетов.
     */
    private fun knownPhotos(ids: List<String>): Map<String, PhotoEntity> {
        val s = _state.value
        val known = buildMap {
            putAll(s.libraryPhotos.associateBy { it.id })
            putAll(s.photos)
        }
        return ids.mapNotNull { id -> known[id]?.let { id to it } }.toMap()
    }

    fun currentVs(): Float {
        val s = _state.value
        return s.viewport.scale(
            Size(s.canvasW, s.canvasH),
            Size(s.screenW.coerceAtLeast(1f), s.screenH.coerceAtLeast(1f)),
        )
    }

    fun hitTest(canvasPoint: Offset): String? =
        _state.value.items.sortedByDescending { it.zIndex }
            .firstOrNull { it.border().rect().contains(canvasPoint) }?.id

    override fun onCleared() {
        super.onCleared()
        bitmapJob?.cancel()
    }
}