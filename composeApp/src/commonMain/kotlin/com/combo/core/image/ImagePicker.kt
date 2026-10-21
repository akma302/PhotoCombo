package com.combo.core.image

/**
 * Выбор фото у пользователя. Возвращает пустой список при отмене диалога:
 * импорт и так считает пустой набор успешным результатом.
 */
interface ImagePicker {
    suspend fun pickImages(): List<ImageSource>
}
