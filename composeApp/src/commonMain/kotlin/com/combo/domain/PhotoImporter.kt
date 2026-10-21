package com.combo.domain

import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.image.ImageSource
import com.combo.core.image.applyExifOrientation
import com.combo.core.platform.Logger
import com.combo.core.util.AverageColor
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSink
import okio.IOException
import okio.Path
import okio.Source
import okio.blackholeSink
import okio.buffer

private const val TAG = "PhotoRepository"

/**
 * Конвейер импорта фото: дедуп по SHA-256, копирование оригинала, нормализация EXIF,
 * миниатюра + avgColor одним проходом декодирования, запись строки в БД.
 *
 * Единственное место, где создаются файлы фото и миниатюр, и единственное — где
 * читается EXIF-ориентация: после успешного импорта ориентация нигде не учитывается.
 * Пути на диске — только через [storage].
 *
 * Время ([nowMillis]) и id ([newId]) инжектируются, чтобы тесты были детерминированными.
 */
class PhotoImporter(
    private val photoDao: PhotoDao,
    private val storage: PhotoStorage,
    private val codec: ImageCodec,
    private val files: FileSystem,
    private val logger: Logger,
    private val nowMillis: () -> Long,
    private val newId: () -> String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun importPhotos(sources: List<ImageSource>): ImportResult = withContext(ioDispatcher) {
        storage.ensureDirs()

        var added = 0
        var duplicates = 0
        var failed = 0

        for (source in sources) {
            // id и имя файла — для уборки, если сбой случится до записи строки в БД.
            var created: Pair<String, String>? = null
            try {
                val hash = sha256(source)
                if (hash == null) {
                    failed++
                    continue
                }
                if (photoDao.findByHash(hash) != null) {
                    duplicates++
                    continue
                }

                val id = newId()
                val ext = extensionForMime(source.mimeType)
                val fileName = "$id.$ext"
                created = id to fileName
                val file = storage.photoPath(fileName)
                if (!copyTo(source, file)) {
                    storage.deletePhotoFiles(fileName, id)
                    failed++
                    continue
                }

                val bounds = codec.readBounds(file)
                if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                    storage.deletePhotoFiles(fileName, id)
                    failed++
                    continue
                }
                var width = bounds.width
                var height = bounds.height

                val orientation = codec.readOrientation(file)
                if (orientation != ExifOrientation.NORMAL) {
                    val normalized = normalizeExif(file, orientation, ext)
                    if (normalized == null) {
                        storage.deletePhotoFiles(fileName, id)
                        failed++
                        continue
                    }
                    width = normalized.first
                    height = normalized.second
                }

                val avgColor = writeThumbnailAndComputeAvgColor(file, id, width, height)
                if (avgColor == null) {
                    storage.deletePhotoFiles(fileName, id)
                    failed++
                    continue
                }

                val entity = PhotoEntity(
                    id = id,
                    fileName = fileName,
                    originalName = source.name,
                    mimeType = source.mimeType,
                    width = width,
                    height = height,
                    sizeBytes = files.metadata(file).size ?: 0L,
                    sha256 = hash,
                    avgColor = avgColor,
                    importedAt = nowMillis(),
                )
                try {
                    photoDao.insert(entity)
                    added++
                } catch (e: Exception) {
                    logger.warn(TAG, "Failed to insert photo $id after decode/copy succeeded", e)
                    storage.deletePhotoFiles(fileName, id)
                    failed++
                }
            } catch (e: Exception) {
                logger.warn(TAG, "Failed to import ${source.name}", e)
                created?.let { (id, fileName) -> storage.deletePhotoFiles(fileName, id) }
                failed++
            }
        }
        ImportResult(added, duplicates, failed)
    }

    /** Обёртка над [Companion.sha256Hex]: источник байтов и логирование — здесь, чистый хеш — там. */
    private fun sha256(source: ImageSource): String? = try {
        source.openSource().use { Companion.sha256Hex(it) }
    } catch (e: IOException) {
        logger.warn(TAG, "Failed to hash ${source.name}", e)
        null
    }

    private fun copyTo(source: ImageSource, dest: Path): Boolean = try {
        source.openSource().use { input ->
            files.write(dest) { writeAll(input) }
        }
        true
    } catch (e: IOException) {
        logger.warn(TAG, "Failed to copy ${source.name}", e)
        false
    }

    /**
     * EXIF-нормализация при импорте: после неё ориентацию нигде больше не учитываем.
     * Пиксели файла поворачиваются и отражаются так, чтобы картинка была «правильной», а
     * файл перезаписывается на месте. Ошибка здесь необратима: неверно перевёрнутое
     * изображение остаётся таким навсегда.
     */
    private fun normalizeExif(file: Path, orientation: ExifOrientation, ext: String): Pair<Int, Int>? {
        val bitmap = codec.decode(file) ?: return null
        val normalized = applyExifOrientation(bitmap, orientation)
        val format = if (ext == "png") ImageFormat.PNG else ImageFormat.JPEG
        if (!codec.encode(file, normalized, format, NORMALIZED_QUALITY)) return null
        return normalized.width to normalized.height
    }

    /**
     * Пишет миниатюру и считает средний цвет за ОДИН проход декодирования: оба результата
     * берутся из одного и того же уменьшенного битмапа. Размеры [width] и [height] уже
     * известны вызывающему (после `readBounds` или `normalizeExif`), поэтому границы
     * повторно не читаются.
     */
    private fun writeThumbnailAndComputeAvgColor(src: Path, id: String, width: Int, height: Int): Int? {
        val bitmap = codec.decode(src, sampleSizeForThumbnail(width, height)) ?: return null
        val color = AverageColor.of(bitmap)
        if (!codec.encode(storage.thumbPath(id), bitmap, ImageFormat.JPEG, THUMB_QUALITY)) return null
        return color
    }

    /**
     * Чистые куски импорта — без файловой системы и логов, чтобы их можно было покрыть
     * обычными JVM-тестами. `internal`, а не `private`: тесты лежат в том же модуле,
     * а наружу (в пакеты `feature`) эти функции стыковаться не должны.
     */
    internal companion object {

        /** Качество перезаписи нормализованного файла и миниатюры. */
        const val NORMALIZED_QUALITY = 95
        const val THUMB_QUALITY = 85

        /**
         * SHA-256 потока в виде hex. Поток закрывает вызывающий. `String?` — из требования
         * к API: единственный источник «нет хеша» это `openSource`, и он проверяется
         * в обёртке `sha256(source)`.
         */
        fun sha256Hex(source: Source): String? {
            val hashing = HashingSink.sha256(blackholeSink())
            try {
                source.buffer().readAll(hashing)
                return hashing.hash.hex()
            } finally {
                hashing.close()
            }
        }

        /** Расширение файла оригинала по mime-типу выбранного фото. */
        fun extensionForMime(mimeType: String): String = when {
            mimeType.contains("png", true) -> "png"
            mimeType.contains("webp", true) -> "webp"
            else -> "jpg"
        }

        /** Степень сэмплирования для миниатюры: длинная сторона должна влезть в 512 px. */
        fun sampleSizeForThumbnail(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / sample > 512) sample *= 2
            return sample
        }
    }
}
