package com.combo.core.platform

import okio.Path

/** Каталог данных, выбранный при старте приложения (см. [defaultAppDataDir]). */
class DesktopAppDirs(override val dataDir: Path) : AppDirs
