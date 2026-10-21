package com.combo.di

import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.PhotoStorage
import com.combo.domain.ComboRepository
import com.combo.domain.ComboRepositoryImpl
import com.combo.domain.PhotoImporter
import com.combo.domain.PhotoRepository
import com.combo.domain.PhotoRepositoryImpl
import org.koin.dsl.module

/**
 * Биндинги, общие для всех платформ: хранилище файлов, импортёр и репозитории.
 * Платформенные зависимости (`FileSystem`, каталог данных, кодек, диспетчеры) приходят
 * из модуля платформы, поэтому этот модуль не знает, на чём он работает.
 *
 * Время и генератор id передаются [PhotoImporter] адаптерами: сам Koin биндит
 * функциональные типы плохо (разные `FunctionN` одного вида неразличимы по классу).
 */
val appModule = module {
    single { PhotoStorage(dirs = get(), files = get()) }

    single {
        val clock = get<WallClock>()
        val ids = get<IdGenerator>()
        PhotoImporter(
            photoDao = get(),
            storage = get(),
            codec = get(),
            files = get(),
            logger = get(),
            nowMillis = clock::nowMillis,
            newId = ids::newId,
            ioDispatcher = get(),
        )
    }

    single<PhotoRepository> {
        PhotoRepositoryImpl(
            photoDao = get(),
            comboDao = get(),
            storage = get(),
            files = get(),
            importer = get(),
            ioDispatcher = get(),
        )
    }

    single<ComboRepository> { ComboRepositoryImpl(db = get(), comboDao = get()) }
}
