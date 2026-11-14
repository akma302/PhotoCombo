# PhotoCombo — полный индекс исходников

Снимок `7351a27b9daa140471cae8295a4aca88441ca572`, 2026-11-14. **135 Main-файлов**, включая `test-support`; **78 файлов тестов**.

Этот индекс автоматически читает весь `src/commonMain` и `src/desktopMain` зарегистрированных модулей. Объявления извлекаются по началу строки, а не Kotlin-компилятором: здесь показан top-level API, включая константы и private helpers. Члены классов и вложенные типы изучайте по ссылке на исходник. Импорт — зависимость имени, не доказательство вызова. Использования в том же пакете не требуют import.

[Открыть атлас](open-atlas.html) · [Onboarding](ONBOARDING.md)

## :composeApp

### AppModules.kt

[composeApp/src/commonMain/kotlin/com/combo/di/AppModules.kt](../../composeApp/src/commonMain/kotlin/com/combo/di/AppModules.kt)

`com.combo.di` · `commonMain`

```kotlin
// L26 · public
val appModule = module {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.data.PhotoStorage
import com.combo.data.FileGallerySettingsStorage
import com.combo.data.GallerySettingsStorage
import com.combo.domain.GallerySettingsRepository
import com.combo.domain.GallerySettingsRepositoryImpl
import com.combo.domain.ComboRepository
import com.combo.domain.ComboRepositoryImpl
import com.combo.domain.ComboPngExporter
import com.combo.domain.PhotoImporter
import com.combo.domain.PhotoRepository
import com.combo.domain.PhotoRepositoryImpl
```

</details>

### ViewModelModule.kt

[composeApp/src/commonMain/kotlin/com/combo/di/ViewModelModule.kt](../../composeApp/src/commonMain/kotlin/com/combo/di/ViewModelModule.kt)

`com.combo.di` · `commonMain`

```kotlin
// L25 · public
val viewModelModule = module {
// L61 · public
class EntryArgs(val handle: SavedStateHandle)
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.domain.ComboPngExporter
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListViewModel
import com.combo.feature.library.LibraryViewModel
import com.combo.feature.viewer.PhotoViewerViewModel
```

</details>

### AppNav.kt

[composeApp/src/commonMain/kotlin/com/combo/ui/AppNav.kt](../../composeApp/src/commonMain/kotlin/com/combo/ui/AppNav.kt)

`com.combo.ui` · `commonMain`

```kotlin
// L60 · public
fun AppNav(backDispatcher: BackDispatcher) {
// L147 · private
private fun builderHandle(key: ScreenKey.Builder): SavedStateHandle {
// L157 · private
private fun EntryScope(content: @Composable () -> Unit) {
// L168 · private
private fun MutableList<NavKey>.switchTab(tab: NavKey) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.components.PcSegmentedControl
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.di.EntryArgs
import com.combo.feature.builder.ComboBuilderScreen
import com.combo.feature.builder.ComboBuilderViewModel
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.library.LibraryScreen
import com.combo.feature.viewer.PhotoViewerScreen
import com.combo.ui.common.BackDispatcher
import com.combo.ui.common.BackKeyHost
import com.combo.ui.common.LocalBackDispatcher
import com.combo.ui.common.RegisterBackHandler
```

</details>

### ScreenKey.kt

[composeApp/src/commonMain/kotlin/com/combo/ui/ScreenKey.kt](../../composeApp/src/commonMain/kotlin/com/combo/ui/ScreenKey.kt)

`com.combo.ui` · `commonMain`

```kotlin
// L13 · public
sealed interface ScreenKey : NavKey {
```

### ViewModelStoreOwners.kt

[composeApp/src/commonMain/kotlin/com/combo/ui/ViewModelStoreOwners.kt](../../composeApp/src/commonMain/kotlin/com/combo/ui/ViewModelStoreOwners.kt)

`com.combo.ui` · `commonMain`

```kotlin
// L9 · private
private class StackViewModelStoreOwner : ViewModelStoreOwner {
// L26 · public
fun rememberEntryViewModelStoreOwner(): ViewModelStoreOwner {
// L40 · public
fun rememberRootViewModelStoreOwner(): ViewModelStoreOwner = remember { StackViewModelStoreOwner() }
```

### DesktopModules.kt

[composeApp/src/desktopMain/kotlin/com/combo/di/DesktopModules.kt](../../composeApp/src/desktopMain/kotlin/com/combo/di/DesktopModules.kt)

`com.combo.di` · `desktopMain`

```kotlin
// L40 · public
fun desktopModule(dataDir: Path = defaultAppDataDir().toOkioPath()): Module = module {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.DesktopImagePicker
import com.combo.core.image.DesktopPngOutput
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImagePicker
import com.combo.core.image.PngOutput
import com.combo.core.image.SkiaImageCodec
import com.combo.core.platform.AppDirs
import com.combo.core.platform.DesktopAppDirs
import com.combo.core.platform.DesktopLogger
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.Logger
import com.combo.core.platform.WallClock
import com.combo.core.platform.defaultAppDataDir
import com.combo.data.AppDb
import com.combo.data.ComboDao
import com.combo.data.DB_FILE_NAME
import com.combo.data.PhotoDao
import com.combo.data.createAppDb
import com.combo.domain.BitmapSource
import com.combo.domain.image.LruBitmapSource
```

</details>

### main.kt

[composeApp/src/desktopMain/kotlin/com/combo/main.kt](../../composeApp/src/desktopMain/kotlin/com/combo/main.kt)

`com.combo` · `desktopMain`

```kotlin
// L32 · public
fun main() {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.di.appModule
import com.combo.di.desktopModule
import com.combo.di.viewModelModule
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.AppNav
import com.combo.ui.common.BackDispatcher
```

</details>

### GalleryLauncher.kt

[composeApp/src/desktopMain/kotlin/com/combo/ui/GalleryLauncher.kt](../../composeApp/src/desktopMain/kotlin/com/combo/ui/GalleryLauncher.kt)

`com.combo.ui` · `desktopMain`

```kotlin
// L18 · public
fun main() = application {
// L28 · public
fun sharedGalleryExamples(): List<GalleryExample> = listOf(GalleryExample("Gallery grid") {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.GalleryGrid
import com.combo.ui.common.PcDesktopTheme
import com.combo.ui.gallery.DesignSystemGallery
import com.combo.ui.gallery.GalleryExample
```

</details>

## :core

### Const.kt

[core/src/commonMain/kotlin/com/combo/Const.kt](../../core/src/commonMain/kotlin/com/combo/Const.kt)

`com.combo` · `commonMain`

```kotlin
// L7 · public
object Const {
```

### AverageAreaOklabStrategy.kt

[core/src/commonMain/kotlin/com/combo/core/background/AverageAreaOklabStrategy.kt](../../core/src/commonMain/kotlin/com/combo/core/background/AverageAreaOklabStrategy.kt)

`com.combo.core.background` · `commonMain`

```kotlin
// L17 · public
object AverageAreaOklabStrategy : BackgroundStrategy {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.color.Oklab
```

</details>

### Background.kt

[core/src/commonMain/kotlin/com/combo/core/background/Background.kt](../../core/src/commonMain/kotlin/com/combo/core/background/Background.kt)

`com.combo.core.background` · `commonMain`

```kotlin
// L15 · public
sealed interface BackgroundSpec {
// L27 · public
sealed interface ResolvedBackground {
// L32 · public
data class FrameInput(
// L41 · public
data class BackgroundInput(
// L47 · public
interface BackgroundStrategy {
// L58 · public
class BackgroundResolver(
// L79 · public
object BackgroundPresets {
```

### Oklab.kt

[core/src/commonMain/kotlin/com/combo/core/color/Oklab.kt](../../core/src/commonMain/kotlin/com/combo/core/color/Oklab.kt)

`com.combo.core.color` · `commonMain`

```kotlin
// L15 · public
object Oklab {
```

### ExifOrientation.kt

[core/src/commonMain/kotlin/com/combo/core/image/ExifOrientation.kt](../../core/src/commonMain/kotlin/com/combo/core/image/ExifOrientation.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L8 · public
enum class ExifOrientation(val exifValue: Int) {
// L25 · public
fun exifOrientationOf(value: Int): ExifOrientation =
```

### ExifTransform.kt

[core/src/commonMain/kotlin/com/combo/core/image/ExifTransform.kt](../../core/src/commonMain/kotlin/com/combo/core/image/ExifTransform.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L19 · public
fun applyExifOrientation(image: ImageBitmap, orientation: ExifOrientation): ImageBitmap {
```

### ImageCodec.kt

[core/src/commonMain/kotlin/com/combo/core/image/ImageCodec.kt](../../core/src/commonMain/kotlin/com/combo/core/image/ImageCodec.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L8 · public
enum class ImageFormat {
// L18 · public
interface ImageCodec {
```

### ImagePicker.kt

[core/src/commonMain/kotlin/com/combo/core/image/ImagePicker.kt](../../core/src/commonMain/kotlin/com/combo/core/image/ImagePicker.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L7 · public
interface ImagePicker {
```

### ImageSource.kt

[core/src/commonMain/kotlin/com/combo/core/image/ImageSource.kt](../../core/src/commonMain/kotlin/com/combo/core/image/ImageSource.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L10 · public
interface ImageSource {
```

### PngOutput.kt

[core/src/commonMain/kotlin/com/combo/core/image/PngOutput.kt](../../core/src/commonMain/kotlin/com/combo/core/image/PngOutput.kt)

`com.combo.core.image` · `commonMain`

```kotlin
// L6 · public
data class PngDestination(val path: Path, val replaceExisting: Boolean)
// L8 · public
interface PngOutput {
```

### Model.kt

[core/src/commonMain/kotlin/com/combo/core/model/Model.kt](../../core/src/commonMain/kotlin/com/combo/core/model/Model.kt)

`com.combo.core.model` · `commonMain`

```kotlin
// L18 · public
enum class FramingMode { FREE, FIT }
```

### AppDirs.kt

[core/src/commonMain/kotlin/com/combo/core/platform/AppDirs.kt](../../core/src/commonMain/kotlin/com/combo/core/platform/AppDirs.kt)

`com.combo.core.platform` · `commonMain`

```kotlin
// L10 · public
interface AppDirs {
```

### Logger.kt

[core/src/commonMain/kotlin/com/combo/core/platform/Logger.kt](../../core/src/commonMain/kotlin/com/combo/core/platform/Logger.kt)

`com.combo.core.platform` · `commonMain`

```kotlin
// L7 · public
interface Logger {
```

### Providers.kt

[core/src/commonMain/kotlin/com/combo/core/platform/Providers.kt](../../core/src/commonMain/kotlin/com/combo/core/platform/Providers.kt)

`com.combo.core.platform` · `commonMain`

```kotlin
// L8 · public
fun interface WallClock {
// L13 · public
fun interface IdGenerator {
```

### Aspect.kt

[core/src/commonMain/kotlin/com/combo/core/render/Aspect.kt](../../core/src/commonMain/kotlin/com/combo/core/render/Aspect.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L9 · public
data class CanvasFormat(val w: Int, val h: Int) {
// L27 · public
fun minBorderSize(canvasW: Float, canvasH: Float): Float =
// L35 · public
object AspectPresets {
// L76 · public
fun relayout(
// L134 · private
private fun coverClamp(pan: Float, contentSize: Float, borderSize: Float): Float {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.model.FramingMode
```

</details>

### BackgroundDraw.kt

[core/src/commonMain/kotlin/com/combo/core/render/BackgroundDraw.kt](../../core/src/commonMain/kotlin/com/combo/core/render/BackgroundDraw.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L16 · public
fun DrawScope.drawComboBackground(bg: ResolvedBackground, topLeft: Offset, size: Size) {
// L27 · public
fun frameInputOf(border: Border, photoColors: List<Int>): FrameInput {
// L32 · public
fun backgroundInputOf(canvasW: Float, canvasH: Float, frames: List<FrameInput>) =
// L39 · public
fun resolveBackground(spec: BackgroundSpec, input: BackgroundInput): ResolvedBackground =
// L51 · public
fun comboBackgroundOf(
// L63 · public
fun comboBackgroundOf(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.BackgroundInput
import com.combo.core.background.BackgroundSpec
import com.combo.core.background.FrameInput
import com.combo.core.background.ResolvedBackground
```

</details>

### Drafts.kt

[core/src/commonMain/kotlin/com/combo/core/render/Drafts.kt](../../core/src/commonMain/kotlin/com/combo/core/render/Drafts.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L17 · public
class ItemDraft(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.model.FramingMode
```

</details>

### DrawCombo.kt

[core/src/commonMain/kotlin/com/combo/core/render/DrawCombo.kt](../../core/src/commonMain/kotlin/com/combo/core/render/DrawCombo.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L30 · public
fun DrawScope.drawCombo(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.ResolvedBackground
```

</details>

### DrawItem.kt

[core/src/commonMain/kotlin/com/combo/core/render/DrawItem.kt](../../core/src/commonMain/kotlin/com/combo/core/render/DrawItem.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L14 · public
data class ContentDrawArgs(
// L22 · public
data class ItemFrame(
// L29 · public
fun DrawScope.drawItem(f: ItemFrame) {
// L39 · private
private fun Rect.toIntOffset() = IntOffset(left.roundToInt(), top.roundToInt())
// L40 · private
private fun Rect.toIntSize() = IntSize(width.roundToInt(), height.roundToInt())
// L43 · public
fun visiblePhotoBounds(border: Border, srcW: Int, srcH: Int, framing: Framing): Rect {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
```

</details>

### Framing.kt

[core/src/commonMain/kotlin/com/combo/core/render/Framing.kt](../../core/src/commonMain/kotlin/com/combo/core/render/Framing.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L13 · public
data class Framing(val scale: Float, val panX: Float, val panY: Float)
// L23 · public
fun effectiveFraming(
// L45 · public
fun contentArgsOf(
// L69 · private
private fun fitScaleOf(border: Border, srcW: Int, srcH: Int): Float =
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.model.FramingMode
```

</details>

### Geometry.kt

[core/src/commonMain/kotlin/com/combo/core/render/Geometry.kt](../../core/src/commonMain/kotlin/com/combo/core/render/Geometry.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L7 · public
data class Border(val centerX: Float, val centerY: Float, val width: Float, val height: Float) {
// L12 · public
enum class Edge { LEFT, TOP, RIGHT, BOTTOM }
// L14 · public
data class Viewport(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {
// L33 · public
fun viewportZoomPan(
// L51 · public
data class CanvasTransform(val scale: Float, val origin: Offset) {
```

### ItemFrames.kt

[core/src/commonMain/kotlin/com/combo/core/render/ItemFrames.kt](../../core/src/commonMain/kotlin/com/combo/core/render/ItemFrames.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L12 · public
class FrameContent(
// L32 · public
fun buildItemFrame(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.model.FramingMode
```

</details>

### ItemGestures.kt

[core/src/commonMain/kotlin/com/combo/core/render/ItemGestures.kt](../../core/src/commonMain/kotlin/com/combo/core/render/ItemGestures.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L6 · public
object ItemGestures {
// L64 · internal
internal class FrameEdgeResize(frame: Border, edge: Edge, minSize: Float, private val oneSided: Boolean) {
// L91 · internal
internal fun moveFrame(frame: Border, dx: Float, dy: Float, canvasW: Float, canvasH: Float): Border =
```

### LayoutPresets.kt

[core/src/commonMain/kotlin/com/combo/core/render/LayoutPresets.kt](../../core/src/commonMain/kotlin/com/combo/core/render/LayoutPresets.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L8 · public
object LayoutPresets {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.model.FramingMode
```

</details>

### FrameSnap.kt

[core/src/commonMain/kotlin/com/combo/core/render/snap/FrameSnap.kt](../../core/src/commonMain/kotlin/com/combo/core/render/snap/FrameSnap.kt)

`com.combo.core.render.snap` · `commonMain`

```kotlin
// L10 · public
enum class SnapAxis { X, Y }
// L12 · public
data class SnapGuide(
// L19 · public
data class SnapDistances(val capture: Float, val release: Float)
// L21 · public
data class SnapStep(val state: FrameSnapState, val placed: Border, val guides: List<SnapGuide>)
// L23 · internal
class FrameSnapState internal constructor(
// L34 · private
class FrameSnap private constructor(
// L122 · internal
internal data class Alignment(val position: Float, val coordinate: Float, val target: Border?) {
// L133 · private
private fun choose(
// L155 · private
private fun placeCenter(frame: Border, x: Float?, y: Float?): Border =
// L158 · private
private fun photoAlignments(axis: SnapAxis, frame: Border, photo: Border): List<Alignment> {
// L164 · private
private fun alignments(axis: SnapAxis, frame: Border, targets: List<Border>, limit: Float): List<Alignment> {
// L181 · private
private fun SnapAxis.center(frame: Border): Float = if (this == SnapAxis.X) frame.centerX else frame.centerY
// L182 · private
private fun SnapAxis.halfSize(frame: Border): Float = if (this == SnapAxis.X) frame.width / 2f else frame.height / 2f
// L183 · private
private fun SnapAxis.crossStart(frame: Border): Float =
// L185 · private
private fun SnapAxis.crossEnd(frame: Border): Float =
// L187 · private
private fun SnapAxis.crossGap(a: Border, b: Border): Float =
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.Border
import com.combo.core.render.Edge
import com.combo.core.render.FrameEdgeResize
import com.combo.core.render.clampPhotoPan
import com.combo.core.render.moveFrame
```

</details>

### TransformMath.kt

[core/src/commonMain/kotlin/com/combo/core/render/TransformMath.kt](../../core/src/commonMain/kotlin/com/combo/core/render/TransformMath.kt)

`com.combo.core.render` · `commonMain`

```kotlin
// L11 · public
object TransformMath {
// L39 · internal
internal fun clampPhotoPan(pan: Float, frameSize: Float, photoSize: Float): Float {
```

### AverageColor.kt

[core/src/commonMain/kotlin/com/combo/core/util/AverageColor.kt](../../core/src/commonMain/kotlin/com/combo/core/util/AverageColor.kt)

`com.combo.core.util` · `commonMain`

```kotlin
// L9 · public
object AverageColor {
```

### UndoStack.kt

[core/src/commonMain/kotlin/com/combo/core/util/UndoStack.kt](../../core/src/commonMain/kotlin/com/combo/core/util/UndoStack.kt)

`com.combo.core.util` · `commonMain`

```kotlin
// L9 · public
class UndoStack<T>(private val maxDepth: Int = 50) {
```

### DesktopImagePicker.kt

[core/src/desktopMain/kotlin/com/combo/core/image/DesktopImagePicker.kt](../../core/src/desktopMain/kotlin/com/combo/core/image/DesktopImagePicker.kt)

`com.combo.core.image` · `desktopMain`

```kotlin
// L12 · private
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
// L18 · public
class DesktopImagePicker(private val files: FileSystem) : ImagePicker {
```

### DesktopPngOutput.kt

[core/src/desktopMain/kotlin/com/combo/core/image/DesktopPngOutput.kt](../../core/src/desktopMain/kotlin/com/combo/core/image/DesktopPngOutput.kt)

`com.combo.core.image` · `desktopMain`

```kotlin
// L17 · public
class DesktopPngOutput(
// L63 · private
private val invalidFilenameChars = Regex("""[<>:"/\\|?*\p{Cntrl}]""")
// L64 · private
private val reservedFilename = Regex("""CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³]""", RegexOption.IGNORE_CASE)
// L66 · internal
internal fun suggestedPngName(name: String): String {
// L72 · private
private fun String.withPngExtension(): String = if (endsWith(".png", ignoreCase = true)) this else "$this.png"
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.AppDirs
```

</details>

### FileImageSource.kt

[core/src/desktopMain/kotlin/com/combo/core/image/FileImageSource.kt](../../core/src/desktopMain/kotlin/com/combo/core/image/FileImageSource.kt)

`com.combo.core.image` · `desktopMain`

```kotlin
// L8 · public
class FileImageSource(
// L24 · public
fun mimeTypeForName(name: String): String = when {
```

### SkiaImageCodec.kt

[core/src/desktopMain/kotlin/com/combo/core/image/SkiaImageCodec.kt](../../core/src/desktopMain/kotlin/com/combo/core/image/SkiaImageCodec.kt)

`com.combo.core.image` · `desktopMain`

```kotlin
// L25 · private
private const val TAG = "SkiaImageCodec"
// L28 · private
private const val PNG_COMPRESSION_LEVEL = 6
// L36 · public
class SkiaImageCodec(
// L142 · private
private fun EncodedOrigin.toExifOrientation(): ExifOrientation = when (this) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.Logger
```

</details>

### DefaultAppDataDir.kt

[core/src/desktopMain/kotlin/com/combo/core/platform/DefaultAppDataDir.kt](../../core/src/desktopMain/kotlin/com/combo/core/platform/DefaultAppDataDir.kt)

`com.combo.core.platform` · `desktopMain`

```kotlin
// L5 · private
private const val APP_DIR_NAME = "PhotoCombo"
// L14 · public
fun defaultAppDataDir(): File {
```

### DesktopAppDirs.kt

[core/src/desktopMain/kotlin/com/combo/core/platform/DesktopAppDirs.kt](../../core/src/desktopMain/kotlin/com/combo/core/platform/DesktopAppDirs.kt)

`com.combo.core.platform` · `desktopMain`

```kotlin
// L6 · public
class DesktopAppDirs(override val dataDir: Path) : AppDirs
```

### DesktopLogger.kt

[core/src/desktopMain/kotlin/com/combo/core/platform/DesktopLogger.kt](../../core/src/desktopMain/kotlin/com/combo/core/platform/DesktopLogger.kt)

`com.combo.core.platform` · `desktopMain`

```kotlin
// L4 · public
class DesktopLogger : Logger {
```

## :data

### AppDb.kt

[data/src/commonMain/kotlin/com/combo/data/AppDb.kt](../../data/src/commonMain/kotlin/com/combo/data/AppDb.kt)

`com.combo.data` · `commonMain`

```kotlin
// L25 · public
abstract class AppDb : RoomDatabase() {
// L35 · public
expect object AppDbConstructor : RoomDatabaseConstructor<AppDb> {
```

### BackgroundConverters.kt

[data/src/commonMain/kotlin/com/combo/data/BackgroundConverters.kt](../../data/src/commonMain/kotlin/com/combo/data/BackgroundConverters.kt)

`com.combo.data` · `commonMain`

```kotlin
// L13 · public
class BackgroundConverters {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.BackgroundSpec
```

</details>

### Daos.kt

[data/src/commonMain/kotlin/com/combo/data/Daos.kt](../../data/src/commonMain/kotlin/com/combo/data/Daos.kt)

`com.combo.data` · `commonMain`

```kotlin
// L12 · public
interface PhotoDao {
// L30 · public
interface ComboDao {
```

### Entities.kt

[data/src/commonMain/kotlin/com/combo/data/Entities.kt](../../data/src/commonMain/kotlin/com/combo/data/Entities.kt)

`com.combo.data` · `commonMain`

```kotlin
// L13 · public
data class PhotoEntity(
// L27 · public
data class ComboEntity(
// L61 · public
data class ComboItemEntity(
// L77 · public
data class ComboWithItems(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
```

</details>

### EntityExt.kt

[data/src/commonMain/kotlin/com/combo/data/EntityExt.kt](../../data/src/commonMain/kotlin/com/combo/data/EntityExt.kt)

`com.combo.data` · `commonMain`

```kotlin
// L17 · public
fun ComboItemEntity.border(): Border = Border(centerX, centerY, width, height)
// L20 · public
fun PhotoEntity.srcSize(): Size = Size(width.toFloat(), height.toFloat())
// L29 · public
fun ComboWithItems.backgroundFrames(photos: Map<String, PhotoEntity>): List<Pair<Border, List<Int>>> =
// L39 · public
fun ComboItemEntity.frameContent(image: ImageBitmap, photo: PhotoEntity) = FrameContent(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.Border
import com.combo.core.render.FrameContent
```

</details>

### GallerySettingsStorage.kt

[data/src/commonMain/kotlin/com/combo/data/GallerySettingsStorage.kt](../../data/src/commonMain/kotlin/com/combo/data/GallerySettingsStorage.kt)

`com.combo.data` · `commonMain`

```kotlin
// L9 · public
data class GallerySettings(val photosPercent: Float = 100f, val combosPercent: Float = 100f)
// L11 · public
fun normalizedGalleryPercent(value: Float): Float =
// L14 · public
interface GallerySettingsStorage {
// L19 · public
class FileGallerySettingsStorage(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.AppDirs
```

</details>

### Migrations.kt

[data/src/commonMain/kotlin/com/combo/data/Migrations.kt](../../data/src/commonMain/kotlin/com/combo/data/Migrations.kt)

`com.combo.data` · `commonMain`

```kotlin
// L23 · public
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
// L73 · public
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
```

### PhotoStorage.kt

[data/src/commonMain/kotlin/com/combo/data/PhotoStorage.kt](../../data/src/commonMain/kotlin/com/combo/data/PhotoStorage.kt)

`com.combo.data` · `commonMain`

```kotlin
// L14 · public
class PhotoStorage(private val dirs: AppDirs, private val files: FileSystem) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.AppDirs
```

</details>

### DatabaseFactory.kt

[data/src/desktopMain/kotlin/com/combo/data/DatabaseFactory.kt](../../data/src/desktopMain/kotlin/com/combo/data/DatabaseFactory.kt)

`com.combo.data` · `desktopMain`

```kotlin
// L10 · public
const val DB_FILE_NAME = "combo.db"
// L17 · public
fun createAppDb(file: File = File(defaultAppDataDir(), DB_FILE_NAME)): AppDb {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.defaultAppDataDir
```

</details>

## :domain

### ComboPngExporter.kt

[domain/src/commonMain/kotlin/com/combo/domain/ComboPngExporter.kt](../../domain/src/commonMain/kotlin/com/combo/domain/ComboPngExporter.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L26 · public
class ComboPngExporter(
// L71 · private
private fun ComboItemEntity.exportPixels(photo: PhotoEntity): Int {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.image.PngOutput
import com.combo.core.render.CanvasTransform
import com.combo.core.render.buildItemFrame
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.core.render.effectiveFraming
import com.combo.data.ComboItemEntity
import com.combo.data.PhotoEntity
import com.combo.data.backgroundFrames
import com.combo.data.border
import com.combo.data.frameContent
```

</details>

### GallerySettingsRepository.kt

[domain/src/commonMain/kotlin/com/combo/domain/GallerySettingsRepository.kt](../../domain/src/commonMain/kotlin/com/combo/domain/GallerySettingsRepository.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L13 · public
enum class GalleryKind { Photos, Combos }
// L15 · public
interface GallerySettingsRepository {
// L21 · public
class GallerySettingsRepositoryImpl(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.platform.Logger
import com.combo.data.GallerySettings
import com.combo.data.GallerySettingsStorage
import com.combo.data.normalizedGalleryPercent
```

</details>

### LruBitmapSource.kt

[domain/src/commonMain/kotlin/com/combo/domain/image/LruBitmapSource.kt](../../domain/src/commonMain/kotlin/com/combo/domain/image/LruBitmapSource.kt)

`com.combo.domain.image` · `commonMain`

```kotlin
// L34 · public
class LruBitmapSource(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ImageCodec
import com.combo.data.PhotoDao
import com.combo.data.PhotoStorage
import com.combo.domain.BitmapSource
```

</details>

### Impls.kt

[domain/src/commonMain/kotlin/com/combo/domain/Impls.kt](../../domain/src/commonMain/kotlin/com/combo/domain/Impls.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L22 · public
class PhotoRepositoryImpl(
// L51 · public
class ComboRepositoryImpl(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ImageSource
import com.combo.data.AppDb
import com.combo.data.ComboDao
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
```

</details>

### PhotoAssets.kt

[domain/src/commonMain/kotlin/com/combo/domain/PhotoAssets.kt](../../domain/src/commonMain/kotlin/com/combo/domain/PhotoAssets.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L24 · public
class PhotoAssets(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.data.PhotoEntity
```

</details>

### PhotoImporter.kt

[domain/src/commonMain/kotlin/com/combo/domain/PhotoImporter.kt](../../domain/src/commonMain/kotlin/com/combo/domain/PhotoImporter.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L23 · private
private const val TAG = "PhotoRepository"
// L35 · public
class PhotoImporter(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.image.ImageSource
import com.combo.core.image.applyExifOrientation
import com.combo.core.platform.Logger
import com.combo.core.util.AverageColor
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
import com.combo.data.PhotoStorage
```

</details>

### Repositories.kt

[domain/src/commonMain/kotlin/com/combo/domain/Repositories.kt](../../domain/src/commonMain/kotlin/com/combo/domain/Repositories.kt)

`com.combo.domain` · `commonMain`

```kotlin
// L12 · public
data class ImportResult(val added: Int, val duplicates: Int, val failed: Int)
// L14 · public
enum class DeleteResult { DELETED, BLOCKED_IF_USED_IN_COMBO }
// L16 · public
interface PhotoRepository {
// L31 · public
interface ComboRepository {
// L43 · public
interface BitmapSource {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ImageSource
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
```

</details>

## :design-system

### PcActionGroup.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcActionGroup.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcActionGroup.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L16 · public
fun PcActionGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
// L29 · private
private fun PcActionFlow(modifier: Modifier, content: @Composable () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcButton.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcButton.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcButton.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L39 · public
enum class PcButtonVariant { Primary, Secondary, Ghost, Danger, DangerFilled }
// L43 · public
fun PcButton(
// L120 · internal
internal fun PcBusyIndicator(color: Color) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcCheckbox.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcCheckbox.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcCheckbox.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L30 · public
fun PcCheckbox(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcDialog.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcDialog.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcDialog.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L42 · public
fun PcDialog(
// L94 · internal
internal expect fun PcPlatformDialog(onDismissRequest: () -> Unit, dismissOnClickOutside: Boolean, content: @Composable () -> Unit)
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcPopupAppearance
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcIconButton.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcIconButton.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcIconButton.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L24 · public
fun PcIconButton(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcOption.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcOption.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcOption.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L6 · public
data class PcOption<T>(val value: T, val label: String, val icon: ImageVector? = null)
```

### PcSegmentedControl.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSegmentedControl.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSegmentedControl.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L45 · public
fun <T> PcSegmentedControl(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcSelect.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSelect.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSelect.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L65 · public
fun <T> PcSelect(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.primitives.pcPopupAppearance
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcSlider.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSlider.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcSlider.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L30 · public
fun PcSlider(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcTextField.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcTextField.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcTextField.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L30 · public
fun PcTextField(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcTooltip.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcTooltip.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/components/PcTooltip.kt)

`com.combo.ui.common.designsystem.components` · `commonMain`

```kotlin
// L38 · public
fun PcTooltip(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
// L71 · internal
internal class PcAnchorPosition(private val gap: Int, private val above: Boolean = false) : PopupPositionProvider {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcPopupAppearance
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcIcons.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/icons/PcIcons.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/icons/PcIcons.kt)

`com.combo.ui.common.designsystem.icons` · `commonMain`

```kotlin
// L13 · public
object PcIcons {
// L211 · private
private fun icon(
// L233 · private
private fun PathBuilder.roundedBox(
// L252 · private
private fun PathBuilder.circle(x: Float, y: Float, radius: Float) {
```

### PcControl.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcControl.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcControl.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L40 · public
fun Modifier.pcControlSurface(
// L105 · public
fun PcControlLayout(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcDivider.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcDivider.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcDivider.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L15 · public
fun PcDivider(modifier: Modifier = Modifier, vertical: Boolean = false, color: Color = PcTheme.colors.border) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcIcon.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcIcon.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcIcon.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L16 · public
fun PcIcon(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcPopupAppearance.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcPopupAppearance.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcPopupAppearance.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L17 · public
fun Modifier.pcPopupAppearance(): Modifier {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcSurface.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcSurface.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcSurface.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L19 · public
fun PcSurface(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.LocalPcContentColor
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcText.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcText.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/primitives/PcText.kt)

`com.combo.ui.common.designsystem.primitives` · `commonMain`

```kotlin
// L14 · public
fun PcText(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.LocalPcTextStyle
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcTheme.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/theme/PcTheme.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/theme/PcTheme.kt)

`com.combo.ui.common.designsystem.theme` · `commonMain`

```kotlin
// L19 · private
private val LocalPcColors = staticCompositionLocalOf { PcColors.Dark }
// L20 · private
private val LocalPcTypography = staticCompositionLocalOf { PcTypography() }
// L21 · private
private val LocalPcSpacing = staticCompositionLocalOf { PcSpacing() }
// L22 · private
private val LocalPcSizes = staticCompositionLocalOf { PcSizes() }
// L23 · private
private val LocalPcShapes = staticCompositionLocalOf { PcShapes() }
// L24 · private
private val LocalPcStrokes = staticCompositionLocalOf { PcStrokes() }
// L25 · private
private val LocalPcOpacity = staticCompositionLocalOf { PcOpacity() }
// L26 · private
private val LocalPcMotion = staticCompositionLocalOf { PcMotion() }
// L27 · private
private val LocalPcDensity = staticCompositionLocalOf { PcDensity.Mouse }
// L28 · private
private val LocalPcFocus = staticCompositionLocalOf { PcFocus() }
// L30 · public
val LocalPcContentColor = staticCompositionLocalOf { PcColors.Dark.text }
// L31 · public
val LocalPcTextStyle = staticCompositionLocalOf<TextStyle> { PcTypography().body }
// L33 · public
object PcTheme {
// L50 · public
fun PcTheme(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.tokens.PcColors
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcFocus
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.common.designsystem.tokens.PcOpacity
import com.combo.ui.common.designsystem.tokens.PcShapes
import com.combo.ui.common.designsystem.tokens.PcSizes
import com.combo.ui.common.designsystem.tokens.PcSpacing
import com.combo.ui.common.designsystem.tokens.PcStrokes
import com.combo.ui.common.designsystem.tokens.PcTypography
```

</details>

### PcColors.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcColors.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcColors.kt)

`com.combo.ui.common.designsystem.tokens` · `commonMain`

```kotlin
// L7 · public
data class PcColors(
```

### PcFocus.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcFocus.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcFocus.kt)

`com.combo.ui.common.designsystem.tokens` · `commonMain`

```kotlin
// L9 · public
data class PcFocus(
```

### PcGeometry.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcGeometry.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcGeometry.kt)

`com.combo.ui.common.designsystem.tokens` · `commonMain`

```kotlin
// L9 · public
data class PcSpacing(
// L19 · public
data class PcSizes(
// L31 · public
data class PcShapes(
// L39 · public
data class PcStrokes(
// L46 · public
data class PcOpacity(val disabled: Float = 0.36f)
// L48 · public
enum class PcDensity(val minimumTarget: Dp) {
```

### PcMotion.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcMotion.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcMotion.kt)

`com.combo.ui.common.designsystem.tokens` · `commonMain`

```kotlin
// L6 · public
data class PcMotion(
```

### PcTypography.kt

[design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcTypography.kt](../../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/tokens/PcTypography.kt)

`com.combo.ui.common.designsystem.tokens` · `commonMain`

```kotlin
// L9 · public
class PcTypography(fontFamily: FontFamily = FontFamily.SansSerif) {
```

### PcPlatformDialog.desktop.kt

[design-system/src/desktopMain/kotlin/com/combo/ui/common/designsystem/components/PcPlatformDialog.desktop.kt](../../design-system/src/desktopMain/kotlin/com/combo/ui/common/designsystem/components/PcPlatformDialog.desktop.kt)

`com.combo.ui.common.designsystem.components` · `desktopMain`

```kotlin
// L12 · internal
internal actual fun PcPlatformDialog(onDismissRequest: () -> Unit, dismissOnClickOutside: Boolean, content: @Composable () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PcDesktopTheme.kt

[design-system/src/desktopMain/kotlin/com/combo/ui/common/PcDesktopTheme.kt](../../design-system/src/desktopMain/kotlin/com/combo/ui/common/PcDesktopTheme.kt)

`com.combo.ui.common` · `desktopMain`

```kotlin
// L17 · public
object PcDesktopFonts {
// L30 · public
fun PcDesktopTheme(density: PcDensity = PcDensity.Mouse, motion: PcMotion = PcMotion(), content: @Composable () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.tokens.PcDensity
import com.combo.ui.common.designsystem.tokens.PcMotion
import com.combo.ui.common.designsystem.tokens.PcTypography
```

</details>

### ComponentExamples.kt

[design-system/src/desktopMain/kotlin/com/combo/ui/gallery/ComponentExamples.kt](../../design-system/src/desktopMain/kotlin/com/combo/ui/gallery/ComponentExamples.kt)

`com.combo.ui.gallery` · `desktopMain`

```kotlin
// L17 · internal
internal fun ComponentExample(name: String) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.*
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### DesignSystemGallery.kt

[design-system/src/desktopMain/kotlin/com/combo/ui/gallery/DesignSystemGallery.kt](../../design-system/src/desktopMain/kotlin/com/combo/ui/gallery/DesignSystemGallery.kt)

`com.combo.ui.gallery` · `desktopMain`

```kotlin
// L30 · public
data class GalleryExample(val name: String, val content: @Composable () -> Unit)
// L32 · private
private val componentNames = listOf("Button", "Icon button", "Checkbox", "Text field", "Slider",
// L36 · public
fun DesignSystemGallery(extraExamples: List<GalleryExample> = emptyList()) {
// L61 · private
private fun OverviewGallery() {
// L148 · private
private fun galleryIcons(): List<Pair<String, ImageVector>> = listOf(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.PcDesktopFonts
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.*
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

## :ui-common

### BackDispatcher.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/BackDispatcher.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/BackDispatcher.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L18 · public
class BackDispatcher {
// L41 · public
val LocalBackDispatcher = staticCompositionLocalOf<BackDispatcher> {
// L53 · public
fun RegisterBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
```

### BackKeyHost.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/BackKeyHost.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/BackKeyHost.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L24 · public
const val BACK_HOST_TAG = "back_host"
// L38 · public
fun BackKeyHost(dispatcher: BackDispatcher, content: @Composable () -> Unit) {
```

### BackNavIcon.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/BackNavIcon.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/BackNavIcon.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L25 · public
fun BackNavIcon(onClick: () -> Unit) {
```

### CollectMessages.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/CollectMessages.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/CollectMessages.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L20 · public
fun CollectMessages(messages: Flow<String>, snackbarHostState: SnackbarHostState) {
```

### GalleryGrid.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/GalleryGrid.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/GalleryGrid.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L14 · public
fun galleryColumnCount(availableWidth: Int, referenceWidth: Int, gap: Int): Int {
// L20 · public
data class GalleryCellGeometry(val width: Dp, val previewHeight: Dp)
// L24 · public
fun <T> GalleryGrid(
```

### GalleryScaleState.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/GalleryScaleState.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/GalleryScaleState.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L12 · public
data class GalleryScaleUiState(
// L19 · public
class GalleryScaleState(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.data.normalizedGalleryPercent
import com.combo.domain.GalleryKind
import com.combo.domain.GallerySettingsRepository
```

</details>

### GallerySizeControl.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/GallerySizeControl.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/GallerySizeControl.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L16 · public
fun GallerySizeControl(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.components.*
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### KeyInputHost.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/KeyInputHost.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/KeyInputHost.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L27 · public
fun KeyInputHost(
```

### PhotoGrid.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/PhotoGrid.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/PhotoGrid.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L36 · public
fun PhotoGrid(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.data.PhotoEntity
```

</details>

### ThumbImage.kt

[ui-common/src/commonMain/kotlin/com/combo/ui/common/ThumbImage.kt](../../ui-common/src/commonMain/kotlin/com/combo/ui/common/ThumbImage.kt)

`com.combo.ui.common` · `commonMain`

```kotlin
// L36 · public
fun ThumbImage(photoId: String, modifier: Modifier = Modifier, placeholderColor: Color = PcTheme.colors.workspace) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.core.image.ImageCodec
import com.combo.domain.PhotoRepository
```

</details>

## :feature:builder

### BuilderContract.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderContract.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderContract.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L25 · public
const val EDITOR_CANVAS_TAG = "builder_editor_canvas"
// L28 · public
const val TOOL_TAG_DEL = "builder_tool_del"
// L29 · public
const val TOOL_TAG_REPLACE_PHOTO = "builder_tool_replace_photo"
// L30 · public
const val TOOL_TAG_ADD_ITEM = "builder_tool_add_item"
// L31 · public
const val TOOL_TAG_AUTOFIT = "builder_tool_autofit"
// L32 · public
const val TOOL_TAG_ASPECT = "builder_tool_aspect"
// L33 · public
const val TOOL_TAG_FIT_VIEW = "builder_tool_fit_view"
// L34 · public
const val TOOL_TAG_SAVE = "builder_tool_save"
// L35 · public
const val TOOL_TAG_NAME = "builder_combo_name"
// L36 · public
const val TOOL_TAG_BACKGROUND = "builder_tool_background"
// L37 · public
const val TOOL_TAG_MODE_FRAME = "builder_mode_frame"
// L38 · public
const val TOOL_TAG_MODE_PHOTO = "builder_mode_photo"
// L39 · public
const val TOOL_TAG_FIT = "builder_tool_fit"
// L40 · public
const val TOOL_TAG_FILL = "builder_tool_fill"
// L41 · public
const val TOOL_TAG_ZOOM_IN = "builder_zoom_in"
// L42 · public
const val TOOL_TAG_ZOOM_OUT = "builder_zoom_out"
// L43 · public
const val BUILDER_ZOOM_TAG = "builder_zoom_value"
// L44 · public
const val BUILDER_INSPECTOR_TAG = "builder_inspector"
// L47 · public
const val DIALOG_TAG_SAVE = "builder_exit_save"
// L48 · public
const val DIALOG_TAG_DISCARD = "builder_exit_discard"
// L49 · public
const val DIALOG_TAG_KEEP = "builder_exit_keep"
// L52 · public
const val DIALOG_TAG_PICK_CONFIRM = "builder_pick_confirm"
// L54 · public
fun addPhotoCellTag(photoId: String) = "builder_add_photo_$photoId"
// L58 · public
enum class GesturePhase { START, CHANGE, END, CANCEL }
// L60 · public
enum class EditMode { EDIT, BORDER }
// L62 · public
sealed interface SnapDragKind {
// L69 · public
const val MSG_NOTHING_TO_SAVE = "Add at least one photo"
// L73 · public
sealed interface BuilderEvent {
// L114 · public
data class BuilderUiState(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.render.Edge
import com.combo.core.render.ItemDraft
import com.combo.core.render.Viewport
import com.combo.core.render.snap.SnapDistances
import com.combo.core.render.snap.SnapGuide
import com.combo.data.PhotoEntity
import com.combo.feature.builder.draft.FitPreset
```

</details>

### BuilderControls.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderControls.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderControls.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L10 · internal
internal fun BuilderIconButton(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.components.PcIconButton
```

</details>

### BuilderInspector.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderInspector.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderInspector.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L37 · internal
internal fun BuilderInspector(
// L141 · private
private fun InspectorLabel(label: String) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.model.FramingMode
import com.combo.feature.builder.draft.FitPreset
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcCheckbox
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.components.PcSegmentedControl
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.theme.LocalPcContentColor
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### BuilderToolbar.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderToolbar.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderToolbar.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L26 · internal
internal fun BuilderCanvasToolbar(state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onAdd: () -> Unit) {
// L65 · private
private fun BuilderSettingsLayout(modifier: Modifier, content: @Composable () -> Unit) {
// L93 · internal
internal enum class BuilderPickMode { NONE, ADD, REPLACE }
// L96 · internal
internal fun BuilderPhotoPicker(mode: BuilderPickMode, state: BuilderUiState, onEvent: (BuilderEvent) -> Unit, onDismiss: () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.background.BackgroundPresets
import com.combo.core.render.AspectPresets
import com.combo.core.render.CanvasFormat
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcOption
import com.combo.ui.common.designsystem.components.PcSelect
import com.combo.ui.common.designsystem.icons.PcIcons
```

</details>

### BuilderTopBar.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderTopBar.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderTopBar.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L29 · internal
internal fun BuilderTopBar(state: BuilderUiState, wide: Boolean, onBack: () -> Unit, onEvent: (BuilderEvent) -> Unit) {
// L70 · private
private fun BuilderUiState.hasMarginsToTrim(): Boolean {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.toSnap
import com.combo.feature.builder.draft.trimmedToPhotos
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcTextField
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### BuilderViewControls.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderViewControls.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderViewControls.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L29 · internal
internal fun BuilderViewControls(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.render.Viewport
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### BuilderWorkspace.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderWorkspace.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderWorkspace.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L17 · internal
internal val BUILDER_WIDE_MIN = 760.dp
// L24 · internal
internal fun BuilderWorkspace(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.designsystem.primitives.PcSurface
```

</details>

### CanvasSizeDialog.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/CanvasSizeDialog.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/CanvasSizeDialog.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L28 · internal
internal fun CanvasSizeDialog(
// L90 · private
private fun quickResolutions(ratio: CanvasFormat): List<CanvasFormat> {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.render.CanvasFormat
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.components.PcTextField
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### ComboBuilderScreen.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ComboBuilderScreen.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ComboBuilderScreen.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L54 · public
fun ComboBuilderScreen(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.core.render.CanvasFormat
import com.combo.ui.common.CollectMessages
import com.combo.ui.common.RegisterBackHandler
```

</details>

### ComboBuilderViewModel.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ComboBuilderViewModel.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ComboBuilderViewModel.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L70 · public
class ComboBuilderViewModel(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.platform.IdGenerator
import com.combo.core.platform.WallClock
import com.combo.core.render.ItemDraft
import com.combo.core.render.Border
import com.combo.core.render.ItemFrame
import com.combo.core.render.ItemGestures
import com.combo.core.render.Viewport
import com.combo.core.render.TransformMath
import com.combo.core.render.buildItemFrame
import com.combo.core.render.minBorderSize
import com.combo.core.render.relayout
import com.combo.core.render.snap.FrameSnap
import com.combo.core.render.snap.FrameSnapState
import com.combo.core.render.snap.SnapStep
import com.combo.data.ComboEntity
import com.combo.data.PhotoEntity
import com.combo.data.srcSize
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.DraftEditor
import com.combo.feature.builder.draft.FitPreset
import com.combo.feature.builder.draft.frameContent
import com.combo.feature.builder.draft.toDraft
import com.combo.feature.builder.draft.toDrafts
import com.combo.feature.builder.draft.toEntities
import com.combo.feature.builder.draft.toSnap
import com.combo.feature.builder.draft.trimmedToPhotos
import com.combo.feature.builder.history.DraftHistory
```

</details>

### ComboDraftMapper.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/ComboDraftMapper.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/ComboDraftMapper.kt)

`com.combo.feature.builder.draft` · `commonMain`

```kotlin
// L24 · internal
internal fun ComboWithItems.toDrafts(): List<ItemDraft> =
// L50 · internal
internal fun List<ItemDraft>.toEntities(comboId: String): List<ComboItemEntity> =
// L68 · internal
internal fun ItemDraft.toSnap(): ItemSnap = ItemSnap(
// L79 · internal
internal fun ItemSnap.toDraft(): ItemDraft = ItemDraft(
// L96 · internal
internal fun ItemDraft.frameContent(image: ImageBitmap, photo: PhotoEntity) =
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.FrameContent
import com.combo.core.render.ItemDraft
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
```

</details>

### ComboSnap.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/ComboSnap.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/ComboSnap.kt)

`com.combo.feature.builder.draft` · `commonMain`

```kotlin
// L13 · internal
internal data class ItemSnap(
// L43 · internal
internal data class ComboSnap(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
```

</details>

### DraftEditor.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/DraftEditor.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/DraftEditor.kt)

`com.combo.feature.builder.draft` · `commonMain`

```kotlin
// L20 · internal
internal data class NewComboDrafts(
// L30 · internal
internal data class NewItems(
// L64 · internal
internal object DraftEditor {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.model.FramingMode
import com.combo.core.render.ItemDraft
import com.combo.core.render.LayoutPresets
import com.combo.core.render.effectiveFraming
import com.combo.data.PhotoEntity
import com.combo.data.srcSize
```

</details>

### FinalizeCanvas.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/FinalizeCanvas.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/FinalizeCanvas.kt)

`com.combo.feature.builder.draft` · `commonMain`

```kotlin
// L12 · internal
internal fun ComboSnap.trimmedToPhotos(photos: Map<String, PhotoEntity>): ComboSnap {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.Border
import com.combo.core.render.effectiveFraming
import com.combo.core.render.visiblePhotoBounds
import com.combo.data.PhotoEntity
```

</details>

### FitPreset.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/FitPreset.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/draft/FitPreset.kt)

`com.combo.feature.builder.draft` · `commonMain`

```kotlin
// L13 · public
enum class FitPreset { FIT, FILL }
```

### EditorCanvas.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorCanvas.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorCanvas.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L44 · internal
internal fun EditorViewport(
// L98 · private
private fun EditorCanvas(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.core.render.CanvasTransform
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.core.render.snap.SnapDistances
```

</details>

### EditorGestures.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorGestures.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorGestures.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L24 · internal
internal enum class GestureKind { NONE, DRAG_ITEM, PAN_CONTENT, RESIZE, PAN_VIEWPORT }
// L40 · internal
internal fun classifyGesture(
// L60 · internal
internal fun nearEdge(p: Offset, border: Rect, tolerance: Float): Edge? {
// L95 · internal
internal fun Modifier.editorGestures(
// L276 · private
private fun centroidOf(pointers: List<PointerInputChange>): Offset {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.CanvasTransform
import com.combo.core.render.Edge
import com.combo.core.render.snap.SnapDistances
```

</details>

### EditorWheel.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorWheel.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/EditorWheel.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L16 · private
private const val WHEEL_ZOOM_STEP = 1.1f
// L17 · private
private const val FINE_WHEEL_ZOOM_STEP = 1.02f
// L24 · private
private const val WHEEL_IDLE_END_MS = 250L
// L37 · internal
internal fun Modifier.editorWheelZoom(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.CanvasTransform
```

</details>

### ExitDialog.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ExitDialog.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ExitDialog.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L29 · internal
internal fun ExitDialog(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.primitives.PcText
```

</details>

### DraftHistory.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/history/DraftHistory.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/history/DraftHistory.kt)

`com.combo.feature.builder.history` · `commonMain`

```kotlin
// L27 · internal
internal class DraftHistory(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.util.UndoStack
import com.combo.feature.builder.draft.ComboSnap
```

</details>

### PhotoSize.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PhotoSize.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PhotoSize.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L7 · public
data class PhotoSize(
// L17 · internal
internal fun BuilderUiState.selectedPhotoSize(): PhotoSize? {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.effectiveFraming
```

</details>

### PhotoSizeInfo.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PhotoSizeInfo.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PhotoSizeInfo.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L12 · internal
internal fun PhotoSizeInfo(size: PhotoSize, modifier: Modifier = Modifier) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### PickPhotosDialog.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PickPhotosDialog.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/PickPhotosDialog.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L58 · internal
internal fun PickPhotosDialog(
// L110 · private
private fun PickPhotoCell(photo: PhotoEntity, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.data.PhotoEntity
import com.combo.ui.common.ThumbImage
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcDialog
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### SelectionOverlay.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/SelectionOverlay.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/SelectionOverlay.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L33 · internal
internal fun DrawScope.drawSelectionOverlay(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.CanvasTransform
```

</details>

### SnapGuideOverlay.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/SnapGuideOverlay.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/SnapGuideOverlay.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L12 · internal
internal fun DrawScope.drawSnapGuideOverlay(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.render.CanvasTransform
import com.combo.core.render.snap.SnapAxis
```

</details>

### ViewportController.kt

[feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ViewportController.kt](../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ViewportController.kt)

`com.combo.feature.builder` · `commonMain`

```kotlin
// L32 · internal
internal fun Viewport.zoomedAround(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.Const
import com.combo.core.render.Viewport
import com.combo.core.render.viewportZoomPan
```

</details>

## :feature:library

### LibraryScreen.kt

[feature/library/src/commonMain/kotlin/com/combo/feature/library/LibraryScreen.kt](../../feature/library/src/commonMain/kotlin/com/combo/feature/library/LibraryScreen.kt)

`com.combo.feature.library` · `commonMain`

```kotlin
// L46 · public
const val PHOTO_CELL_TAG_PREFIX = "library_photo_"
// L47 · public
const val LIB_SELECT_TAG = "library_action_select"
// L48 · public
const val LIB_CANCEL_TAG = "library_action_cancel"
// L49 · public
const val LIB_DELETE_TAG = "library_action_delete"
// L50 · public
const val LIB_CREATE_COMBO_TAG = "library_action_create_combo"
// L51 · public
const val LIB_IMPORT_TAG = "library_action_import"
// L54 · public
fun LibraryScreen(
// L98 · internal
internal fun LibraryScreenContent(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.GallerySizeControl
import com.combo.ui.common.GalleryScaleUiState
import com.combo.data.PhotoEntity
import com.combo.ui.common.CollectMessages
import com.combo.ui.common.PhotoGrid
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.ThumbImage
import com.combo.ui.common.designsystem.components.PcActionGroup
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### LibraryViewModel.kt

[feature/library/src/commonMain/kotlin/com/combo/feature/library/LibraryViewModel.kt](../../feature/library/src/commonMain/kotlin/com/combo/feature/library/LibraryViewModel.kt)

`com.combo.feature.library` · `commonMain`

```kotlin
// L24 · public
class LibraryViewModel(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.domain.GallerySettingsRepository
import com.combo.domain.GalleryKind
import com.combo.ui.common.GalleryScaleState
import com.combo.Const
import com.combo.core.image.ImagePicker
import com.combo.data.PhotoEntity
import com.combo.domain.DeleteResult
import com.combo.domain.ImportResult
import com.combo.domain.PhotoRepository
```

</details>

## :feature:combo-list

### ComboListScreen.kt

[feature/combo-list/src/commonMain/kotlin/com/combo/feature/combo_list/ComboListScreen.kt](../../feature/combo-list/src/commonMain/kotlin/com/combo/feature/combo_list/ComboListScreen.kt)

`com.combo.feature.combo_list` · `commonMain`

```kotlin
// L37 · public
const val NEW_COMBO_TAG = "combo_list_new_combo"
// L40 · public
fun ComboListScreen(
// L58 · internal
internal fun ComboListContent(
// L139 · private
private fun ComboDetails(
// L170 · private
private fun StaticPreview(vm: ComboListViewModel, combo: ComboWithItems, modifier: Modifier) {
// L200 · private
private fun rememberFrames(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.GalleryGrid
import com.combo.ui.common.GallerySizeControl
import com.combo.ui.common.GalleryScaleUiState
import com.combo.core.render.CanvasTransform
import com.combo.core.render.ItemFrame
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.data.ComboWithItems
import com.combo.data.backgroundFrames
import com.combo.ui.common.designsystem.components.PcButton
import com.combo.ui.common.designsystem.components.PcButtonVariant
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
```

</details>

### ComboListViewModel.kt

[feature/combo-list/src/commonMain/kotlin/com/combo/feature/combo_list/ComboListViewModel.kt](../../feature/combo-list/src/commonMain/kotlin/com/combo/feature/combo_list/ComboListViewModel.kt)

`com.combo.feature.combo_list` · `commonMain`

```kotlin
// L31 · private
private const val PREVIEW_PX = 800
// L33 · public
class ComboListViewModel(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.domain.GallerySettingsRepository
import com.combo.domain.GalleryKind
import com.combo.ui.common.GalleryScaleState
import com.combo.core.render.ItemFrame
import com.combo.core.render.buildItemFrame
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.data.border
import com.combo.data.frameContent
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
```

</details>

## :feature:viewer

### PhotoViewerScreen.kt

[feature/viewer/src/commonMain/kotlin/com/combo/feature/viewer/PhotoViewerScreen.kt](../../feature/viewer/src/commonMain/kotlin/com/combo/feature/viewer/PhotoViewerScreen.kt)

`com.combo.feature.viewer` · `commonMain`

```kotlin
// L43 · public
fun PhotoViewerScreen(
// L54 · internal
internal fun PhotoViewerContent(
// L139 · private
private fun ViewerChrome(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.primitives.PcDivider
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.theme.PcTheme
import com.combo.ui.common.KeyInputHost
```

</details>

### PhotoViewerViewModel.kt

[feature/viewer/src/commonMain/kotlin/com/combo/feature/viewer/PhotoViewerViewModel.kt](../../feature/viewer/src/commonMain/kotlin/com/combo/feature/viewer/PhotoViewerViewModel.kt)

`com.combo.feature.viewer` · `commonMain`

```kotlin
// L21 · private
private const val VIEWER_PX = 2048
// L23 · public
class PhotoViewerViewModel(
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.data.PhotoEntity
import com.combo.domain.BitmapSource
import com.combo.domain.PhotoAssets
import com.combo.domain.PhotoRepository
```

</details>

## :test-support

### ImageFixtures.kt

[test-support/src/desktopMain/kotlin/com/combo/ImageFixtures.kt](../../test-support/src/desktopMain/kotlin/com/combo/ImageFixtures.kt)

`com.combo` · `desktopMain`

```kotlin
// L19 · public
object ImageFixtures {
```

### TestDoubles.kt

[test-support/src/desktopMain/kotlin/com/combo/TestDoubles.kt](../../test-support/src/desktopMain/kotlin/com/combo/TestDoubles.kt)

`com.combo` · `desktopMain`

```kotlin
// L20 · public
class FakePhotoDao : PhotoDao {
// L48 · public
class FakeComboDao : ComboDao {
// L67 · public
class FakeLogger : Logger {
// L76 · public
class CountingCodec : ImageCodec {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.platform.Logger
import com.combo.data.ComboDao
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoDao
import com.combo.data.PhotoEntity
```

</details>

### FakeGallerySettingsRepository.kt

[test-support/src/desktopMain/kotlin/com/combo/ui/FakeGallerySettingsRepository.kt](../../test-support/src/desktopMain/kotlin/com/combo/ui/FakeGallerySettingsRepository.kt)

`com.combo.ui` · `desktopMain`

```kotlin
// L7 · public
class FakeGallerySettingsRepository : GallerySettingsRepository {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.data.GallerySettings
import com.combo.domain.GalleryKind
import com.combo.domain.GallerySettingsRepository
```

</details>

### TestBackHost.kt

[test-support/src/desktopMain/kotlin/com/combo/ui/TestBackHost.kt](../../test-support/src/desktopMain/kotlin/com/combo/ui/TestBackHost.kt)

`com.combo.ui` · `desktopMain`

```kotlin
// L30 · public
fun uiTestKoin(photoRepo: PhotoRepository): KoinApplication = koinApplication {
// L46 · public
fun TestScreen(
// L67 · public
fun ComposeUiTest.pressEsc() {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.core.image.ImageCodec
import com.combo.domain.PhotoRepository
import com.combo.ui.common.BACK_HOST_TAG
import com.combo.ui.common.BackDispatcher
import com.combo.ui.common.BackKeyHost
import com.combo.ui.common.LocalBackDispatcher
```

</details>

### TestDoubles.kt

[test-support/src/desktopMain/kotlin/com/combo/ui/TestDoubles.kt](../../test-support/src/desktopMain/kotlin/com/combo/ui/TestDoubles.kt)

`com.combo.ui` · `desktopMain`

```kotlin
// L25 · public
fun photoEntity(id: String, originalName: String = "$id.jpg") = PhotoEntity(
// L38 · public
fun comboEntity(
// L53 · public
fun comboItemEntity(
// L75 · public
class FakePhotoRepository(initial: List<PhotoEntity> = emptyList()) : PhotoRepository {
// L98 · public
class FakeComboRepository(initial: List<ComboWithItems> = emptyList()) : ComboRepository {
// L134 · public
class StubBitmapSource : BitmapSource {
// L140 · public
class FakeImagePicker : ImagePicker {
// L145 · public
class StubImageCodec : ImageCodec {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.ImageFixtures
import com.combo.core.background.BackgroundSpec
import com.combo.core.image.ExifOrientation
import com.combo.core.image.ImageCodec
import com.combo.core.image.ImageFormat
import com.combo.core.image.ImagePicker
import com.combo.core.image.ImageSource
import com.combo.data.ComboEntity
import com.combo.data.ComboItemEntity
import com.combo.data.ComboWithItems
import com.combo.data.PhotoEntity
import com.combo.domain.BitmapSource
import com.combo.domain.ComboRepository
import com.combo.domain.DeleteResult
import com.combo.domain.ImportResult
import com.combo.domain.PhotoRepository
```

</details>

### UiTestBase.kt

[test-support/src/desktopMain/kotlin/com/combo/ui/UiTestBase.kt](../../test-support/src/desktopMain/kotlin/com/combo/ui/UiTestBase.kt)

`com.combo.ui` · `desktopMain`

```kotlin
// L26 · public
abstract class UiTestBase {
```

<details><summary>Внутренние импорты</summary>

```kotlin
import com.combo.domain.PhotoRepository
import com.combo.ui.common.BackDispatcher
```

</details>

## Тесты: полный список файлов

Наличие теста не означает, что он был запущен при создании атласа.

- [AppModulesTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/AppModulesTest.kt) — `composeApp`
- [ArchitectureRulesTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/ArchitectureRulesTest.kt) — `composeApp`
- [ItemFrameParityTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/ItemFrameParityTest.kt) — `composeApp`
- [SourceHygieneTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/SourceHygieneTest.kt) — `composeApp`
- [AppNavViewModelTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/ui/AppNavViewModelTest.kt) — `composeApp`
- [LibraryOpensTappedPhotoTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/ui/LibraryOpensTappedPhotoTest.kt) — `composeApp`
- [SharedGalleryTest.kt](../../composeApp/src/desktopTest/kotlin/com/combo/ui/SharedGalleryTest.kt) — `composeApp`
- [AverageColorTest.kt](../../core/src/desktopTest/kotlin/com/combo/AverageColorTest.kt) — `core`
- [BackgroundStrategyTest.kt](../../core/src/desktopTest/kotlin/com/combo/BackgroundStrategyTest.kt) — `core`
- [CanvasTransformTest.kt](../../core/src/desktopTest/kotlin/com/combo/CanvasTransformTest.kt) — `core`
- [DesktopPngOutputTest.kt](../../core/src/desktopTest/kotlin/com/combo/DesktopPngOutputTest.kt) — `core`
- [DrawComboBackgroundTest.kt](../../core/src/desktopTest/kotlin/com/combo/DrawComboBackgroundTest.kt) — `core`
- [DrawComboClipTest.kt](../../core/src/desktopTest/kotlin/com/combo/DrawComboClipTest.kt) — `core`
- [ExifOrientationTest.kt](../../core/src/desktopTest/kotlin/com/combo/ExifOrientationTest.kt) — `core`
- [FrameSnapTest.kt](../../core/src/desktopTest/kotlin/com/combo/FrameSnapTest.kt) — `core`
- [ItemGesturesTest.kt](../../core/src/desktopTest/kotlin/com/combo/ItemGesturesTest.kt) — `core`
- [SkiaImageCodecTest.kt](../../core/src/desktopTest/kotlin/com/combo/SkiaImageCodecTest.kt) — `core`
- [TransformMathTest.kt](../../core/src/desktopTest/kotlin/com/combo/TransformMathTest.kt) — `core`
- [UndoStackTest.kt](../../core/src/desktopTest/kotlin/com/combo/UndoStackTest.kt) — `core`
- [AppDbTest.kt](../../data/src/desktopTest/kotlin/com/combo/AppDbTest.kt) — `data`
- [BackgroundConvertersTest.kt](../../data/src/desktopTest/kotlin/com/combo/BackgroundConvertersTest.kt) — `data`
- [EntityExtTest.kt](../../data/src/desktopTest/kotlin/com/combo/EntityExtTest.kt) — `data`
- [GallerySettingsStorageTest.kt](../../data/src/desktopTest/kotlin/com/combo/GallerySettingsStorageTest.kt) — `data`
- [MigrationTest.kt](../../data/src/desktopTest/kotlin/com/combo/MigrationTest.kt) — `data`
- [ComboPngExporterTest.kt](../../domain/src/desktopTest/kotlin/com/combo/ComboPngExporterTest.kt) — `domain`
- [ComboRepositoryTest.kt](../../domain/src/desktopTest/kotlin/com/combo/ComboRepositoryTest.kt) — `domain`
- [GallerySettingsRepositoryTest.kt](../../domain/src/desktopTest/kotlin/com/combo/GallerySettingsRepositoryTest.kt) — `domain`
- [LruBitmapSourceTest.kt](../../domain/src/desktopTest/kotlin/com/combo/LruBitmapSourceTest.kt) — `domain`
- [PhotoImporterTest.kt](../../domain/src/desktopTest/kotlin/com/combo/PhotoImporterTest.kt) — `domain`
- [PhotoRepositoryTest.kt](../../domain/src/desktopTest/kotlin/com/combo/PhotoRepositoryTest.kt) — `domain`
- [RealPhotoImportTest.kt](../../domain/src/desktopTest/kotlin/com/combo/RealPhotoImportTest.kt) — `domain`
- [ComponentGalleryTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/ComponentGalleryTest.kt) — `design-system`
- [PcActionGroupBoundsTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcActionGroupBoundsTest.kt) — `design-system`
- [PcButtonTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcButtonTest.kt) — `design-system`
- [PcCheckboxTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcCheckboxTest.kt) — `design-system`
- [PcDialogTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcDialogTest.kt) — `design-system`
- [PcIconButtonTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcIconButtonTest.kt) — `design-system`
- [PcSegmentedControlTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcSegmentedControlTest.kt) — `design-system`
- [PcSegmentedFocusOutlineTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcSegmentedFocusOutlineTest.kt) — `design-system`
- [PcSegmentedTrackTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcSegmentedTrackTest.kt) — `design-system`
- [PcSelectTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcSelectTest.kt) — `design-system`
- [PcSliderTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcSliderTest.kt) — `design-system`
- [PcTextFieldTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcTextFieldTest.kt) — `design-system`
- [PcTooltipTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/components/PcTooltipTest.kt) — `design-system`
- [PcIconsTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/icons/PcIconsTest.kt) — `design-system`
- [PcFocusRenderingTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/PcFocusRenderingTest.kt) — `design-system`
- [PcPrimitivesTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/PcPrimitivesTest.kt) — `design-system`
- [PcThemeTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/theme/PcThemeTest.kt) — `design-system`
- [PcTokensTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/common/designsystem/tokens/PcTokensTest.kt) — `design-system`
- [FocusedGalleryTest.kt](../../design-system/src/desktopTest/kotlin/com/combo/ui/gallery/FocusedGalleryTest.kt) — `design-system`
- [BackDispatcherTest.kt](../../ui-common/src/desktopTest/kotlin/com/combo/ui/common/BackDispatcherTest.kt) — `ui-common`
- [BackKeyHostTest.kt](../../ui-common/src/desktopTest/kotlin/com/combo/ui/common/BackKeyHostTest.kt) — `ui-common`
- [GalleryGridTest.kt](../../ui-common/src/desktopTest/kotlin/com/combo/ui/common/GalleryGridTest.kt) — `ui-common`
- [GalleryScaleStateTest.kt](../../ui-common/src/desktopTest/kotlin/com/combo/ui/common/GalleryScaleStateTest.kt) — `ui-common`
- [AspectRelayoutTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/AspectRelayoutTest.kt) — `feature/builder`
- [AutoFitTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/AutoFitTest.kt) — `feature/builder`
- [BuilderAdaptiveLayoutTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/BuilderAdaptiveLayoutTest.kt) — `feature/builder`
- [BuilderExitDialogTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/BuilderExitDialogTest.kt) — `feature/builder`
- [BuilderInspectorCompositionTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/BuilderInspectorCompositionTest.kt) — `feature/builder`
- [ComboBuilderBackgroundTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ComboBuilderBackgroundTest.kt) — `feature/builder`
- [ComboBuilderFinalizationTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ComboBuilderFinalizationTest.kt) — `feature/builder`
- [ComboBuilderGesturesTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ComboBuilderGesturesTest.kt) — `feature/builder`
- [ComboBuilderToolbarTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ComboBuilderToolbarTest.kt) — `feature/builder`
- [ComboBuilderViewModelTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ComboBuilderViewModelTest.kt) — `feature/builder`
- [DraftEditorTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/DraftEditorTest.kt) — `feature/builder`
- [DraftHistoryTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/DraftHistoryTest.kt) — `feature/builder`
- [DraftMapperTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/DraftMapperTest.kt) — `feature/builder`
- [EditorWheelTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/EditorWheelTest.kt) — `feature/builder`
- [GestureClassifierTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/GestureClassifierTest.kt) — `feature/builder`
- [NearEdgeTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/NearEdgeTest.kt) — `feature/builder`
- [ViewportControllerTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/feature/builder/ViewportControllerTest.kt) — `feature/builder`
- [ViewportTest.kt](../../feature/builder/src/desktopTest/kotlin/com/combo/ViewportTest.kt) — `feature/builder`
- [LibraryDesignSystemTest.kt](../../feature/library/src/desktopTest/kotlin/com/combo/ui/LibraryDesignSystemTest.kt) — `feature/library`
- [LibraryImportAndMessageTest.kt](../../feature/library/src/desktopTest/kotlin/com/combo/ui/LibraryImportAndMessageTest.kt) — `feature/library`
- [LibraryMultiselectTest.kt](../../feature/library/src/desktopTest/kotlin/com/combo/ui/LibraryMultiselectTest.kt) — `feature/library`
- [ComboListDesignSystemTest.kt](../../feature/combo-list/src/desktopTest/kotlin/com/combo/ui/ComboListDesignSystemTest.kt) — `feature/combo-list`
- [ComboListOpensBuilderTest.kt](../../feature/combo-list/src/desktopTest/kotlin/com/combo/ui/ComboListOpensBuilderTest.kt) — `feature/combo-list`
- [PhotoViewerDesignSystemTest.kt](../../feature/viewer/src/desktopTest/kotlin/com/combo/feature/viewer/PhotoViewerDesignSystemTest.kt) — `feature/viewer`
