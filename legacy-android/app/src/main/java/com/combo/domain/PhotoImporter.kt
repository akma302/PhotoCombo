package com.combo.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.combo.core.util.AverageColor
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PhotoRepository"

/**
 * Конвейер импорта фото: дедуп по SHA-256, копирование оригинала, нормализация EXIF,
 * миниатюра + avgColor одним проходом декодирования, запись строки в БД.
 *
 * Вынесен из `PhotoRepositoryImpl` без изменения поведения (те же ветки отказа, те же
 * тексты логов, тот же `TAG`, те же счётчики `ImportResult`): репозиторий остаётся
 * владельцем сущностей, `PhotoImporter` — единственным, кто создаёт новые файлы.
 *
 * Пути на диске — только через [storage], чтобы «оригиналы и миниатюры лежат здесь»
 * решалось в одном месте.
 */
@Singleton
class PhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao,
    private val storage: PhotoStorage,
) {

    suspend fun importPhotos(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        storage.ensureDirs()

        var added = 0
        var duplicates = 0
        var failed = 0

        for (uri in uris) {
            try {
                val hash = sha256(uri)
                if (hash == null) { failed++; continue }
                if (photoDao.findByHash(hash) != null) { duplicates++; continue }

                val id = UUID.randomUUID().toString()
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = extensionForMime(mimeType)
                val originalName = queryDisplayName(uri)
                val file = storage.photoFile("$id.$ext")
                if (!copyTo(uri, file)) { failed++; continue }

                var bounds = decodeBounds(file)
                if (bounds == null || bounds.first <= 0) { file.delete(); failed++; continue }
                var (width, height) = bounds

                val orientation = readOrientation(file)
                if (orientation != ExifInterface.ORIENTATION_NORMAL &&
                    orientation != ExifInterface.ORIENTATION_UNDEFINED
                ) {
                    val normalized = normalizeExif(file, orientation, ext)
                    if (normalized != null) {
                        width = normalized.first
                        height = normalized.second
                    } else {
                        file.delete(); failed++; continue
                    }
                }

                val avgColor = writeThumbnailAndComputeAvgColor(file, id, width, height)
                if (avgColor == null) { file.delete(); failed++; continue }

                val entity = PhotoEntity(
                    id = id,
                    fileName = file.name,
                    originalName = originalName,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    sizeBytes = file.length(),
                    sha256 = hash,
                    avgColor = avgColor,
                    importedAt = System.currentTimeMillis(),
                )
                try {
                    photoDao.insert(entity)
                    added++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to insert photo $id after decode/copy succeeded", e)
                    file.delete()
                    storage.thumbFile(id).delete()
                    failed++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to import $uri", e)
                failed++
            }
        }
        ImportResult(added, duplicates, failed)
    }

    /** Обёртка над [Companion.sha256Hex]: источник байтов и логирование — здесь, чистый хеш — там. */
    private fun sha256(uri: Uri): String? = try {
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) null else stream.use { Companion.sha256Hex(it) }
    } catch (e: IOException) {
        Log.w(TAG, "Failed to hash $uri", e)
        null
    }

    private fun copyTo(uri: Uri, dest: File): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } != null
    } catch (e: IOException) {
        Log.w(TAG, "Failed to copy $uri", e)
        false
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query display name for $uri", e)
        null
    }

    private fun decodeBounds(file: File): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth <= 0) null else opts.outWidth to opts.outHeight
    }

    private fun readOrientation(file: File): Int = try {
        file.inputStream().use { ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read EXIF orientation for ${file.name}", e)
        ExifInterface.ORIENTATION_NORMAL
    }

    /**
     * EXIF-нормализация при импорте: после неё ориентацию нигде больше не учитываем.
     * Пиксели файла поворачиваются и отражаются так, чтобы картинка была «правильной», а
     * файл перезаписывается на месте. Ошибка здесь необратима: неверно перевёрнутое
     * изображение остаётся таким навсегда.
     *
     * Все восемь значений ориентации обрабатываются явно (`NORMAL` и неизвестные значения
     * оставляют файл без изменений). `FLIP_HORIZONTAL` и `FLIP_VERTICAL` — простые
     * зеркала по одной оси. `TRANSPOSE` и `TRANSVERSE` — зеркало, совмещённое с поворотом
     * на 90°; встречаются редко (отдельные модели камер и сканеров).
     *
     * НЕ ПРОВЕРЕНО на реальных файлах: ветки `TRANSPOSE` и `TRANSVERSE`. Есть подозрение,
     * что порядок «зеркало, поворот» в них перепутан между собой: в известной реализации
     * (Glide) для `TRANSPOSE` сначала поворот на 90°, потом зеркало по X, здесь наоборот.
     * До исправления нужно проверить на фото с тегом ориентации 5 и 7.
     */
    private fun normalizeExif(file: File, orientation: Int, ext: String): Pair<Int, Int>? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val m = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                else -> {}
            }
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val ok = file.outputStream().use { out ->
            val format = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            rotated.compress(format, 95, out)
        }
        val w = rotated.width
        val h = rotated.height
        bmp.recycle()
        if (rotated !== bmp) rotated.recycle()
        return if (ok) w to h else null
    }

    /**
     * Пишет миниатюру и считает средний цвет за ОДИН проход декодирования: оба результата
     * берутся из одного и того же уменьшенного битмапа. Размеры [width] и [height] уже
     * известны вызывающему (после `decodeBounds` или `normalizeExif`), поэтому границы
     * повторно не читаются.
     */
    private fun writeThumbnailAndComputeAvgColor(src: File, id: String, width: Int, height: Int): Int? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Companion.sampleSizeForThumbnail(width, height)
        }
        val bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return null
        val color = AverageColor.of(bmp)
        val ok = storage.thumbFile(id).outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bmp.recycle()
        return if (ok) color else null
    }

    /**
     * Чистые куски импорта — без `contentResolver`, без БД и без логов, чтобы их можно
     * было покрыть обычными JVM-тестами. `internal`, а не `private`: тесты лежат в том же
     * модуле, а наружу (в пакеты `feature`) эти функции стыковаться не должны.
     */
    internal companion object {

        /**
         * SHA-256 потока в виде hex. Поток закрывает вызывающий (в импорте — `use {}`).
         * Тип `String?` — из требования к API: единственный источник «нет хеша» это
         * `contentResolver.openInputStream`, и он проверяется в обёртке `sha256(uri)`.
         */
        fun sha256Hex(input: InputStream): String? {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Расширение файла оригинала по mime-типу от ContentResolver. */
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