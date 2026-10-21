package com.combo.ui

import androidx.navigation3.runtime.NavKey

/**
 * Ключи навигации вместо строковых маршрутов: аргументы — типизированные поля, а не
 * разбор строки с разделителями (id фото не устоит перед запятой в имени файла).
 *
 * `Library` и `Combos` — корневые вкладки: у них есть нижняя панель, и их ViewModel
 * живут, пока работает приложение (переключение вкладок не сбрасывает выбор и прокрутку).
 * Остальные ключи кладутся в стек поверх вкладки и живут до «назад».
 */
sealed interface ScreenKey : NavKey {
    data object Library : ScreenKey

    data object Combos : ScreenKey

    data class Viewer(val photoId: String) : ScreenKey

    /** `comboId` — открытие существующего комбо; `photos` — порядок фото нового комбо. */
    data class Builder(val comboId: String? = null, val photos: List<String> = emptyList()) : ScreenKey
}
