package com.combo.core.render

import androidx.compose.ui.geometry.Offset
import com.combo.core.model.Direction
import com.combo.core.model.TransitionType

interface ContentTransition {
    fun outgoing(t: Float, dir: Direction): ContentDrawSpec
    fun incoming(t: Float, dir: Direction): ContentDrawSpec
}

data class ContentDrawSpec(
    val alpha: Float = 1f,
    val offsetNorm: Offset = Offset.Zero,
    val scale: Float = 1f,
    val wipe: Float = -1f,
) {
    companion object {
        val IDENTITY = ContentDrawSpec()
    }
}

/**
 * Объект перехода по типу. Это функция-расширение, а не метод enum: [TransitionType] лежит
 * в `core/model`, а модель не должна зависеть от кода отрисовки.
 */
fun TransitionType.create(): ContentTransition = when (this) {
    TransitionType.NONE -> None; TransitionType.FADE -> Fade; TransitionType.SLIDE -> Slide
    TransitionType.ZOOM -> Zoom; TransitionType.WIPE -> Wipe
}

object None : ContentTransition {
    override fun outgoing(t: Float, dir: Direction) = ContentDrawSpec(alpha = 1 - t)
    override fun incoming(t: Float, dir: Direction) = ContentDrawSpec.IDENTITY
}

object Fade : ContentTransition {
    override fun outgoing(t: Float, dir: Direction) = ContentDrawSpec(alpha = 1 - t)
    override fun incoming(t: Float, dir: Direction) = ContentDrawSpec(alpha = t)
}

/** outgoing уходит по направлению жеста, incoming входит с противоположной стороны. */
object Slide : ContentTransition {
    override fun outgoing(t: Float, dir: Direction) = ContentDrawSpec(offsetNorm = dir.axis() * t)
    override fun incoming(t: Float, dir: Direction) = ContentDrawSpec(offsetNorm = dir.axis() * (t - 1f))
}

object Zoom : ContentTransition {
    override fun outgoing(t: Float, dir: Direction) = ContentDrawSpec(alpha = 1 - t, scale = 1 + 0.15f * t)
    override fun incoming(t: Float, dir: Direction) = ContentDrawSpec(alpha = t, scale = 0.85f + 0.15f * t)
}

object Wipe : ContentTransition {
    override fun outgoing(t: Float, dir: Direction) = ContentDrawSpec.IDENTITY
    override fun incoming(t: Float, dir: Direction) = ContentDrawSpec(wipe = t)
}
