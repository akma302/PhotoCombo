package com.combo.di

import com.combo.core.image.DesktopImagePicker
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImagePicker
import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.AppDirs
import com.combo.core.platform.DesktopAppDirs
import com.combo.core.platform.DesktopLogger
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.Logger
import com.combo.core.platform.WallClock
import com.combo.core.platform.defaultAppDataDir
import com.combo.data.AppDb
import com.combo.data.ComboDao
import com.combo.data.DB_FILE_NAME
import com.combo.data.PhotoDao
import com.combo.data.createAppDb
import com.combo.domain.BitmapSource
import com.combo.domain.image.LruBitmapSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Платформенные биндинги desktop: файловая система, каталог данных, кодек, пикер,
 * диспетчеры, база. [dataDir] — параметр, а не глобальное состояние: тесты подставляют
 * временный каталог и не трогают данные пользователя.
 */
fun desktopModule(dataDir: Path = defaultAppDataDir().toOkioPath()): Module = module {
    single<FileSystem> { FileSystem.SYSTEM }
    single<AppDirs> { DesktopAppDirs(dataDir) }
    single<Logger> { DesktopLogger() }
    single<ImageCodec> { SkiaImageCodec(files = get(), logger = get()) }
    single<ImagePicker> { DesktopImagePicker(files = get()) }
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<WallClock> { WallClock { System.currentTimeMillis() } }
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }

    single<AppDb> { createAppDb(File(dataDir.toFile(), DB_FILE_NAME)) }.onClose { it?.close() }
    single<PhotoDao> { get<AppDb>().photos() }
    single<ComboDao> { get<AppDb>().combos() }

    single<BitmapSource> {
        LruBitmapSource(
            photoDao = get(),
            storage = get(),
            codec = get(),
            // Кэш ограничен четвертью доступной памяти JVM.
            maxCacheKb = max(4, (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()),
        )
    }
}
