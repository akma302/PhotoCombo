package com.combo.domain.image

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.combo.data.PhotoDao
import com.combo.data.PhotoStorage
import com.combo.domain.BitmapSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LruBitmapSource @Inject constructor(
    private val photoDao: PhotoDao,
    private val storage: PhotoStorage,
) : BitmapSource {

    internal val cache = object : LruCache<String, ImageBitmap>(max(4, maxMemory() / 4)) {
        // Ёмкость LruCache задана в КБ (maxMemory() возвращает КБ), поэтому и sizeOf обязан
        // возвращать КБ. Если считать в байтах, одна картинка 2048 px (~16 МБ = 16 777 216
        // «единиц») превысит потолок ~1 048 576 и кэш будет вытеснять всё подряд.
        override fun sizeOf(key: String, value: ImageBitmap): Int = cacheSizeKb(value)
    }

    // Одновременные запросы одного ключа (photoId, sample) сводятся к одному декодированию,
    // а запросы разных ключей идут параллельно: фото — независимые файлы, и общая
    // блокировка на всё приложение только замедляла бы комбо, которому нужно разом много
    // битмапов (до ~10 рамок по ~5 фото).
    //
    // `scope` не привязан к корутине вызывающего: если ViewModel, запустившая декодирование,
    // уничтожена, декодирование продолжится для остальных, кто ждёт тот же ключ, а не
    // отменится вместе с первой.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap>>()

    override suspend fun get(photoId: String, requiredPx: Int): ImageBitmap {
        val photo = photoDao.get(photoId) ?: error("Unknown photo $photoId")
        val sample = sampleSizeFor(photo.width, photo.height, requiredPx.coerceAtLeast(1))
        val key = "$photoId:$sample"

        cache.get(key)?.let { return it }

        val deferred = inFlight.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    val file = storage.photoFile(photo.fileName)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                        ?: error("Cannot decode ${file.name}")
                    val image = bmp.asImageBitmap()
                    cache.put(key, image)
                    image
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return deferred.await()
    }

    private fun maxMemory(): Int = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    /**
     * Размер записи кэша в КБ — в тех же единицах, что и ёмкость [maxMemory].
     * Ноль не возвращаем: LruCache трактует size == 0 как «не считается» и такие
     * записи никогда не вытесняются.
     */
    internal fun cacheSizeKb(value: ImageBitmap): Int =
        maxOf(1, value.asAndroidBitmap().allocationByteCount / 1024)

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