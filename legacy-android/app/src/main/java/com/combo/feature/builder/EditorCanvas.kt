package com.combo.feature.builder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
 * @param bitmaps поток битмапов приходит СЮДА и намеренно не читается: подписка нужна, чтобы
 *   холст перерисовывался, когда кадр сменился (состояние при этом не меняется — меняется
 *   только содержимое кэша `PhotoAssets`), поэтому параметр и остаётся неиспользуемым.
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
    val primaryColor = MaterialTheme.colorScheme.primary

    // Блоки pointerInput ниже запускаются ОДИН раз (ключ Unit) — иначе смена выделения
    // или режима посреди жеста перезапускала бы блок, обрывая жест без END (терялись
    // undo-шаг и isDirty), а замыканные сюда state/сдвиг вида устаревали бы. Поэтому всё
    // зависящее от композиции читаем через rememberUpdatedState.
    val curState = rememberUpdatedState(state)
    val curTransform = rememberUpdatedState(transform)

    EditorCanvas(
        vm = vm,
        state = state,
        transform = transform,
        handlePx = handlePx,
        primaryColor = primaryColor,
        onCanvasSized = { boxSize = it },
        curState = curState,
        curTransform = curTransform,
        modifier = modifier,
    )
}

/**
 * Полотно редактора: рамка с фоном, `Canvas` с холстом и точки страниц поверх.
 *
 * Порядок модификаторов на `Canvas` значим: сначала `detectTapGestures`, затем жесты
 * [editorGestures] — оба блока живут с ключом `Unit` и оба получают события в этом порядке.
 */
@Composable
private fun EditorCanvas(
    vm: ComboBuilderViewModel,
    state: BuilderUiState,
    transform: CanvasTransform,
    handlePx: Float,
    primaryColor: Color,
    onCanvasSized: (IntSize) -> Unit,
    curState: State<BuilderUiState>,
    curTransform: State<CanvasTransform>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
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
                .editorGestures(vm, handlePx, curState, curTransform),
        ) {
            // Холст и рамки — единым вызовом (тот же transform, тот же фон и тот же
            // drawItem, что в плеере и списке). Сбор кадров остаётся ЗДЕСЬ, внутри
            // draw-лямбды: поднять его в композицию значило бы читать состояние при
            // отрисовке и перерисовывать холст на каждой смене ревизии без нужды.
            drawCombo(
                transform,
                state.canvasW,
                state.canvasH,
                // Фон — та же функция и тот же BackgroundSpec, что в плеере и списке.
                comboBackgroundOf(
                    state.canvasW,
                    state.canvasH,
                    state.background,
                    state.items.map { item ->
                        item.border() to
                            item.contents.mapNotNull { state.photos[it.photoId]?.avgColor }
                    },
                ),
                // Порядок как в state.items: редактор не сортирует рамки по zIndex,
                // его порядок задают ReorderItem и zIndex драфтов.
                state.items.mapNotNull { vm.frameFor(it) },
            )
            drawSelectionOverlay(state, transform, primaryColor)
        }

        PageDots(vm, state, transform)
    }
}