package com.combo.data

import com.combo.core.platform.AppDirs
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Раскладка файлов на диске: `photos/<fileName>` — оригиналы, приведённые при
 * импорте к нормальной ориентации, `thumbs/<photoId>.jpg` — миниатюры превью.
 * Раскладка — пользовательские данные: пути и имена не меняются, база хранит
 * только имя файла, а не путь.
 */
class PhotoStorage(private val dirs: AppDirs, private val files: FileSystem) {

    fun photosDir(): Path = dirs.dataDir / "photos"

    fun thumbsDir(): Path = dirs.dataDir / "thumbs"

    fun photoPath(fileName: String): Path = photosDir() / fileName

    fun thumbPath(photoId: String): Path = thumbsDir() / "$photoId.jpg"

    fun ensureDirs() {
        files.createDirectories(photosDir())
        files.createDirectories(thumbsDir())
    }

    /**
     * Удаляет файлы фото (оригинал и миниатюру). Best-effort: остатки мусора
     * не должны мешать откату неудачного импорта или удалению записи из базы.
     */
    fun deletePhotoFiles(fileName: String, photoId: String) {
        deleteQuietly(photoPath(fileName))
        deleteQuietly(thumbPath(photoId))
    }

    private fun deleteQuietly(path: Path) {
        try {
            files.delete(path)
        } catch (_: IOException) {
        }
    }
}
