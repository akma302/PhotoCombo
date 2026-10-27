package com.combo

import com.combo.core.platform.DesktopAppDirs
import com.combo.data.*
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GallerySettingsStorageTest {
    @get:Rule val folder = TemporaryFolder()
    private fun storage() = FileGallerySettingsStorage(DesktopAppDirs(folder.root.toOkioPath()), FileSystem.SYSTEM)

    @Test fun missingFileUsesDefaultsAndBothValuesSurviveReplacement() {
        assertEquals(GallerySettings(), storage().read())
        storage().write(GallerySettings(50f, 200f))
        assertEquals(GallerySettings(50f, 200f), storage().read())
        storage().write(GallerySettings(123.5f, 75f))
        assertEquals(GallerySettings(123.5f, 75f), storage().read())
        assertFalse(folder.root.resolve("gallery-settings.json.tmp").exists())
    }

    @Test fun corruptFieldsRecoverIndependentlyAndNumbersAreClamped() {
        val file = folder.root.resolve("gallery-settings.json")
        file.writeText("""{"photosPercent":0,"combosPercent":999}""")
        assertEquals(GallerySettings(50f, 200f), storage().read())
        file.writeText("""{"photosPercent":{},"combosPercent":150}""")
        assertEquals(GallerySettings(100f, 150f), storage().read())
        file.writeText("""{"photosPercent":"NaN","combosPercent":null}""")
        assertEquals(GallerySettings(), storage().read())
        file.writeText("{broken")
        assertEquals(GallerySettings(), storage().read())
    }

    @Test fun failedReplacementLeavesPreviousSettingsReadable() {
        storage().write(GallerySettings(75f, 125f))
        // Каталог вместо временного файла воспроизводит ошибку записи без прав администратора.
        folder.root.resolve("gallery-settings.json.tmp").mkdir()
        folder.root.resolve("gallery-settings.json.tmp/block").writeText("fixture")
        assertThrows(okio.IOException::class.java) { storage().write(GallerySettings(200f, 50f)) }
        assertEquals(GallerySettings(75f, 125f), storage().read())
    }
}
