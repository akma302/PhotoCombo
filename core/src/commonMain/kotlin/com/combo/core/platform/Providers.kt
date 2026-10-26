package com.combo.core.platform

/**
 * «Настенное» время для отметок в данных (`importedAt`, `updatedAt`). Отдельный тип,
 * а не `() -> Long`: Koin индексирует биндинги по классу, и два функциональных типа
 * стёрлись бы в один `Function0`, сделав биндинги неразличимыми.
 */
fun interface WallClock {
    fun nowMillis(): Long
}

/** Генератор id новых записей; инжектируется, чтобы тесты давали предсказуемые id. */
fun interface IdGenerator {
    fun newId(): String
}
