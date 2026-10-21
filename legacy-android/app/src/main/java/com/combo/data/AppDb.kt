package com.combo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * База приложения: фото, комбо, рамки комбо и их фото. Схема экспортируется в
 * `app/schemas`. Миграций нет: при любом изменении сущностей нужно повысить `version` и
 * написать миграцию.
 */
@Database(
    entities = [
        PhotoEntity::class,
        ComboEntity::class,
        ComboItemEntity::class,
        ComboItemContentEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(BackgroundConverters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun combos(): ComboDao
}
