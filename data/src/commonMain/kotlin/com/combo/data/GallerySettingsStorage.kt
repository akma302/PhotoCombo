package com.combo.data

import com.combo.core.platform.AppDirs
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path

/** Масштаб миниатюр хранится отдельно от базы фотографий и комбо. */
data class GallerySettings(val photosPercent: Float = 100f, val combosPercent: Float = 100f)

fun normalizedGalleryPercent(value: Float): Float =
    if (value.isFinite()) value.coerceIn(50f, 200f) else 100f

interface GallerySettingsStorage {
    fun read(): GallerySettings
    fun write(settings: GallerySettings)
}

class FileGallerySettingsStorage(
    dirs: AppDirs,
    private val files: FileSystem,
) : GallerySettingsStorage {
    private val path: Path = dirs.dataDir / "gallery-settings.json"

    override fun read(): GallerySettings {
        if (!files.exists(path)) return GallerySettings()
        val text = files.read(path) { readUtf8() }
        val json = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: IllegalArgumentException) { null }
        fun percent(key: String): Float = normalizedGalleryPercent(
            (json?.get(key) as? JsonPrimitive)?.floatOrNull ?: 100f,
        )
        return GallerySettings(percent("photosPercent"), percent("combosPercent"))
    }

    override fun write(settings: GallerySettings) {
        files.createDirectories(checkNotNull(path.parent))
        val temporary = checkNotNull(path.parent) / "gallery-settings.json.tmp"
        val json = buildJsonObject {
            put("photosPercent", normalizedGalleryPercent(settings.photosPercent))
            put("combosPercent", normalizedGalleryPercent(settings.combosPercent))
        }
        try {
            files.write(temporary) { writeUtf8(json.toString()) }
            files.atomicMove(temporary, path)
        } finally {
            // Ошибка очистки не должна маскировать результат основной записи.
            try { files.delete(temporary, mustExist = false) } catch (_: okio.IOException) { }
        }
    }
}
