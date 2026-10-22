package com.combo.feature.builder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.combo.ui.common.designsystem.theme.PcTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.combo.core.render.CanvasTransform
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo

/**
 * Вьюпорт редактора: следит за размером экрана, считает [CanvasTransform] и рисует холст.
 *
 * @param bitmaps загруженные битмапы. Кэш `PhotoAssets` — не Compose-состояние, поэтому сама
 *   отрисовка о новых битмапах не узнаёт: параметр доходит до [EditorCanvas] и захватывается
 *   draw-лямбдой, а смена его значения даёт новую лямбду и перерисовку холста. Без этого
 *   рамки, чьё фото загрузилось позже первой, не появлялись до следующего события.
 */
@Composable
internal fun EditorViewport(
    vm: ComboBuilderViewModel,
    state: BuilderUiState,
    bitmaps: Map<String, ImageBitmap>,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(boxSize) {
        if (boxSize != IntSize.Zero) {
            vm.onEvent(BuilderEvent.SetScreenSize(boxSize.width.toFloat(), boxSize.height.toFloat()))
            vm.refreshBitmaps(immediate = true)
        }
    }

    val cw = state.canvasW
    val ch = state.canvasH
    val sw = state.screenW.coerceAtLeast(1f)
    val sh = state.screenH.coerceAtLeast(1f)
    val transform = CanvasTransform.ofViewport(state.viewport, Size(cw, ch), Size(sw, sh))

    val density = LocalDensity.current
    val handlePx = with(density) { 24.dp.toPx() }
    val primaryColor = PcTheme.colors.accent

    // Блоки pointerInput ниже запускаются ОДИН раз (ключ Unit) — иначе смена выделения
    // или режима посреди жеста перезапускала бы блок, обрывая жест без END (терялись
    // undo-шаг и isDirty), а замыканные сюда state/сдвиг вида устаревали бы. Поэтому всё
    // зависящее от композиции читаем через rememberUpdatedState.
    val curState = rememberUpdatedState(state)
    val curTransform = rememberUpdatedState(transform)
    val curHandlePx = rememberUpdatedState(handlePx)

    EditorCanvas(
        vm = vm,
        state = state,
        transform = transform,
        bitmaps = bitmaps,
        primaryColor = primaryColor,
        onCanvasSized = { boxSize = it },
        curState = curState,
        curTransform = curTransform,
        curHandlePx = curHandlePx,
        modifier = modifier,
    )
}

/**
 * Полотно редактора: рамка с фоном и `Canvas` с холстом.
 *
 * Порядок модификаторов на `Canvas` значим: сначала `detectTapGestures`, затем жесты
 * [editorGestures] — оба блока живут с ключом `Unit` и оба получают события в этом порядке.
 */
@Composable
private fun EditorCanvas(
    vm: ComboBuilderViewModel,
    state: BuilderUiState,
    transform: CanvasTransform,
    bitmaps: Map<String, ImageBitmap>,
    primaryColor: Color,
    onCanvasSized: (IntSize) -> Unit,
    curState: State<BuilderUiState>,
    curTransform: State<CanvasTransform>,
    curHandlePx: State<Float>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PcTheme.colors.workspace)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(EDITOR_CANVAS_TAG)
                .onSizeChanged(onCanvasSized)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { p ->
                            val id = vm.hitTest(curTransform.value.toCanvas(p))
                            if (id == null) vm.onEvent(BuilderEvent.CanvasTapped)
                            else vm.onEvent(BuilderEvent.ItemTapped(id))
                        },
                        onDoubleTap = { vm.onEvent(BuilderEvent.ToggleMode) },
                    )
                }
                .editorGestures(vm, curHandlePx, curState, curTransform)
                // Колесо — отдельный цикл: у него нет UP-события, серию тиков завершает
                // таймаут (см. editorWheelZoom).
                .editorWheelZoom(vm, curState, curTransform),
        ) {
            // Захват [bitmaps] draw-лямбдой: см. KDoc EditorViewport.
            val loaded = bitmaps.size
            // Холст и рамки — единым вызовом (тот же transform, тот же фон и тот же
            // drawItem, что в списке комбо). Сбор кадров остаётся ЗДЕСЬ, внутри
            // draw-лямбды: поднять его в композицию значило бы читать состояние при
            // отрисовке и перерисовывать холст на каждой смене ревизии без нужды.
            drawCombo(
                transform,
                state.canvasW,
                state.canvasH,
                // Фон — та же функция и тот же BackgroundSpec, что в списке комбо.
                comboBackgroundOf(
                    state.canvasW,
                    state.canvasH,
                    state.background,
                    state.items.map { item ->
                        item.border() to listOfNotNull(state.photos[item.photoId]?.avgColor)
                    },
                ),
                // Порядок как в state.items: редактор не сортирует рамки по zIndex,
                // его порядок задают ReorderItem и zIndex драфтов.
                state.items.mapNotNull { if (loaded == 0) null else vm.frameFor(it) },
            )
            drawSelectionOverlay(state, transform, primaryColor)
        }
    }
}
