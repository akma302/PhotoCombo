package com.combo.ui

import com.combo.data.GallerySettings
import com.combo.domain.GalleryKind
import com.combo.domain.GallerySettingsRepository

class FakeGallerySettingsRepository : GallerySettingsRepository {
    var settings = GallerySettings()
    var writes = 0
    var failWrites = false
    override suspend fun load() = settings
    override suspend fun saveScale(kind: GalleryKind, percent: Float): Boolean {
        settings = when (kind) {
            GalleryKind.Photos -> settings.copy(photosPercent = percent)
            GalleryKind.Combos -> settings.copy(combosPercent = percent)
        }
        writes++
        return !failWrites
    }
}
