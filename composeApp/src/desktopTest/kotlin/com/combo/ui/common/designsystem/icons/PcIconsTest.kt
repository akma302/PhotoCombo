package com.combo.ui.common.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PcIconsTest {
    private fun icons(): List<ImageVector> = listOf(
        PcIcons.Back, PcIcons.Undo, PcIcons.Redo, PcIcons.Add, PcIcons.Remove,
        PcIcons.Check, PcIcons.Close, PcIcons.ChevronDown, PcIcons.Photo,
        PcIcons.ReplacePhoto, PcIcons.Delete, PcIcons.LayerUp, PcIcons.LayerDown,
        PcIcons.WholeCanvas, PcIcons.AspectRatio, PcIcons.Palette, PcIcons.Frame,
        PcIcons.Crop, PcIcons.Info, PcIcons.Warning, PcIcons.Edit,
    )

    @Test
    fun `all public vectors are cached and have distinct artwork`() {
        val first = icons()
        val second = icons()
        assertEquals(21, first.size)
        first.zip(second).forEach { (a, b) -> assertSame(a.name, a, b) }
        assertEquals(first.size, first.map { it.name }.toSet().size)
        assertEquals(first.size, first.map { (it.root[0] as VectorPath).pathData }.toSet().size)
    }

    @Test
    fun `only navigation and history arrows mirror in rtl`() {
        val mirrored = icons().filter { it.autoMirror }.map { it.name }.toSet()
        assertEquals(setOf("PcIcons.Back", "PcIcons.Undo", "PcIcons.Redo"), mirrored)
    }

    @Test
    fun `vectors share viewport stroke and neutral tint source`() {
        icons().forEach { icon ->
            assertEquals(icon.name, 24.dp, icon.defaultWidth)
            assertEquals(icon.name, 24.dp, icon.defaultHeight)
            assertEquals(icon.name, 24f, icon.viewportWidth, 0f)
            assertEquals(icon.name, 24f, icon.viewportHeight, 0f)
            assertEquals(icon.name, Color.Unspecified, icon.tintColor)
            assertEquals(icon.name, 1, icon.root.size)
            val path = icon.root[0] as VectorPath
            assertNull(icon.name, path.fill)
            assertEquals(icon.name, SolidColor(Color.Black), path.stroke)
            assertEquals(icon.name, 1.75f, path.strokeLineWidth, 0f)
            assertEquals(icon.name, StrokeCap.Round, path.strokeLineCap)
            assertEquals(icon.name, StrokeJoin.Round, path.strokeLineJoin)
        }
    }

    @Test
    fun `visible strokes respect safe field including rounded caps`() {
        icons().forEach { icon ->
            val vectorPath = icon.root[0] as VectorPath
            val bounds = PathParser().addPathNodes(vectorPath.pathData).toPath().getBounds()
            val radius = vectorPath.strokeLineWidth / 2f
            assertTrue("${icon.name}: $bounds", bounds.width > 0f)
            // У горизонтального минуса нулевая высота геометрии, но видимая толщина есть.
            assertTrue("${icon.name}: $bounds", bounds.left - radius >= 2f)
            assertTrue("${icon.name}: $bounds", bounds.top - radius >= 2f)
            assertTrue("${icon.name}: $bounds", bounds.right + radius <= 22f)
            assertTrue("${icon.name}: $bounds", bounds.bottom + radius <= 22f)
        }
    }
}
