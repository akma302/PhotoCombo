package com.combo.core.image

import androidx.compose.ui.graphics.ImageBitmap
import com.combo.core.platform.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

class DesktopPngOutput(
    private val codec: ImageCodec,
    private val dirs: AppDirs,
) : PngOutput {
    override suspend fun chooseDestination(name: String): PngDestination? = withContext(Dispatchers.Swing) {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
        val dialog = FileDialog(owner, "Export PNG", FileDialog.SAVE).apply {
            file = suggestedPngName(name)
        }
        try {
            dialog.isVisible = true
            val selected = dialog.files.singleOrNull() ?: return@withContext null
            val pngName = selected.name.withPngExtension()
            val path = selected.toPath().resolveSibling(pngName)
            // The native dialog prompts only for the exact path it returns.
            PngDestination(path.toOkioPath(), replaceExisting = pngName == selected.name && Files.exists(path))
        } finally {
            dialog.dispose()
        }
    }

    override suspend fun write(destination: PngDestination, image: ImageBitmap) {
        val selected = destination.path.toNioPath().toAbsolutePath()
        val parent = selected.parent.toRealPath()
        val dataDir = dirs.dataDir.toNioPath().toAbsolutePath().normalize()
        val library = if (Files.exists(dataDir)) dataDir.toRealPath() else dataDir
        require(!parent.startsWith(library)) { "Choose a folder outside the PhotoCombo library." }

        val target = parent.resolve(selected.fileName)
        val temporary = Files.createTempFile(parent, ".photocombo-", ".tmp")
        try {
            check(codec.encode(temporary.toOkioPath(), image, ImageFormat.PNG, quality = 100)) {
                "The PNG could not be encoded or written."
            }
            currentCoroutineContext().ensureActive()
            if (destination.replaceExisting) {
                Files.move(temporary, target, ATOMIC_MOVE)
            } else {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private val invalidFilenameChars = Regex("""[<>:"/\\|?*\p{Cntrl}]""")
private val reservedFilename = Regex("""CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³]""", RegexOption.IGNORE_CASE)

internal fun suggestedPngName(name: String): String {
    val sanitized = name.replace(invalidFilenameChars, "_").take(120).trim(' ', '.').ifEmpty { "Combo" }
    val base = if (reservedFilename.matches(sanitized.substringBefore('.').trimEnd())) "_$sanitized" else sanitized
    return base.withPngExtension()
}

private fun String.withPngExtension(): String = if (endsWith(".png", ignoreCase = true)) this else "$this.png"
