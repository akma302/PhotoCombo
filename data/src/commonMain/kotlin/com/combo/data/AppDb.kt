package com.combo.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

/**
 * База приложения: фото, комбо и рамки комбо (в каждой рамке одно фото). Схема
 * экспортируется в `data/schemas`. При любом изменении сущностей нужно повысить
 * `version` и написать миграцию (см. `Migrations.kt`).
 */
@Database(
    entities = [
        PhotoEntity::class,
        ComboEntity::class,
        ComboItemEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(BackgroundConverters::class)
@ConstructedBy(AppDbConstructor::class)
abstract class AppDb : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun combos(): ComboDao
}

/**
 * Конструктор базы: реализацию (`actual`) для каждой платформы генерирует компилятор Room,
 * вручную её писать не нужно.
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDbConstructor : RoomDatabaseConstructor<AppDb> {
    override fun initialize(): AppDb
}
