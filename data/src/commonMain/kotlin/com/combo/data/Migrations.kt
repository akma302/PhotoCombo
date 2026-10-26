package com.combo.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Миграции базы. Каждая повышает версию схемы на один шаг; текст SQL — часть формата
 * хранения, менять его в уже выпущенной миграции нельзя.
 */

/**
 * 1 → 2: «одна рамка — одно фото». Таблица `combo_item_contents` исчезает, поля фото
 * переезжают в `combo_items`.
 *
 * - В рамке остаётся фото с наименьшим `orderIndex` (при равенстве — с наименьшим `id`).
 * - Рамка без единого фото удаляется: пустых рамок в новой модели не бывает.
 * - Поля `switchMode`, `autoIntervalMs`, `manualTransition`, `autoTransition` отбрасываются.
 * - Таблица пересоздаётся (а не правится `ALTER TABLE`): SQLite не умеет добавлять внешние
 *   ключи к существующей таблице. Новая таблица создаётся под временным именем, потому что
 *   старая нужна как источник данных.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE `combo_items_new` (
                `id` TEXT NOT NULL,
                `comboId` TEXT NOT NULL,
                `zIndex` INTEGER NOT NULL,
                `centerX` REAL NOT NULL,
                `centerY` REAL NOT NULL,
                `width` REAL NOT NULL,
                `height` REAL NOT NULL,
                `rotation` REAL NOT NULL,
                `framingMode` TEXT NOT NULL,
                `photoId` TEXT NOT NULL,
                `contentScale` REAL NOT NULL,
                `panX` REAL NOT NULL,
                `panY` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`comboId`) REFERENCES `combos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `combo_items_new`
                (`id`, `comboId`, `zIndex`, `centerX`, `centerY`, `width`, `height`, `rotation`,
                 `framingMode`, `photoId`, `contentScale`, `panX`, `panY`)
            SELECT i.`id`, i.`comboId`, i.`zIndex`, i.`centerX`, i.`centerY`, i.`width`, i.`height`,
                   i.`rotation`, i.`framingMode`, c.`photoId`, c.`contentScale`, c.`panX`, c.`panY`
            FROM `combo_items` i
            JOIN `combo_item_contents` c ON c.`itemId` = i.`id`
            WHERE c.`id` = (
                SELECT c2.`id` FROM `combo_item_contents` c2
                WHERE c2.`itemId` = i.`id`
                ORDER BY c2.`orderIndex` ASC, c2.`id` ASC
                LIMIT 1
            )
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `combo_item_contents`")
        connection.execSQL("DROP TABLE `combo_items`")
        connection.execSQL("ALTER TABLE `combo_items_new` RENAME TO `combo_items`")
        connection.execSQL("CREATE INDEX `index_combo_items_comboId` ON `combo_items` (`comboId`)")
        connection.execSQL("CREATE INDEX `index_combo_items_photoId` ON `combo_items` (`photoId`)")
    }
}

/** Все миграции по порядку; подключаются в `createAppDb` и в тестах. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
