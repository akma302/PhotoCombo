package com.combo.core.background

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Фон комбо — это ОПИСАНИЕ (рецепт), а не готовый цвет: `Auto` вычисляется по текущему
 * содержимому холста, поэтому одна и та же запись остаётся «живой» при смене фото.
 * Хранится в Room как JSON-строка, отсюда `@Serializable`.
 *
 * Новые варианты (градиент и т.п.) добавляются здесь же; все `when` по [BackgroundSpec]
 * и [ResolvedBackground] в проекте сделаны исчерпывающими, без `else`.
 */
@Serializable
sealed interface BackgroundSpec {
    /** Рецепт, а не цвет: цвет вычисляется стратегией по текущему содержимому. */
    @Serializable
    @SerialName("auto")
    data class Auto(val strategy: String = AverageAreaOklabStrategy.ID) : BackgroundSpec

    @Serializable
    @SerialName("solid")
    data class Solid(val argb: Int) : BackgroundSpec
}

/** Результат вычисления: то, что реально рисуется. */
sealed interface ResolvedBackground {
    data class Solid(val argb: Int) : ResolvedBackground
}

/** Одна рамка как вход для стратегии: прямоугольник в координатах холста + цвета фото. */
data class FrameInput(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** `PhotoEntity.avgColor` ВСЕХ фото рамки, а не только активного. */
    val photoColors: List<Int>,
)

data class BackgroundInput(
    val canvasW: Float,
    val canvasH: Float,
    val frames: List<FrameInput>,
)

interface BackgroundStrategy {
    val id: String

    /** Чистая и дешёвая функция (вызывается из отрисовки). */
    fun resolve(input: BackgroundInput): ResolvedBackground
}

/**
 * `Solid` идёт как есть, `Auto` — через зарегистрированную стратегию. Неизвестный id
 * стратегии (например, из более новой версии приложения) молча уходит на дефолтную.
 */
class BackgroundResolver(
    strategies: List<BackgroundStrategy>,
    private val default: BackgroundStrategy,
) {
    private val byId: Map<String, BackgroundStrategy> = strategies.associateBy { it.id }

    fun resolve(spec: BackgroundSpec, input: BackgroundInput): ResolvedBackground =
        when (spec) {
            is BackgroundSpec.Solid -> ResolvedBackground.Solid(spec.argb)
            is BackgroundSpec.Auto -> (byId[spec.strategy] ?: default).resolve(input)
        }

    companion object {
        val Default = BackgroundResolver(listOf(AverageAreaOklabStrategy), AverageAreaOklabStrategy)
    }
}

/**
 * Пресеты кнопки BG. `next` — чистая функция, чтобы цикл фона не зависел от
 * локального индекса в тулбаре (иначе состояние кнопки расходилось с `state.background`).
 */
object BackgroundPresets {

    val cycle: List<BackgroundSpec> = listOf(
        BackgroundSpec.Auto(),
        BackgroundSpec.Solid(0xFFFFFFFF.toInt()),
        BackgroundSpec.Solid(0xFF000000.toInt()),
        BackgroundSpec.Solid(0xFF808080.toInt()),
    )

    /** Если текущего значения нет в цикле — возвращается первый пресет. */
    fun next(current: BackgroundSpec): BackgroundSpec {
        val idx = cycle.indexOfFirst { it == current }
        return if (idx < 0) cycle.first() else cycle[(idx + 1) % cycle.size]
    }

    /** Подпись для кнопки: BG Auto / White / Black / Gray / Custom. */
    fun label(spec: BackgroundSpec): String = when (spec) {
        is BackgroundSpec.Auto -> "Auto"
        is BackgroundSpec.Solid -> when (spec.argb) {
            0xFFFFFFFF.toInt() -> "White"
            0xFF000000.toInt() -> "Black"
            0xFF808080.toInt() -> "Gray"
            else -> "Custom"
        }
    }
}