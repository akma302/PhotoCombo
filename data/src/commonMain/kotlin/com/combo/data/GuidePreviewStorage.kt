package com.combo.data

import com.combo.core.platform.AppDirs
import okio.FileSystem
import okio.IOException

class GuidePreviewStorage(
    dirs: AppDirs,
    private val files: FileSystem,
) {
    private val path = dirs.dataDir / "guide-preview.txt"
    private val temporary = dirs.dataDir / "guide-preview.txt.tmp"

    fun read(): Boolean = files.exists(path) && files.read(path) { readUtf8().trim() == "true" }

    fun write(enabled: Boolean) {
        files.createDirectories(checkNotNull(path.parent))
        try {
            files.write(temporary) { writeUtf8(enabled.toString()) }
            files.atomicMove(temporary, path)
        } finally {
            try { files.delete(temporary, mustExist = false) } catch (_: IOException) { }
        }
    }
}
