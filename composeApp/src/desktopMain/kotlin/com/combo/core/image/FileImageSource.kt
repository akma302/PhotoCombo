package com.combo.core.image

import okio.FileSystem
import okio.Path
import okio.Source

/** Файл на диске как источник импорта: выбран в диалоге или подсунут тестом. */
class FileImageSource(
    private val path: Path,
    private val files: FileSystem,
) : ImageSource {

    override val name: String get() = path.name

    override val mimeType: String get() = mimeTypeForName(path.name)

    override fun openSource(): Source = files.source(path)
}

/**
 * Mime-тип по расширению файла. Неизвестные расширения считаются JPEG: диалог выбора
 * фильтрует по списку расширений, а импорт всё равно проверяет файл декодированием.
 */
fun mimeTypeForName(name: String): String = when {
    name.endsWith(".png", true) -> "image/png"
    name.endsWith(".webp", true) -> "image/webp"
    else -> "image/jpeg"
}
