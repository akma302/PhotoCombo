package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.combo.core.background.BackgroundInput
import com.combo.core.background.BackgroundSpec
import com.combo.core.background.FrameInput
import com.combo.core.background.ResolvedBackground

/**
 * Единственная отрисовка фона комбо — её зовут билдер и превью списка, иначе
 * WYSIWYG-разъезд неизбежен (как с `drawItem()`).
 */
fun DrawScope.drawComboBackground(bg: ResolvedBackground, topLeft: Offset, size: Size) {
    when (bg) {
        is ResolvedBackground.Solid -> drawRect(
            color = Color(bg.argb),
            topLeft = topLeft,
            size = size,
        )
    }
}

/** Общий адаптер «прямоугольник + цвета фото» -> [FrameInput] для всех трёх мест. */
fun frameInputOf(border: Border, photoColors: List<Int>): FrameInput {
    val r = border.rect()
    return FrameInput(r.left, r.top, r.right, r.bottom, photoColors)
}

fun backgroundInputOf(canvasW: Float, canvasH: Float, frames: List<FrameInput>) =
    BackgroundInput(canvasW, canvasH, frames)

/**
 * Разрешает [spec] в рисуемый фон. Специально не кэшируется: стратегия чистая и дешёвая,
 * а кэш здесь означал бы ещё одно место, где фон может «разъехаться» с холстом.
 */
fun resolveBackground(spec: BackgroundSpec, input: BackgroundInput): ResolvedBackground =
    com.combo.core.background.BackgroundResolver.Default.resolve(spec, input)

/**
 * Фон одного комбо по данным репозитория (превью списка). Вход для стратегии
 * собирается здесь, чтобы экраны не делали это по-разному. Ключи `remember` у вызывающего
 * должны покрывать всё, от чего зависит результат: положение и размер рамок, состав
 * фото, спецификацию фона и `updatedAt` (он меняется при любой правке комбо).
 *
 * Перегрузка с [Float] — для билдера, у которого холст живёт во Float: обрезать его до
 * Int перед расчётом веса значило бы вносить в фон погрешность относительно превью списка.
 */
fun comboBackgroundOf(
    canvasW: Int,
    canvasH: Int,
    background: BackgroundSpec,
    frames: List<Pair<Border, List<Int>>>,
): ResolvedBackground = comboBackgroundOf(
    canvasW.toFloat(),
    canvasH.toFloat(),
    background,
    frames,
)

fun comboBackgroundOf(
    canvasW: Float,
    canvasH: Float,
    background: BackgroundSpec,
    frames: List<Pair<Border, List<Int>>>,
): ResolvedBackground = resolveBackground(
    background,
    backgroundInputOf(
        canvasW,
        canvasH,
        frames.map { (border, colors) -> frameInputOf(border, colors) },
    ),
)