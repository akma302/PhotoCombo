package com.combo.domain

import com.combo.core.platform.Logger
import com.combo.data.GuidePreviewStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException

interface GuidePreviewSettings {
    suspend fun load(): Boolean
    suspend fun save(enabled: Boolean): Boolean
}

class FileGuidePreviewSettings(
    private val storage: GuidePreviewStorage,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : GuidePreviewSettings {
    private val mutex = Mutex()

    override suspend fun load(): Boolean = mutex.withLock {
        try {
            withContext(ioDispatcher) { storage.read() }
        } catch (error: IOException) {
            logger.warn("GuidePreview", "Cannot read guide preview setting", error)
            false
        }
    }

    override suspend fun save(enabled: Boolean): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            try {
                withContext(ioDispatcher) { storage.write(enabled) }
                true
            } catch (error: IOException) {
                logger.warn("GuidePreview", "Cannot save guide preview setting", error)
                false
            }
        }
    }
}
