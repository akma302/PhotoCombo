package com.combo.core.image

import androidx.compose.ui.graphics.ImageBitmap
import okio.Path

data class PngDestination(val path: Path, val replaceExisting: Boolean)

interface PngOutput {
    suspend fun chooseDestination(name: String): PngDestination?
    suspend fun write(destination: PngDestination, image: ImageBitmap)
}
