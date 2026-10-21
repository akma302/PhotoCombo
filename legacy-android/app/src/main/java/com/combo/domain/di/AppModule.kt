package com.combo.domain.di

import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.ComboRepositoryImpl
import com.combo.domain.PhotoRepository
import com.combo.domain.PhotoRepositoryImpl
import com.combo.domain.image.LruBitmapSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Только привязки интерфейсов к реализациям. Провайдинг Room вынесен
 * в `data/di/DatabaseModule.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindComboRepository(impl: ComboRepositoryImpl): ComboRepository

    @Binds
    abstract fun bindBitmapSource(impl: LruBitmapSource): BitmapSource
}