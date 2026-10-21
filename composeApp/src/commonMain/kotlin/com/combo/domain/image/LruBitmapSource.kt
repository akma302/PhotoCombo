package com.combo.domain.image

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.image.ImageCodec
import com.combo.data.PhotoDao
import com.combo.data.PhotoStorage
import com.combo.domain.BitmapSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Битмапный кэш с дедупликацией одновременных декодов.
 *
 * Кэш — `LinkedHashMap` в порядке доступа под [lock], а не `androidx.collection.LruCache`:
 * ключ — пара (photoId, sampleSize), размер записи считается в КБ и учитывает только
 * пиксели, поэтому ручная политика вытеснения проще и предсказуемее, чем чужая.
 *
 * Одновременные запросы одного ключа (photoId, sample) сводятся к одному декодированию,
 * а запросы разных ключей идут параллельно: фото — независимые файлы, и общая блокировка
 * на всё приложение только замедляла бы комбо, которому нужно разом много битмапов
 * (до ~10 рамок по ~5 фото).
 *
 * [decodeScope] не привязан к корутине вызывающего: если ViewModel, запустившая
 * декодирование, уничтожена, декодирование продолжится для остальных, кто ждёт тот же
 * ключ, а не отменится вместе с первой.
 */
class LruBitmapSource(
    private val photoDao: PhotoDao,
    private val storage: PhotoStorage,
    private val codec: ImageCodec,
    private val maxCacheKb: Int,
    decodeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BitmapSource {

    private val scope = decodeScope
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap>>()

    /** Ключи кэша от самого старого к самому свежему; для тестов политики вытеснения. */
    internal val cachedKeys: List<String>
        get() = cache.keys.toList()

    /**
     * Текущий размер кэша в КБ — в тех же единицах, что и [maxCacheKb]. Читается тестами
     * между декодированиями; в рабочем коде изменяется только под [lock].
     */
    internal var currentCacheSizeKb = 0
        private set

    override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap {
        val photo = photoDao.get(photoId) ?: error("Unknown photo $photoId")
        val sample = sampleSizeFor(photo.width, photo.height, requiredPx.coerceAtLeast(1))
        val key = "$photoId:$sample"

        lock.withLock { getLocked(key) }?.let { return it }

        val deferred = lock.withLock {
            inFlight[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    val file = storage.photoPath(photo.fileName)
                    val image = codec.decode(file, sample) ?: error("Cannot decode ${file.name}")
                    lock.withLock { putLocked(key, image) }
                    image
                } finally {
                    lock.withLock { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return deferred.await()
    }

    /**
     * Размер записи кэша в КБ: 4 байта на пиксель (ARGB_8888 — формат битмапов Compose).
     * Ноль не возвращаем, иначе запись «бесплатна» и никогда не вытесняется.
     */
    internal fun cacheSizeKb(value: ImageBitmap): Int =
        max(1, (value.width.toLong() * value.height * 4 / 1024).toInt())

    private fun getLocked(key: String): ImageBitmap? {
        val image = cache.remove(key) ?: return null
        cache[key] = image
        return image
    }

    private fun putLocked(key: String, image: ImageBitmap) {
        cache.remove(key)?.let { currentCacheSizeKb -= cacheSizeKb(it) }
        cache[key] = image
        currentCacheSizeKb += cacheSizeKb(image)

        val iterator = cache.entries.iterator()
        while (currentCacheSizeKb > maxCacheKb && iterator.hasNext()) {
            val entry = iterator.next()
            currentCacheSizeKb -= cacheSizeKb(entry.value)
            iterator.remove()
        }
    }

    private fun sampleSizeFor(srcW: Int, srcH: Int, requiredPx: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (max(w, h) / 2 >= requiredPx * 2) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }
}
