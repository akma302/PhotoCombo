package com.combo.core.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * Системный диалог выбора файлов. Работает в EDT ([Dispatchers.Swing]): AWT-диалог
 * нельзя открывать из произвольного потока. Пустой список — пользователь отменил выбор.
 */
class DesktopImagePicker(private val files: FileSystem) : ImagePicker {

    override suspend fun pickImages(): List<ImageSource> = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Выберите фото", FileDialog.LOAD).apply {
            isMultipleMode = true
            filenameFilter = FilenameFilter { _, name ->
                name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
            }
        }
        dialog.isVisible = true
        dialog.files.orEmpty().map { FileImageSource(it.toOkioPath(), files) }
    }
}
