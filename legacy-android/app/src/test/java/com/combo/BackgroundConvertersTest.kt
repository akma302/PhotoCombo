package com.combo

import com.combo.core.background.AverageAreaOklabStrategy
import com.combo.core.background.BackgroundSpec
import com.combo.data.BackgroundConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundConvertersTest {

    private val converters = BackgroundConverters

    @Test
    fun `auto round trips through json`() {
        val spec = BackgroundSpec.Auto()
        val json = converters.toJson(spec)
        assertEquals(spec, converters.fromJson(json))
    }

    @Test
    fun `solid round trips through json`() {
        val spec = BackgroundSpec.Solid(0xFF0A0B0C.toInt())
        assertEquals(spec, converters.fromJson(converters.toJson(spec)))
    }

    /** encodeDefaults: id стратегии должен попадать в данные явно. */
    @Test
    fun `auto json contains explicit strategy id`() {
        val json = converters.toJson(BackgroundSpec.Auto())
        assertTrue(json, json.contains(AverageAreaOklabStrategy.ID))
        assertTrue(json, json.contains("\"type\""))
    }

    @Test
    fun `unknown type and garbage degrade to auto`() {
        assertEquals(BackgroundSpec.Auto(), converters.fromJson("не json вовсе"))
        assertEquals(BackgroundSpec.Auto(), converters.fromJson(""))
        assertEquals(BackgroundSpec.Auto(), converters.fromJson("""{"type":"gradient.v9","stops":[1,2]}"""))
    }

    @Test
    fun `unknown strategy id in json is kept as spec`() {
        val spec = converters.fromJson("""{"type":"auto","strategy":"future.v1"}""")
        assertEquals(BackgroundSpec.Auto("future.v1"), spec)
        // ...и резолвер не падает на нём.
        assertTrue(
            com.combo.core.background.BackgroundResolver.Default.resolve(
                spec,
                com.combo.core.background.BackgroundInput(10f, 10f, emptyList()),
            ) is com.combo.core.background.ResolvedBackground.Solid,
        )
    }
}