package com.combo.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.combo.Const
import com.combo.core.image.PngOutput
import com.combo.core.render.CanvasTransform
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.core.render.effectiveFraming
import com.combo.data.ComboItemEntity
import com.combo.data.PhotoEntity
import com.combo.data.backgroundFrames
import com.combo.data.border
import com.combo.data.toItemFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class ComboPngExporter(
    private val combos: ComboRepository,
    private val photos: PhotoRepository,
    private val bitmaps: BitmapSource,
    private val output: PngOutput,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** Returns the exported filename, or null when the save dialog is cancelled. */
    suspend fun export(id: String): String? = withContext(ioDispatcher) {
        val snapshot = checkNotNull(combos.get(id)) { "This combo no longer exists." }
        val combo = snapshot.combo
        val width = combo.canvasWidth
        val height = combo.canvasHeight
        require(width > 0 && height > 0 && width.toLong() * height <= Const.MAX_CANVAS_PIXELS) {
            "The saved canvas size is outside the supported range for PNG export."
        }
        require(snapshot.items.isNotEmpty()) { "This combo has no photos to export." }
        val destination = output.chooseDestination(combo.name) ?: return@withContext null

        val byPhoto = snapshot.items.groupBy { it.photoId }
        val metadata = byPhoto.keys.associateWith { photoId ->
            checkNotNull(photos.get(photoId)) { "A photo in this combo is missing from the library." }
        }
        val images = byPhoto.mapValues { (photoId, items) ->
            val photo = metadata.getValue(photoId)
            bitmaps.get(photoId, items.maxOf { it.exportPixels(photo) })
        }
        val frames = snapshot.items.sortedBy { it.zIndex }.map { item ->
            item.toItemFrame(images.getValue(item.photoId), metadata.getValue(item.photoId))
        }
        val background = comboBackgroundOf(width, height, combo.background, snapshot.backgroundFrames(metadata))
        val size = Size(width.toFloat(), height.toFloat())
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
            drawCombo(CanvasTransform(1f, Offset.Zero), size.width, size.height, background, frames)
        }
        output.write(destination, image)
        destination.path.name
    }
}

private fun ComboItemEntity.exportPixels(photo: PhotoEntity): Int {
    val scale = effectiveFraming(framingModeEnum, border(), photo.width, photo.height, contentScale, panX, panY).scale
    val sourceSide = maxOf(photo.width, photo.height)
    require(scale.isFinite() && scale > 0f && sourceSide > 0) { "A frame has invalid photo dimensions or scale." }
    return ceil(sourceSide * scale).toInt().coerceIn(1, sourceSide)
}
