package com.combo.core.image

import okio.Source

/**
 * Один выбранный пользователем файл-изображение. Источник открывается повторно —
 * импорт читает его несколько раз (хеш, копия, декод), поэтому [openSource]
 * обязан возвращать новый поток при каждом вызове.
 */
interface ImageSource {
    val name: String
    val mimeType: String

    fun openSource(): Source
}
