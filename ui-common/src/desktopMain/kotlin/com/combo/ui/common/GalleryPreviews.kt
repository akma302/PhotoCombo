package com.combo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.combo.data.PhotoEntity
import com.combo.ui.common.designsystem.theme.PcTheme

private val samples = (1..6).map { PhotoEntity("$it", "$it.jpg", "Photo $it", "image/jpeg", 640, 480, 0, "hash$it", 0, 0) }
private val colors = listOf(Color(0xff657d8a), Color(0xffb3997d), Color(0xff65837b))

@Preview(name = "Gallery grid") @Composable
internal fun GalleryGridPreview() = PcDesktopTheme {
    GalleryGrid(samples, { it.id }, 110.dp, Modifier.size(380.dp, 250.dp), horizontalGap = 2.dp) { photo, _ ->
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(colors[(photo.id.toInt() - 1) % colors.size]).testTag("sample_${photo.id}"))
    }
}

@Preview(name = "Photo grid") @Composable
internal fun PhotoGridPreview() = PcDesktopTheme {
    PhotoGrid(samples, {}, { it.id }, Modifier.size(380.dp, 250.dp), referenceItemWidth = 110.dp,
        isSelected = { it.id == "2" }, thumbnail = { photo, modifier ->
            Box(modifier.background(colors[(photo.id.toInt() - 1) % colors.size]))
        })
}

@Preview(name = "Gallery item size") @Composable
internal fun GallerySizePreview() = PcDesktopTheme {
    GallerySizeControl(GalleryScaleUiState(percent = 100f, loaded = true), {}, {}, {}, Modifier.width(420.dp))
}

@Preview(name = "Back navigation") @Composable
internal fun BackNavPreview() = PcDesktopTheme { BackNavIcon {} }

/** Isolated dependencies let the placeholder render without a database or user files. */
@Preview(name = "Thumbnail placeholder") @Composable
internal fun ThumbnailPreview() {
    val app = androidx.compose.runtime.remember {
        org.koin.dsl.koinApplication {
            modules(org.koin.dsl.module {
                single<com.combo.domain.PhotoRepository> {
                    object : com.combo.domain.PhotoRepository {
                        override suspend fun importPhotos(sources: List<com.combo.core.image.ImageSource>) = com.combo.domain.ImportResult(0, 0, 0)
                        override fun all() = kotlinx.coroutines.flow.flowOf(emptyList<PhotoEntity>())
                        override suspend fun get(id: String): PhotoEntity? = null
                        override suspend fun delete(id: String) = com.combo.domain.DeleteResult.DELETED
                        override fun thumbPath(photoId: String): okio.Path? = null
                    }
                }
                single<com.combo.core.image.ImageCodec> {
                    object : com.combo.core.image.ImageCodec {
                        override fun readBounds(path: okio.Path): androidx.compose.ui.unit.IntSize? = null
                        override fun readOrientation(path: okio.Path) = com.combo.core.image.ExifOrientation.NORMAL
                        override fun decode(path: okio.Path, sample: Int): androidx.compose.ui.graphics.ImageBitmap? = null
                        override fun encode(path: okio.Path, image: androidx.compose.ui.graphics.ImageBitmap, format: com.combo.core.image.ImageFormat, quality: Int) = false
                    }
                }
                single<kotlinx.coroutines.CoroutineDispatcher> { kotlinx.coroutines.Dispatchers.IO }
            })
        }
    }
    androidx.compose.runtime.DisposableEffect(app) { onDispose { app.close() } }
    org.koin.compose.KoinIsolatedContext(app) {
        PcDesktopTheme { ThumbImage("sample", Modifier.size(120.dp), placeholderColor = PcTheme.colors.workspace) }
    }
}
