package com.combo.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственное место, где решается, где на диске лежат оригиналы фото и миниатюры.
 * Импорт, удаление, загрузка битмапов и превью берут пути только отсюда, поэтому
 * они не могут разойтись.
 *
 * ВАЖНО: раскладка каталогов — это данные пользователя на диске, менять её нельзя:
 * оригиналы лежат в `filesDir/photos/<fileName>`, миниатюры в `filesDir/thumbs/<photoId>.jpg`.
 * Любое изменение (нормализация имён, `canonicalPath`, другая папка) «потеряет» все уже
 * импортированные фото после обновления приложения.
 */
@Singleton
class PhotoStorage @Inject constructor(@ApplicationContext private val context: Context) {

    /** Каталог оригиналов: `filesDir/photos`. */
    fun photosDir(): File = File(context.filesDir, "photos")

    /** Файл оригинала по имени из БД (`PhotoEntity.fileName`). */
    fun photoFile(fileName: String): File = File(photosDir(), fileName)

    /** Файл миниатюры по id фото: `filesDir/thumbs/<photoId>.jpg`. */
    fun thumbFile(photoId: String): File = File(thumbsDir(), "$photoId.jpg")

    /**
     * Создаёт каталоги и файл `.nomedia` (чтобы галерея и медиасканер не показывали
     * приватные фото приложения). Вызывается перед импортом.
     */
    fun ensureDirs() {
        photosDir().mkdirs()
        File(photosDir(), ".nomedia").createNewFile()
        thumbsDir().mkdirs()
    }

    private fun thumbsDir(): File = File(context.filesDir, "thumbs")
}