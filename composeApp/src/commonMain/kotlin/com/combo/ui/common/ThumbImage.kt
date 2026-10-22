package com.combo.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.combo.ui.common.designsystem.theme.PcTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.image.ImageCodec
import com.combo.domain.PhotoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Миниатюра фото для сеток (библиотека и диалог «+Photo»).
 *
 * Живёт в `ui.common`, потому что нужна обоим экранам, а иначе библиотека импортирует
 * composable из пакета builder. На входе только идентификатор фото: где лежат миниатюры,
 * решает `PhotoRepository`, а не экран — иначе правило именования разъезжается по коду.
 *
 * Зависимости берутся из Koin прямо в композиции: `koinInject` внедряет их в обычные
 * composable-функции, чего не умеет `viewModel {}`. Файл читается на IO-диспетчере и
 * кэшируется в `remember(photoId)`: декодирование в кадре композиции блокировало бы
 * главный поток, а чтение без кэша на каждый кадр при прокрутке сетки отдавало бы тот же
 * файл десятки раз.
 */
@Composable
fun ThumbImage(photoId: String, modifier: Modifier = Modifier, placeholderColor: Color = PcTheme.colors.workspace) {
    val repo = koinInject<PhotoRepository>()
    val codec = koinInject<ImageCodec>()
    val ioDispatcher = koinInject<CoroutineDispatcher>()
    var bmp by remember(photoId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoId) {
        val path = repo.thumbPath(photoId)
        bmp = if (path == null) null else withContext(ioDispatcher) { codec.decode(path) }
    }
    val image = bmp
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    } else {
        // Заглушка на тот же modifier: иначе до загрузки миниатюры ячейка схлопывалась бы
        // в ноль и по ней нельзя было бы ни попасть, ни положить testTag.
        Box(modifier.background(placeholderColor))
    }
}
