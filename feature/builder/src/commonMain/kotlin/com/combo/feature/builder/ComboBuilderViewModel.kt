package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.core.render.ItemDraft
import com.combo.core.render.Border
import com.combo.core.render.ItemGestures
import com.combo.core.render.Viewport
import com.combo.core.render.TransformMath
import com.combo.core.render.minBorderSize
import com.combo.core.render.relayout
import com.combo.core.render.snap.FrameSnap
import com.combo.core.render.snap.FrameSnapState
import com.combo.core.render.snap.SnapStep
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
import com.combo.feature.builder.draft.cropBounds
import com.combo.feature.builder.draft.cropTo
import com.combo.feature.builder.draft.toDraft
import com.combo.feature.builder.draft.toDrafts
import com.combo.feature.builder.draft.toEntities
import com.combo.feature.builder.draft.toSnap
import com.combo.feature.builder.history.DraftHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Состояние редактора комбо: тонкий диспетчер событий поверх уже вынесенных правил.
 *
 * Что где живёт: контракт (события, состояние, фазы жеста, testTag'и) — `BuilderContract.kt`,
 * фазы жеста и undo-стек — [DraftHistory], операции над драфтами и раскладка нового комбо —
 * [DraftEditor], зум вида — `Viewport.zoomedAround`, сборка кадра — `buildItemFrame`,
 * метаданные и битмапы — [PhotoAssets]. Мутабельные рамки принадлежат ViewModel.
 *
 * Время, id и IO-диспетчер приходят параметрами: commonMain не знает ни
 * `System.currentTimeMillis`, ни `UUID`, ни `Dispatchers.IO`.
 */
class ComboBuilderViewModel(
    savedStateHandle: SavedStateHandle,
    private val comboRepo: ComboRepository,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
    private val ids: IdGenerator,
    private val clock: WallClock,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderUiState())
    private var draftItems = emptyList<ItemDraft>()
    internal val state: StateFlow<BuilderUiState> = _state.asStateFlow()
    internal val selectedPhotoSize: StateFlow<PhotoSize?> = state
        .map { it.selectedPhotoSize() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Метаданные и битмапы живут в общем кэше; размеры и debounce — политика этого экрана. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)

    internal val bitmaps: StateFlow<Map<String, ImageBitmap>> = assets.bitmaps

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    internal val saved = _saved.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    internal val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * История правок холста. Класс ничего не знает ни про ViewModel, ни про состояние
     * экрана: снимок и откат приходят лямбдами, поэтому фазы жеста проверяются обычным
     * JVM-тестом. Вызывающий выставляет `isDirty` по результату [DraftHistory.end].
     */
    private val history = DraftHistory(snapshot = ::snapshot, restore = ::restore)

    private var createdAt: Long = 0
    private var bitmapJob: Job? = null
    private var snapSession: SnapSession? = null

    private class SnapSession(
        val id: String,
        val kind: SnapDragKind,
        val engine: FrameSnap,
        var state: FrameSnapState,
    )

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
        draftItems = loaded.toDrafts()
        ensurePhotoMeta(draftItems)
        publishDraft {
            it.copy(
                comboId = comboId,
                name = loaded.combo.name,
                canvasW = loaded.combo.canvasWidth.toFloat(),
                canvasH = loaded.combo.canvasHeight.toFloat(),
                background = loaded.combo.background,
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
        // Раскладка (включая пустой набор — комбо без рамок) и начальное выделение целиком
        // принадлежат редактору драфтов.
        val drafts = DraftEditor.newComboDrafts(photos, canvasW, canvasH, ids::newId)
        draftItems = drafts.items
        ensurePhotoMeta(draftItems)
        publishDraft {
            it.copy(
                name = "Combo",
                canvasW = canvasW,
                canvasH = canvasH,
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
        assets.ensureMeta(items.map { it.photoId }.distinct())
    }

    // ---------- события ----------

    /**
     * Тонкий диспетчер. Фазы жеста — в [gesture], правила мутаций драфтов — в
     * [DraftEditor] (обёрнуты в узкие хелперы ниже), зум вида — в `zoomedAround`, undo — в
     * [history]. Здесь остаётся политика экрана: что публиковать в состояние и когда
     * перезагружать битмапы.
     *
     * После мутации публикуется отдельный снимок рамок для UI.
     */
    internal fun onEvent(e: BuilderEvent) {
        when (e) {
            is BuilderEvent.ItemTapped -> selectItem(e.id)
            is BuilderEvent.ItemPressed -> {
                history.begin()
                selectItem(e.id, e.bringToFront)
            }
            is BuilderEvent.PointerEditFinished -> {
                clearSnap()
                gesture(if (e.cancelled) GesturePhase.CANCEL else GesturePhase.END) {}
            }
            BuilderEvent.CanvasTapped -> _state.update {
                it.copy(selectedItemId = null)
            }
            BuilderEvent.ToggleMode -> _state.update {
                it.copy(mode = if (it.mode == EditMode.EDIT) EditMode.BORDER else EditMode.EDIT)
            }
            is BuilderEvent.ItemDrag -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val s = _state.value
                    ItemGestures.dragItem(item, e.delta, currentVs(), s.canvasW, s.canvasH)
                    publishDraft()
                }
            }
            is BuilderEvent.SnapDrag -> withItem(e.id) { item -> moveWithSnap(item, e) }
            BuilderEvent.BypassSnap -> bypassSnap()
            BuilderEvent.ClearSnap -> clearSnap()
            is BuilderEvent.ItemPinch -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val minSize = minBorderSize(_state.value.canvasW, _state.value.canvasH)
                    ItemGestures.pinchItem(item, e.factor, minSize)
                    publishDraft()
                }
            }
            is BuilderEvent.ContentPan -> withItem(e.id) { item ->
                // В режиме FIT масштаб и смещение фото вычисляются при отрисовке, поэтому
                // сдвигать в данных нечего. Ресайз за грань идёт отдельным событием и сюда
                // не попадает.
                if (item.framingMode == FramingMode.FIT) return@withItem
                gesture(e.phase) {
                    val src = _state.value.photos[item.photoId]?.srcSize() ?: return@gesture
                    ItemGestures.panContent(item, e.delta, currentVs(), item.border(), src)
                    publishDraft()
                }
            }
            is BuilderEvent.ContentZoom -> withItem(e.id) { item ->
                if (item.framingMode == FramingMode.FIT) return@withItem
                // Перезагрузка битмапов только на END: зум контента меняет нужный сэмпл,
                // а на каждом CHANGE это лишний декод.
                gesture(e.phase, refreshOnEnd = true) {
                    val src = _state.value.photos[item.photoId]?.srcSize() ?: return@gesture
                    ItemGestures.zoomContent(item, e.factor, src)
                    publishDraft()
                }
            }
            is BuilderEvent.BorderResize -> withItem(e.id) { item ->
                gesture(e.phase) {
                    val minSize = minBorderSize(_state.value.canvasW, _state.value.canvasH)
                    ItemGestures.resizeBorder(item, e.edge, e.delta, currentVs(), minSize)
                    publishDraft()
                }
            }
            is BuilderEvent.AddItems -> addItems(e.photoIds)
            is BuilderEvent.ReplacePhoto -> withItem(e.id) { replacePhoto(it, e.photoId) }
            is BuilderEvent.ApplyPreset -> withItem(e.id) { applyPreset(it, e.preset) }
            is BuilderEvent.SetFramingMode -> withItem(e.id) { setFramingMode(it, e.mode) }
            is BuilderEvent.DeleteItem -> deleteItem(e.id)
            is BuilderEvent.SetAspect -> {
                val s = _state.value
                if (e.w <= 0 || e.h <= 0 || e.w.toLong() * e.h > Const.MAX_CANVAS_PIXELS ||
                    (s.canvasW == e.w.toFloat() && s.canvasH == e.h.toFloat())
                ) return
                checkpointForEdit()
                // Рамки следуют за холстом пропорционально, а фото внутри FREE-рамок
                // сохраняют приближение относительно заполнения. Один чистый relayout,
                // чтобы undo/redo и отрисовка видели одно и то же.
                relayout(draftItems, s.canvasW, s.canvasH, e.w.toFloat(), e.h.toFloat(), ::photoSrcSize)
                publishEdit { it.copy(canvasW = e.w.toFloat(), canvasH = e.h.toFloat()) }
            }
            is BuilderEvent.SetBackground -> {
                checkpointForEdit()
                publishEdit { it.copy(background = e.spec) }
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
                        screen = s.viewportSize,
                        canvas = s.canvasSize,
                    ),
                )
            }.also { refreshBitmaps() }
            // Сдвиг и зум вида в Undo не входят: это состояние редактора, а не холста.
            BuilderEvent.ResetViewport -> _state.update { it.copy(viewport = Viewport()) }
                .also { refreshBitmaps() }
            is BuilderEvent.SetScreenSize -> _state.update { it.copy(screenW = e.w, screenH = e.h) }
            // Пустой стек не должен помечать комбо изменённым.
            BuilderEvent.Undo -> if (history.canUndo) undoRedo(history::undo)
            BuilderEvent.Redo -> if (history.canRedo) undoRedo(history::redo)
            BuilderEvent.Save -> save()
        }
    }

    // ---------- операции над драфтами ----------

    /**
     * Добавить по рамке на каждое фото и выделить последнюю добавленную: пользователь
     * добавляет рамку, чтобы работать с ней, и без выделения все кнопки тулбара, кроме
     * «+Frame», остались бы выключенными.
     *
     * Что именно добавляется (лимит, повторы), решает [DraftEditor] и только после этого
     * ставит checkpoint; поэтому пустой запрос или исчерпанный лимит не оставляют ни
     * undo-шага, ни `isDirty`.
     *
     * Метаданные известны синхронно (из кэша или библиотеки), поэтому в состояние они
     * попадают сразу: пресеты и frameFor() не должны ждать запроса. Источник правды при
     * этом PhotoAssets — ensureMeta его дозагрузит, а синхронизатор из init приведёт
     * state.photos к тому же. Фото без метаданных пропускаются: их нечем ни показать, ни
     * вписать в рамку.
     */
    private fun addItems(photoIds: List<String>) {
        val meta = knownPhotos(photoIds)
        val photos = photoIds.mapNotNull { meta[it] }
        val s = _state.value
        val added = DraftEditor.addItems(draftItems, photos, s.canvasW, s.canvasH, ::checkpointForEdit, ids::newId)
            ?: return
        draftItems = added.items
        val usedIds = draftItems.map { it.photoId }.toSet()
        publishEdit {
            it.copy(
                selectedItemId = added.addedIds.last(),
                photos = it.photos + meta.filterKeys { id -> id in usedIds },
            )
        }
        viewModelScope.launch { assets.ensureMeta(usedIds) }
        refreshBitmaps(immediate = true)
    }

    /**
     * Подставить в рамку другое фото. Метаданные нового фото берутся так же, как при
     * добавлении ([knownPhotos]); без них замена тихо ничего не делает (нет размера —
     * нечем посчитать «заполнить»).
     */
    private fun replacePhoto(item: ItemDraft, photoId: String) {
        val photo = knownPhotos(listOf(photoId))[photoId] ?: return
        val changed = DraftEditor.replacePhoto(item, photo, photoSrcSize(photoId) ?: photo.srcSize(), ::checkpointForEdit)
        if (!changed) return
        publishEdit { it.copy(photos = it.photos + (photoId to photo)) }
        viewModelScope.launch { assets.ensureMeta(listOf(photoId)) }
        refreshBitmaps(immediate = true)
    }

    /** Пресет к фото рамки. Порядок «запекание FIT → пресет» — внутри [DraftEditor]. */
    private fun applyPreset(item: ItemDraft, preset: FitPreset) {
        val changed = DraftEditor.applyPreset(
            item, preset, ::photoSrcSize, ::photoOf, ::checkpointForEdit,
        )
        if (changed) publishEdit()
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
        publishEdit()
    }

    private fun selectItem(id: String, bringToFront: Boolean = false) {
        val s = _state.value
        val index = draftItems.indexOfFirst { it.id == id }
        if (index < 0) return
        val reordered = if (bringToFront && !s.saving) {
            DraftEditor.moveToFront(draftItems, index)
        } else null
        if (reordered == null && s.selectedItemId == id) return
        if (reordered != null) draftItems = reordered
        publishDraft { it.copy(selectedItemId = id) }
    }

    private fun moveWithSnap(item: ItemDraft, event: BuilderEvent.SnapDrag) {
        val session = snapSession?.takeIf { it.id == item.id && it.kind == event.kind } ?: run {
            val s = _state.value
            val frame = item.border()
            val kind = event.kind
            if (kind == SnapDragKind.PanPhoto) {
                if (item.framingMode == FramingMode.FIT) return
                val src = s.photos[item.photoId] ?: return
                val size = TransformMath.contentSize(src.width, src.height, item.contentScale)
                val photo = Border(frame.centerX + item.panX, frame.centerY + item.panY, size.width, size.height)
                SnapSession(item.id, kind, FrameSnap.photo(frame, photo), FrameSnapState(photo))
            } else {
                val targets = draftItems.filter { it.id != item.id }.sortedBy { it.id }.map { it.border() }
                val engine = if (kind is SnapDragKind.Resize) {
                    FrameSnap.resize(
                        s.canvasW, s.canvasH, frame, targets, kind.edge,
                        minBorderSize(s.canvasW, s.canvasH), kind.oneSided,
                    )
                } else FrameSnap(s.canvasW, s.canvasH, frame, targets)
                SnapSession(item.id, kind, engine, FrameSnapState(frame))
            }
        }
        snapSession = session
        val step = session.engine.move(
            session.state, event.delta.x, event.delta.y, event.distances, event.bypass,
        )
        applySnap(item, session, step)
    }

    private fun bypassSnap() {
        val session = snapSession ?: return
        if (_state.value.snapGuides.isEmpty()) return
        withItem(session.id) { applySnap(it, session, session.state.withoutSnap()) }
    }

    private fun applySnap(item: ItemDraft, session: SnapSession, step: SnapStep) {
        session.state = step.state
        val placed = step.placed
        if (session.kind == SnapDragKind.PanPhoto) {
            item.panX = placed.centerX - item.centerX
            item.panY = placed.centerY - item.centerY
        } else {
            item.centerX = placed.centerX
            item.centerY = placed.centerY
            item.width = placed.width
            item.height = placed.height
        }
        publishDraft { it.copy(snapGuides = step.guides) }
    }

    private fun clearSnap() {
        snapSession = null
        if (_state.value.snapGuides.isNotEmpty()) _state.update { it.copy(snapGuides = emptyList()) }
    }

    private fun deleteItem(id: String) {
        draftItems = DraftEditor.deleteItem(draftItems, id, ::checkpointForEdit) ?: return
        publishEdit { it.copy(selectedItemId = it.selectedItemId.takeUnless { selected -> selected == id }) }
        refreshBitmaps(immediate = true)
    }

    private fun undoRedo(apply: () -> Unit) {
        apply()
        _state.update { it.copy(isDirty = true) }
        refreshBitmaps()
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
            GesturePhase.CANCEL -> history.cancel()
        }
    }

    /**
     * END: undo-шаг и `isDirty` — только если жест что-то изменил; пустой шаг
     * уже отфильтрован в [DraftHistory.end]. Перезагрузка битмапов — политика экрана,
     * поэтому [refreshOnEnd] проверяется здесь, а не в истории.
     */
    private fun finishGesture(refreshOnEnd: Boolean) {
        if (history.end()) {
            _state.update { it.copy(isDirty = true, canUndo = history.canUndo, canRedo = history.canRedo) }
        }
        if (refreshOnEnd) refreshBitmaps()
    }

    /** Снимок перед дискретной мутацией. Флаги undo публикуются вместе с правкой. */
    private fun checkpointForEdit() {
        history.checkpoint()
    }

    private inline fun publishDraft(update: (BuilderUiState) -> BuilderUiState = { it }) {
        val items = draftItems.map { it.toSnap() }
        _state.update { state ->
            update(state).copy(items = items, canUndo = history.canUndo, canRedo = history.canRedo)
        }
    }

    private inline fun publishEdit(update: (BuilderUiState) -> BuilderUiState = { it }) {
        publishDraft { update(it).copy(isDirty = true) }
    }

    // ---------- bitmaps ----------

    internal fun refreshBitmaps(immediate: Boolean = false) {
        bitmapJob?.cancel()
        assets.evictExcept(_state.value.items.map { it.photoId }.toSet())
        bitmapJob = viewModelScope.launch {
            // Задержка перед загрузкой: во время жеста зум/пан меняется десятками кадров,
            // а декод крупного сэмпла на каждом шаге — это лишняя работа впустую.
            if (!immediate) delay(300)
            val s = _state.value
            val maxBorder = s.items.maxOfOrNull { maxOf(it.width, it.height) } ?: return@launch
            // Политика размера — здесь: запас x2 на зум, дальше потолки из Const/экрана.
            val requiredPx = (maxBorder * s.viewport.zoom * 2).toInt().coerceIn(128, 4096)
            val ids = s.items.map { it.photoId }.distinct()
            // Пропуск «уже есть и этого размера достаточно» — забота PhotoAssets.
            for (id in ids) assets.ensureBitmap(id, requiredPx)
        }
    }

    // ---------- snapshots / undo ----------

    private fun snapshot(): ComboSnap = _state.value.canvasSnapshot()

    private fun restore(snap: ComboSnap) {
        draftItems = snap.items.map { it.toDraft() }
        publishDraft { s ->
            s.copy(
                canvasW = snap.canvasW,
                canvasH = snap.canvasH,
                background = snap.background,
                // После undo/redo выделенной рамки может не быть в наборе: сбрасываем
                // выделение, чтобы selectedItemId не указывал на несуществующую рамку.
                selectedItemId = s.selectedItemId?.takeIf { sel -> draftItems.any { it.id == sel } },
            )
        }
    }

    // ---------- save ----------

    private fun save() {
        val s = _state.value
        if (s.saving || !s.loaded) return
        // Молчаливый return был хуже сообщения: пользователь ждал «сохранено», а редактор
        // оставался открытым — и непонятно было, что именно не так.
        if (s.items.isEmpty()) {
            viewModelScope.launch { _messages.send(MSG_NOTHING_TO_SAVE) }
            return
        }
        _state.update { it.copy(saving = true) }
        val snapshot = s.canvasSnapshot()
        val draft = snapshot.cropTo(snapshot.cropBounds(s.photos))
        val id = s.comboId ?: ids.newId()
        val now = clock.nowMillis()
        val created = if (createdAt > 0) createdAt else now
        val combo = ComboEntity(
            id = id,
            name = s.name.ifBlank { "Combo" },
            canvasWidth = draft.canvasW.toInt(),
            canvasHeight = draft.canvasH.toInt(),
            background = draft.background,
            createdAt = created,
            updatedAt = now,
        )
        // Поля рамок и фото собирает `toEntities()`: enum'ы — через `.name` (enum'ы в базе
        // хранятся строками), zIndex — по индексу в списке.
        val items = draft.items.toEntities(id)
        viewModelScope.launch(ioDispatcher) {
            try {
                comboRepo.saveCombo(combo, items)
                _saved.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Показываем ошибку пользователю: необработанное исключение в
                // корутине уронило бы процесс.
                _state.update { it.copy(saving = false) }
                val detail = e.message?.takeIf { it.isNotBlank() }
                _messages.send(if (detail == null) "Failed to save combo" else "Failed to save combo: $detail")
            }
        }
    }

    // ---------- misc ----------

    private inline fun withItem(id: String?, block: (ItemDraft) -> Unit) {
        val item = draftItems.firstOrNull { it.id == id } ?: return
        block(item)
    }

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

    internal fun currentVs(): Float {
        val s = _state.value
        return s.viewport.scale(s.canvasSize, s.viewportSize)
    }

    internal fun hitTest(canvasPoint: Offset): String? =
        _state.value.items.sortedByDescending { it.zIndex }
            .firstOrNull { it.border().rect().contains(canvasPoint) }?.id

    override fun onCleared() {
        super.onCleared()
        bitmapJob?.cancel()
    }
}
