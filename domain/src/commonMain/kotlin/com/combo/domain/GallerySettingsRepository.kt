package com.combo.domain

import com.combo.core.platform.Logger
import com.combo.data.GallerySettings
import com.combo.data.GallerySettingsStorage
import com.combo.data.normalizedGalleryPercent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException

enum class GalleryKind { Photos, Combos }

interface GallerySettingsRepository {
    suspend fun load(): GallerySettings
    /** При сбое записи текущий выбор остаётся в памяти; false сообщает о сбое. */
    suspend fun saveScale(kind: GalleryKind, percent: Float): Boolean
}

class GallerySettingsRepositoryImpl(
    private val storage: GallerySettingsStorage,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : GallerySettingsRepository {
    private val mutex = Mutex()
    private var current: GallerySettings? = null

    private suspend fun loaded(): GallerySettings {
        current?.let { return it }
        return try {
            withContext(ioDispatcher) { storage.read() }
        } catch (error: IOException) {
            logger.warn("GallerySettings", "Cannot read gallery settings", error)
            GallerySettings()
        }.also { current = it }
    }

    override suspend fun load(): GallerySettings = mutex.withLock { loaded() }

    override suspend fun saveScale(kind: GalleryKind, percent: Float): Boolean = mutex.withLock {
        val settings = loaded()
        val next = when (kind) {
            GalleryKind.Photos -> settings.copy(photosPercent = normalizedGalleryPercent(percent))
            GalleryKind.Combos -> settings.copy(combosPercent = normalizedGalleryPercent(percent))
        }
        current = next
        try {
            withContext(ioDispatcher) { storage.write(next) }
            true
        } catch (error: IOException) {
            logger.warn("GallerySettings", "Cannot save gallery settings", error)
            false
        }
    }
}
