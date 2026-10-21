package com.combo.data.di

import android.content.Context
import androidx.room.Room
import com.combo.data.AppDb
import com.combo.data.ComboDao
import com.combo.data.PhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Провайдинг Room: база и DAO.
 *
 * Отдельным модулем от `domain/di/AppModule.kt` намеренно: провайдинг — это слой `data`,
 * а биндинги интерфейсов на реализации — слой `domain`. Имя базы `"combo.db"` не меняется.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDb =
        Room.databaseBuilder(context, AppDb::class.java, "combo.db").build()

    @Provides
    fun providePhotoDao(db: AppDb): PhotoDao = db.photos()

    @Provides
    fun provideComboDao(db: AppDb): ComboDao = db.combos()
}