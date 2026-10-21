package com.combo.core.util

/**
 * Следующее значение enum'а по кругу: после последнего снова первое.
 *
 * Порядок — порядок объявления. На нём построены кнопки тулбара (`MANUAL → AUTO →
 * MANUAL_AUTO`, `NONE → FADE → SLIDE → ZOOM → WIPE`), поэтому перестановка значений в enum'е
 * меняет порядок нажатий; это проверяет `EnumCycleTest`.
 */
inline fun <reified E : Enum<E>> E.next(): E = enumValues<E>()[(ordinal + 1) % enumValues<E>().size]
