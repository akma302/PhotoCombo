package com.combo.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.combo.data.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Миниатюра фото для сеток (библиотека и диалог «+Photo»).
 *
 * Живёт в `ui.common`, потому что нужна обоим экранам, а иначе библиотека импортирует
 * composable из пакета builder. На входе только идентификатор фото: где лежат миниатюры
 * решает компонент, а не экран — иначе правило именования разъезжается по коду.
 *
 * Файл читается на IO и кэшируется в `remember(photoId)`: декодирование в кадре
 * композиции блокировало бы главный поток, а чтение без кэша на каждый кадр при прокрутке
 * сетки отдавало бы тот же файл десятки раз.
 */
@Composable
fun ThumbImage(photoId: String, modifier: Modifier = Modifier) {
    val path = rememberThumbFilePath(photoId)
    var bmp by remember(photoId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoId) {
        bmp = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    val image = bmp
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    } else {
        // Заглушка на тот же modifier: иначе до загрузки миниатюры ячейка схлопывалась бы
        // в ноль и по ней нельзя было бы ни попасть, ни положить testTag.
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * Файл миниатюры по `photoId`; путь определяет [PhotoStorage].
 *
 * Hilt не внедряет зависимости в обычные composable-функции (нужен `hiltViewModel()` или
 * ручной `EntryPoint`), поэтому `PhotoStorage` создаётся здесь вручную из
 * application-контекста. Это безопасно: класс не хранит ничего, кроме контекста, а
 * `remember` не даёт создавать его заново на каждой перекомпоновке.
 */
@Composable
fun rememberThumbFilePath(photoId: String): String {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { PhotoStorage(context) }
    return remember(storage, photoId) { storage.thumbFile(photoId).absolutePath }
}