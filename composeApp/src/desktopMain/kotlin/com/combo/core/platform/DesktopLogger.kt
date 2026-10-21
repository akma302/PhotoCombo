package com.combo.core.platform

/** Логгер в stderr: у desktop-приложения нет системы логирования уровня ОС. */
class DesktopLogger : Logger {
    override fun warn(tag: String, message: String, error: Throwable?) {
        System.err.println("W/$tag: $message")
        error?.printStackTrace()
    }
}
