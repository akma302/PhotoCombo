package com.combo.feature.combo_viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.combo.core.playback.BindPlaybackLifecycle
import com.combo.core.render.CanvasTransform
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.core.render.flingDirection
import com.combo.core.render.safeVelocity
import com.combo.data.ComboWithItems
import com.combo.data.backgroundFrames
import com.combo.data.border
import com.combo.ui.common.BackNavIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboViewerScreen(
    onBack: () -> Unit,
    vm: ComboViewerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bitmaps by vm.bitmaps.collectAsStateWithLifecycle()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    BindPlaybackLifecycle(onStop = vm::pause, onStart = vm::resume)

    LaunchedEffect(boxSize) {
        if (boxSize != IntSize.Zero) {
            vm.setScreenSize(boxSize.width, boxSize.height)
            vm.preloadAround()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { BackNavIcon(onBack) },
            title = { Text(state.combo?.combo?.name ?: "") },
        )
    }) { padding ->
        val combo = state.combo ?: return@Scaffold

        val cw = combo.combo.canvasWidth.toFloat()
        val ch = combo.combo.canvasHeight.toFloat()
        val sw = boxSize.width.coerceAtLeast(1).toFloat()
        val sh = boxSize.height.coerceAtLeast(1).toFloat()
        // Тот же вписывающий сдвиг, что в билдере и в списке (без зума и pan).
        val transform = CanvasTransform.fitInto(cw, ch, sw, sh)

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { boxSize = it }
                .pointerInput(combo.combo.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val main = pressed.first()
                            tracker.addPosition(main.uptimeMillis, main.position)
                            event.changes.forEach { it.consume() }
                        }
                        // Порог и направление — те же, что в билдере: safeVelocity гасит исключение
                        // calculateVelocity(), flingDirection возвращает null для медленного
                        // и горизонтального жеста — то есть такой жест ничего не делает.
                        val dir = flingDirection(tracker.safeVelocity())
                        if (dir != null) {
                            val hitId = hitTestItem(state.combo, transform.toCanvas(down.position))
                            if (hitId != null) vm.manualSwitch(hitId, dir)
                        }
                    }
                },
        ) {
            key(state.revision) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCombo(
                        transform,
                        cw,
                        ch,
                        // Фон — та же функция и тот же BackgroundSpec, что в билдере и списке.
                        comboBackgroundOf(
                            combo.combo.canvasWidth,
                            combo.combo.canvasHeight,
                            combo.combo.background,
                            combo.backgroundFrames(state.photos),
                        ),
                        // Порядок слоёв: по возрастанию zIndex, последний — сверху.
                        combo.items.sortedBy { it.item.zIndex }.mapNotNull { iw ->
                            vm.frameFor(iw.item, iw.contents)
                        },
                    )
                }
            }
        }
    }
}

internal fun hitTestItem(combo: ComboWithItems?, canvasPoint: Offset): String? {
    if (combo == null) return null
    // Именно ВЕРХНЯЯ рамка: слои рисуются по возрастанию zIndex, значит последняя
    // нарисованная лежит сверху. firstOrNull отдавал нижнюю — свайп уходил не туда.
    return combo.items.sortedByDescending { it.item.zIndex }.firstOrNull { iw ->
        iw.item.border().rect().contains(canvasPoint)
    }?.item?.id
}
