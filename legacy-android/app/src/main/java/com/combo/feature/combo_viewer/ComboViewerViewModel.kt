package com.combo.feature.combo_viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.combo.core.model.Direction
import com.combo.core.model.SwitchMode
import com.combo.core.playback.AutoScheduler
import com.combo.core.playback.PlaybackEngine
import com.combo.core.playback.SwitchAnimator
import com.combo.core.playback.cycleIndex
import com.combo.core.playback.nextIndex
import com.combo.core.render.ItemFrame
import com.combo.core.render.buildItemFrame
import com.combo.data.ComboItemContentEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.data.border
import com.combo.data.frameContent
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ComboViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comboRepo: ComboRepository,
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
) : ViewModel() {

    data class State(
        val combo: ComboWithItems? = null,
        val photos: Map<String, PhotoEntity> = emptyMap(),
        val revision: Long = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Метаданные и битмапы — в общем кэше; размер (`screenMaxPx`) остаётся политикой плеера. */
    private val assets = PhotoAssets(photoRepo, bitmapSource)

    val bitmaps: StateFlow<Map<String, ImageBitmap>> = assets.bitmaps

    val animator = SwitchAnimator()
    val scheduler = AutoScheduler()

    /**
     * Тикер живёт в [PlaybackEngine]: цикл, прерываемый сон до ближайшего таймера и
     * wake-канал там же. Здесь — только политика плеера: когда задача считается живой,
     * что делать с «пора» и что считать кадром.
     */
    private val engine = PlaybackEngine(
        scope = viewModelScope,
        animator = animator,
        scheduler = scheduler,
        // Комба нет — жить незачем; а пока комбо есть, тикер держится за анимацию ИЛИ за
        // работающий таймер (иначе он умер бы до первого автосрабатывания).
        isAlive = {
            _state.value.combo != null && (animator.hasActive() || scheduler.hasRunning())
        },
        onDue = { itemId -> autoSwitch(itemId) },
        onFrame = { bump() },
    )

    private var screenMaxPx = 1080

    init {
        // `State.photos` — зеркало `assets.photos`: единственный источник правды один,
        // а поле состояния остаётся, потому что его читают фон (`backgroundFrames`)
        // и frameFor(). Синхронизация ровно одна и ровно здесь.
        viewModelScope.launch {
            assets.photos.collect { meta -> _state.update { it.copy(photos = meta) } }
        }
        val comboId: String? = savedStateHandle["comboId"]
        viewModelScope.launch {
            if (comboId == null) return@launch
            val loaded = comboRepo.get(comboId) ?: return@launch
            _state.update { it.copy(combo = loaded) }
            scheduleAuto(loaded)
            loadAssets(loaded)
            startTickerIfNeeded()
        }
    }

    fun setScreenSize(w: Int, h: Int) {
        screenMaxPx = maxOf(w, h)
    }

    private fun scheduleAuto(combo: ComboWithItems) {
        combo.items.forEach { iw ->
            // Рамке с одним фото переключаться нечем: слот был бы вечно «пора» —
            // тикер крутился бы впустую до 60 раз в секунду.
            if (iw.contents.size < 2) return@forEach
            when (iw.item.switchModeEnum) {
                SwitchMode.AUTO, SwitchMode.MANUAL_AUTO ->
                    scheduler.schedule(iw.item.id, iw.item.autoIntervalMs)
                SwitchMode.MANUAL -> {}
            }
        }
    }

    private suspend fun loadAssets(combo: ComboWithItems) {
        val ids = combo.items.flatMap { i -> i.contents.map { it.photoId } }.distinct()
        assets.ensureMeta(ids)
        preloadAround()
    }

    /** Preload активного и соседнего контента для всех items. */
    fun preloadAround() {
        viewModelScope.launch {
            val combo = _state.value.combo ?: return@launch
            for (iw in combo.items) {
                if (iw.contents.isEmpty()) continue
                val cur = animator.currentIndex(iw.item.id).coerceIn(0, iw.contents.size - 1)
                val next = nextIndex(cur, iw.contents.size) ?: continue
                listOf(cur, next).forEach { idx ->
                    iw.contents.getOrNull(idx)?.let { ensureBitmap(it.photoId) }
                }
            }
        }
    }

    private suspend fun ensureBitmap(photoId: String) {
        // Пропуск «уже есть и этого размера достаточно» — забота PhotoAssets; нам нужен
        // только факт, что что-то обновилось, чтобы перерисовать кадр.
        if (assets.ensureBitmap(photoId, screenMaxPx)) bump()
    }

    fun frameFor(item: ComboItemEntity, contents: List<ComboItemContentEntity>): ItemFrame? {
        if (contents.isEmpty()) return null
        val curIdx = animator.currentIndex(item.id).coerceIn(0, contents.size - 1)
        val incomingC = contents[curIdx]
        val s = _state.value
        val inPhoto = s.photos[incomingC.photoId] ?: return null
        val inImg = assets.bitmaps.value[incomingC.photoId] ?: return null
        val anim = animator.frame(item.id)
        val (active, progress) = anim ?: Pair(null, 1f)
        val fromC = active?.let { contents.getOrNull(it.from) }
        val outImg = fromC?.let { assets.bitmaps.value[it.photoId] }
        val outPhoto = fromC?.let { s.photos[it.photoId] }
        // Одна фабрика на билдер/плеер/список: режим рамки (AutoFit), fillColor и
        // правило «нет outgoing → статичный кадр» не могут тут разойтись.
        return buildItemFrame(
            border = item.border(),
            mode = item.framingModeEnum,
            incoming = incomingC.frameContent(inImg, inPhoto),
            outgoing = if (fromC != null && outImg != null && outPhoto != null) {
                fromC.frameContent(outImg, outPhoto)
            } else {
                null
            },
            transition = active?.type,
            progress = progress,
            direction = active?.dir ?: Direction.UP,
        )
    }

    /** Ручной свайп по AUTO-элементу сбрасывает таймер. */
    fun manualSwitch(itemId: String, dir: Direction) {
        val combo = _state.value.combo ?: return
        val iw = combo.items.firstOrNull { it.item.id == itemId } ?: return
        // Одно фото — переключаться не на что; таймер при этом трогать нельзя, поэтому
        // reschedule стоит после этой проверки.
        if (iw.contents.size < 2) return
        if (iw.item.switchModeEnum != SwitchMode.MANUAL) scheduler.reschedule(itemId)
        val cur = animator.currentIndex(itemId)
        // Для горизонтального жеста cycleIndex вернёт null: такой жест фото не переключает.
        val target = cycleIndex(cur, iw.contents.size, dir) ?: return
        animator.switch(itemId, target, iw.item.manualTransitionEnum, dir)
        startTickerIfNeeded()
        preloadAround()
    }

    fun pause() = scheduler.pause()

    fun resume() {
        scheduler.resume()
        // После resume слоты снова активны — тикер должен закрутиться заново (на паузе он
        // был остановлен условием выхода).
        startTickerIfNeeded()
    }

    fun startTickerIfNeeded() = engine.startIfNeeded()

    /** Автосмена по таймеру: шаг вперёд по кругу плюс сброс таймера рамки. */
    private fun autoSwitch(itemId: String) {
        val combo = _state.value.combo ?: return
        val iw = combo.items.firstOrNull { it.item.id == itemId } ?: return
        if (iw.contents.size < 2) return
        val cur = animator.currentIndex(itemId)
        val target = nextIndex(cur, iw.contents.size) ?: return
        animator.switch(itemId, target, iw.item.autoTransitionEnum, Direction.UP)
        scheduler.reschedule(itemId)
    }

    private fun bump() = _state.update { it.copy(revision = it.revision + 1) }
}
