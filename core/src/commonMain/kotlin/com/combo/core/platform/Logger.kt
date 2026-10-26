package com.combo.core.platform

/**
 * Минимальный логгер домена. Домен сообщает о некритичных сбоях (нечитаемый файл,
 * неудачная запись) и продолжает работу, а не роняет приложение.
 */
interface Logger {
    fun warn(tag: String, message: String, error: Throwable? = null)
}
