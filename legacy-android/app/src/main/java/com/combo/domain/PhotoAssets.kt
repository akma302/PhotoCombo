package com.combo.domain

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.data.PhotoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Общий кэш метаданных фото и декодированных битмапов.
 *
 * Четыре экрана (редактор, плеер, список, просмотр фото) держали одну и ту же идею
 * четырьмя копиями: догрузить метаданные по id, декодировать битмап, если уже
 * загруженного разрешения не хватает, и не грузить заново то, что уже есть. Копии
 * разъезжались по мелочам — учёт «ранее запрошенного размера» назывался по-разному
 * (`loadedReqPx`/`requestedPx`), обновление карты в плеере было неатомарным, а список
 * вообще не учитывал размер, потому что у него он фиксированный.
 *
 * Здесь живёт ТОЛЬКО кэш и правило пропуска. Политика размера остаётся у вызывающего и
 * передаётся параметром `requiredPx`: редактор считает её от рамки и зума вида (с
 * задержкой 300 мс), плеер — от экрана, превью — 800, просмотр фото — 2048.
 *
 * Экземпляр намеренно обычный класс, а не `@Singleton`: состояние кэша — часть
 * ViewModel, и два разных экрана не должны делить один битмап-кеш. Внутри VM создаётся
 * руками (`PhotoAssets(photoRepo, bitmapSource)`), поэтому класс остаётся тестируемым
 * без Hilt и без Robolectric.
 */
class PhotoAssets(
    private val photoRepo: PhotoRepository,
    private val bitmapSource: BitmapSource,
) {

    private val _photos = MutableStateFlow<Map<String, PhotoEntity>>(emptyMap())

    /** Метаданные (`avgColor`, размеры) по id: без них нечем ни собрать кадр, ни посчитать фон. */
    val photos: StateFlow<Map<String, PhotoEntity>> = _photos.asStateFlow()

    private val _bitmaps = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Декодированные битмапы по id. Оригиналы не трогаем: `BitmapSource` отдаёт сэмплы. */
    val bitmaps: StateFlow<Map<String, ImageBitmap>> = _bitmaps.asStateFlow()

    /**
     * С каким `requiredPx` битмап уже лежит в [_bitmaps].
     *
     * Без этого учёта «битмап уже есть» значило бы «есть в любом разрешении»: после зума
     * в редакторе картинка осталась бы в низком сэмпле и выглядела размытой. Поэтому
     * загруженный битмап считается годным, только если запрошенный для него размер был
     * не меньше текущего.
     */
    private val loadedReqPx = mutableMapOf<String, Int>()

    /** Метаданные для [ids]: уже известные пропускаются, остальные читаются из репозитория. */
    suspend fun ensureMeta(ids: Collection<String>) {
        for (id in ids) {
            if (_photos.value.containsKey(id)) continue
            val photo = photoRepo.get(id) ?: continue
            _photos.update { it + (id to photo) }
        }
    }

    /**
     * Декодирует битмап [id] не меньше [requiredPx] пикселей.
     *
     * Правило пропуска: битмап уже есть И ранее запрошенный размер не меньше [requiredPx].
     * Иначе грузим. `true` — карта обновилась (экрану нужен сигнал перерисоваться),
     * `false` — либо уже актуален, либо декод упал.
     */
    suspend fun ensureBitmap(id: String, requiredPx: Int): Boolean {
        if (_bitmaps.value.containsKey(id) && (loadedReqPx[id] ?: -1) >= requiredPx) return false
        // Сбой декода глотаем: битый/удалённый файл не должен ронять экран, из которого
        // он открыт, — ровно так вело себя и `runCatching` во всех четырёх копиях.
        val bitmap = runCatching { bitmapSource.get(id, requiredPx) }.getOrNull() ?: return false
        loadedReqPx[id] = requiredPx
        // Именно update, а не присваивание `value = value + …`: пересборка карты под
        // двумя конкурентными загрузками иначе теряла бы чужую запись.
        _bitmaps.update { it + (id to bitmap) }
        return true
    }

    /**
     * Вытесняет всё, чего нет в [keep]: и сами битмапы, и учёт их размеров.
     *
     * Учёт чистится ОБЯЗАТЕЛЬНО: оставшаяся запись без битмапа (или наоборот) сделала бы
     * правило пропуска в [ensureBitmap] бессмысленным — годным сочлось бы то, чего
     * уже нет. Если вытеснять нечего, новая карта равна старой и StateFlow не шлёт
     * обновление, то есть лишней перерисовки не будет.
     */
    fun evictExcept(keep: Set<String>) {
        loadedReqPx.keys.retainAll(keep)
        _bitmaps.update { it.filterKeys { id -> id in keep } }
    }
}
