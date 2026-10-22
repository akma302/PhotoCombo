package com.combo.ui.common.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Собственные контурные пиктограммы; цвет и экранный размер задаёт потребитель. */
object PcIcons {
    val Back: ImageVector by lazy {
        icon("Back", autoMirror = true) {
            moveTo(10f, 5f); lineTo(3f, 12f); lineTo(10f, 19f)
            moveTo(3f, 12f); lineTo(21f, 12f)
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo", autoMirror = true) {
            moveTo(8f, 4f); lineTo(3f, 9f); lineTo(8f, 14f)
            moveTo(3f, 9f); lineTo(14f, 9f)
            curveTo(18.5f, 9f, 21f, 11.5f, 21f, 15f)
            curveTo(21f, 18.5f, 18.5f, 20f, 15f, 20f)
        }
    }

    val Redo: ImageVector by lazy {
        icon("Redo", autoMirror = true) {
            moveTo(16f, 4f); lineTo(21f, 9f); lineTo(16f, 14f)
            moveTo(21f, 9f); lineTo(10f, 9f)
            curveTo(5.5f, 9f, 3f, 11.5f, 3f, 15f)
            curveTo(3f, 18.5f, 5.5f, 20f, 9f, 20f)
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(12f, 5f); lineTo(12f, 19f)
        }
    }

    val Remove: ImageVector by lazy {
        icon("Remove") { moveTo(5f, 12f); lineTo(19f, 12f) }
    }

    val Check: ImageVector by lazy {
        icon("Check") { moveTo(4f, 12f); lineTo(9.5f, 17.5f); lineTo(20f, 6.5f) }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
    }

    val Photo: ImageVector by lazy {
        icon("Photo") {
            roundedBox(3f, 4f, 21f, 20f)
            moveTo(3f, 16f); lineTo(8f, 11f); lineTo(13f, 16f)
            lineTo(17f, 12f); lineTo(21f, 16f)
            circle(15.5f, 8.5f, 1f)
        }
    }

    val ReplacePhoto: ImageVector by lazy {
        icon("ReplacePhoto") {
            moveTo(12f, 4f); lineTo(5f, 4f)
            curveTo(3.8f, 4f, 3f, 4.8f, 3f, 6f)
            lineTo(3f, 18f); curveTo(3f, 19.2f, 3.8f, 20f, 5f, 20f)
            lineTo(19f, 20f); curveTo(20.2f, 20f, 21f, 19.2f, 21f, 18f)
            lineTo(21f, 14f)
            moveTo(3f, 16f); lineTo(8f, 11f); lineTo(13f, 16f)
            lineTo(16f, 13f)
            moveTo(15f, 7f); lineTo(21f, 7f)
            moveTo(18f, 4f); lineTo(21f, 7f); lineTo(18f, 10f)
        }
    }

    val Delete: ImageVector by lazy {
        icon("Delete") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
            moveTo(6f, 6f); lineTo(7f, 19f)
            curveTo(7.1f, 20.3f, 7.7f, 21f, 9f, 21f)
            lineTo(15f, 21f); curveTo(16.3f, 21f, 16.9f, 20.3f, 17f, 19f)
            lineTo(18f, 6f)
            moveTo(10f, 10f); lineTo(10.5f, 17f)
            moveTo(14f, 10f); lineTo(13.5f, 17f)
        }
    }

    val LayerUp: ImageVector by lazy {
        icon("LayerUp") {
            moveTo(3f, 15f); lineTo(9f, 12f); lineTo(15f, 15f)
            lineTo(9f, 18f); close()
            moveTo(3f, 18f); lineTo(9f, 21f); lineTo(15f, 18f)
            moveTo(18f, 13f); lineTo(18f, 3f)
            moveTo(15f, 6f); lineTo(18f, 3f); lineTo(21f, 6f)
        }
    }

    val LayerDown: ImageVector by lazy {
        icon("LayerDown") {
            moveTo(3f, 6f); lineTo(9f, 3f); lineTo(15f, 6f)
            lineTo(9f, 9f); close()
            moveTo(3f, 9f); lineTo(9f, 12f); lineTo(15f, 9f)
            moveTo(18f, 11f); lineTo(18f, 21f)
            moveTo(15f, 18f); lineTo(18f, 21f); lineTo(21f, 18f)
        }
    }

    val WholeCanvas: ImageVector by lazy {
        icon("WholeCanvas") {
            moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f)
            moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
            moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
            moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
            roundedBox(7f, 8f, 17f, 16f, 1f)
        }
    }

    val AspectRatio: ImageVector by lazy {
        icon("AspectRatio") {
            roundedBox(3f, 5f, 21f, 19f)
            moveTo(7f, 11f); lineTo(7f, 9f); lineTo(10f, 9f)
            moveTo(14f, 15f); lineTo(17f, 15f); lineTo(17f, 13f)
        }
    }

    val Palette: ImageVector by lazy {
        icon("Palette") {
            moveTo(12f, 3f)
            curveTo(6.8f, 3f, 3f, 6.7f, 3f, 12f)
            curveTo(3f, 17f, 6.8f, 21f, 11f, 21f)
            curveTo(13.3f, 21f, 14f, 19.8f, 13f, 18.2f)
            curveTo(12f, 16.5f, 13f, 15f, 15f, 15f)
            lineTo(17.5f, 15f)
            curveTo(19.8f, 15f, 21f, 13.7f, 21f, 11.5f)
            curveTo(21f, 6.6f, 17.2f, 3f, 12f, 3f); close()
            moveTo(7f, 11f); lineTo(7.05f, 11f)
            moveTo(10f, 7f); lineTo(10.05f, 7f)
            moveTo(15f, 7.5f); lineTo(15.05f, 7.5f)
            moveTo(17f, 11f); lineTo(17.05f, 11f)
        }
    }

    val Frame: ImageVector by lazy {
        icon("Frame") {
            roundedBox(3f, 3f, 21f, 21f)
            roundedBox(7f, 7f, 17f, 17f, 1f)
        }
    }

    val Crop: ImageVector by lazy {
        icon("Crop") {
            moveTo(3f, 7f); lineTo(15f, 7f)
            curveTo(16.2f, 7f, 17f, 7.8f, 17f, 9f); lineTo(17f, 21f)
            moveTo(7f, 3f); lineTo(7f, 15f)
            curveTo(7f, 16.2f, 7.8f, 17f, 9f, 17f); lineTo(21f, 17f)
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7.5f); lineTo(12.05f, 7.5f)
            moveTo(11f, 11f); lineTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(10.5f, 16.5f); lineTo(13.5f, 16.5f)
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            moveTo(10.6f, 4f)
            curveTo(11.2f, 2.9f, 12.8f, 2.9f, 13.4f, 4f)
            lineTo(20.7f, 17.8f)
            curveTo(21f, 19.1f, 20.7f, 20.5f, 19.2f, 20.5f)
            lineTo(4.8f, 20.5f)
            curveTo(3.3f, 20.5f, 3f, 19.1f, 3.3f, 17.8f); close()
            moveTo(12f, 8f); lineTo(12f, 13f)
            moveTo(12f, 16.5f); lineTo(12.05f, 16.5f)
        }
    }
}

private fun icon(
    name: String,
    autoMirror: Boolean = false,
    draw: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = "PcIcons.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = draw,
    )
}.build()

private fun PathBuilder.roundedBox(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float = 2f,
) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    quadTo(right, top, right, top + radius)
    lineTo(right, bottom - radius)
    quadTo(right, bottom, right - radius, bottom)
    lineTo(left + radius, bottom)
    quadTo(left, bottom, left, bottom - radius)
    lineTo(left, top + radius)
    quadTo(left, top, left + radius, top)
    close()
}

private fun PathBuilder.circle(x: Float, y: Float, radius: Float) {
    val handle = radius * 0.5522848f
    moveTo(x + radius, y)
    curveTo(x + radius, y + handle, x + handle, y + radius, x, y + radius)
    curveTo(x - handle, y + radius, x - radius, y + handle, x - radius, y)
    curveTo(x - radius, y - handle, x - handle, y - radius, x, y - radius)
    curveTo(x + handle, y - radius, x + radius, y - handle, x + radius, y)
    close()
}
