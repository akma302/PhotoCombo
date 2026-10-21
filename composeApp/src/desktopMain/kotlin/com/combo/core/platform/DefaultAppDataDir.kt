package com.combo.core.platform

import java.io.File

private const val APP_DIR_NAME = "PhotoCombo"

/**
 * Каталог данных приложения: `%APPDATA%\PhotoCombo` на Windows,
 * `~/Library/Application Support/PhotoCombo` на macOS, `$XDG_DATA_HOME/PhotoCombo`
 * (или `~/.local/share/PhotoCombo`) на Linux. Каталог не создаётся.
 *
 * Явно НЕ `java.io.tmpdir`: временный каталог система вправе очистить.
 */
fun defaultAppDataDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home").orEmpty()
    return when {
        "win" in os -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, APP_DIR_NAME)
        "mac" in os -> File(home, "Library/Application Support/$APP_DIR_NAME")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, APP_DIR_NAME)
    }
}
