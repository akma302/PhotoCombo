package com.combo.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.combo.core.platform.defaultAppDataDir
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Имя файла базы. Не меняется: это данные пользователя на диске. */
const val DB_FILE_NAME = "combo.db"

/**
 * Открывает базу в файле [file] (родительский каталог создаётся). Драйвер SQLite
 * встроенный (`BundledSQLiteDriver`): версия SQLite не зависит от системы.
 * По умолчанию файл лежит в каталоге данных приложения (см. [defaultAppDataDir]).
 */
fun createAppDb(file: File = File(defaultAppDataDir(), DB_FILE_NAME)): AppDb {
    file.absoluteFile.parentFile?.mkdirs()
    return Room.databaseBuilder<AppDb>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*ALL_MIGRATIONS)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
