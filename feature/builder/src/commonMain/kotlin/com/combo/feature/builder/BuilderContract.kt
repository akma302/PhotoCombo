package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.render.Edge
import com.combo.core.render.Viewport
import com.combo.core.render.snap.SnapDistances
import com.combo.core.render.snap.SnapGuide
import com.combo.data.PhotoEntity
import com.combo.feature.builder.draft.ComboSnap
import com.combo.feature.builder.draft.FitPreset
import com.combo.feature.builder.draft.ItemSnap
import com.combo.feature.builder.draft.cropBounds

/**
 * Контракт редактора: testTag'и для UI-тестов, фазы жеста, режимы, события и состояние.
 *
 * Пакет, имена и значения строк-тегов зафиксированы: на них ссылаются
 * `ComboBuilderGesturesTest`, `BuilderExitDialogTest` и `ComboBuilderToolbarTest`.
 */

// ---------- testTag'ы: контракт с UI-тестами ----------

/** testTag холста редактора — UI-тесты жестов берут отсюда границы и шлют touch-события. */
const val EDITOR_CANVAS_TAG = "builder_editor_canvas"

/** testTag'ы кнопок тулбара: UI-тесты проверяют их enabled-состояние и окно выбора фото. */
internal const val TOOL_TAG_DEL = "builder_tool_del"
internal const val TOOL_TAG_REPLACE_PHOTO = "builder_tool_replace_photo"
internal const val TOOL_TAG_ADD_ITEM = "builder_tool_add_item"
internal const val TOOL_TAG_AUTOFIT = "builder_tool_autofit"
internal const val TOOL_TAG_ASPECT = "builder_tool_aspect"
internal const val TOOL_TAG_FIT_VIEW = "builder_tool_fit_view"
internal const val TOOL_TAG_SAVE = "builder_tool_save"
internal const val TOOL_TAG_NAME = "builder_combo_name"
internal const val TOOL_TAG_BACKGROUND = "builder_tool_background"
internal const val TOOL_TAG_MODE_FRAME = "builder_mode_frame"
internal const val TOOL_TAG_MODE_PHOTO = "builder_mode_photo"
internal const val TOOL_TAG_FIT = "builder_tool_fit"
internal const val TOOL_TAG_FILL = "builder_tool_fill"
internal const val TOOL_TAG_ZOOM_IN = "builder_zoom_in"
internal const val TOOL_TAG_ZOOM_OUT = "builder_zoom_out"
internal const val BUILDER_ZOOM_TAG = "builder_zoom_value"
internal const val BUILDER_INSPECTOR_TAG = "builder_inspector"

/** testTag'ы кнопок диалога выхода с несохранёнными изменениями. */
internal const val DIALOG_TAG_SAVE = "builder_exit_save"
internal const val DIALOG_TAG_DISCARD = "builder_exit_discard"
internal const val DIALOG_TAG_KEEP = "builder_exit_keep"

/** testTag кнопки подтверждения окна выбора фото для «+Frame». */
internal const val DIALOG_TAG_PICK_CONFIRM = "builder_pick_confirm"

internal fun addPhotoCellTag(photoId: String) = "builder_add_photo_$photoId"

// ---------- режимы и фазы ----------

internal enum class GesturePhase { START, CHANGE, END, CANCEL }

internal enum class EditMode { EDIT, BORDER }

internal sealed interface SnapDragKind {
    object MoveFrame : SnapDragKind
    object PanPhoto : SnapDragKind
    data class Resize(val edge: Edge, val oneSided: Boolean) : SnapDragKind
}

/** Сообщение при попытке сохранить комбо без рамок (рамка создаётся выбором фото). */
internal const val MSG_NOTHING_TO_SAVE = "Add at least one photo"

// ---------- события ----------

internal sealed interface BuilderEvent {
    data class ItemTapped(val id: String) : BuilderEvent
    data class ItemPressed(val id: String, val bringToFront: Boolean) : BuilderEvent
    data class PointerEditFinished(val cancelled: Boolean) : BuilderEvent
    object CanvasTapped : BuilderEvent
    object ToggleMode : BuilderEvent
    data class ItemDrag(val id: String, val delta: Offset, val phase: GesturePhase) : BuilderEvent
    data class SnapDrag(
        val id: String,
        val kind: SnapDragKind,
        val delta: Offset,
        val distances: SnapDistances,
        val bypass: Boolean,
    ) : BuilderEvent
    object BypassSnap : BuilderEvent
    object ClearSnap : BuilderEvent
    data class ItemPinch(val id: String, val factor: Float, val phase: GesturePhase) : BuilderEvent
    data class ContentPan(val id: String, val delta: Offset, val phase: GesturePhase) : BuilderEvent
    data class ContentZoom(val id: String, val factor: Float, val phase: GesturePhase) : BuilderEvent
    data class BorderResize(val id: String, val edge: Edge, val delta: Offset, val phase: GesturePhase) : BuilderEvent
    /** Добавить по рамке на каждое фото из списка (в пределах лимита) и выделить последнюю. */
    data class AddItems(val photoIds: List<String>) : BuilderEvent
    /** Подставить в рамку другое фото: позиция и размер рамки сохраняются. */
    data class ReplacePhoto(val id: String, val photoId: String) : BuilderEvent
    data class ApplyPreset(val id: String, val preset: FitPreset) : BuilderEvent
    data class SetFramingMode(val id: String, val mode: FramingMode) : BuilderEvent
    data class DeleteItem(val id: String) : BuilderEvent
    data class SetAspect(val w: Int, val h: Int) : BuilderEvent
    data class SetBackground(val spec: BackgroundSpec) : BuilderEvent
    data class SetName(val name: String) : BuilderEvent
    data class ViewportPan(val delta: Offset) : BuilderEvent
    data class ViewportZoom(val factor: Float, val focus: Offset) : BuilderEvent
    data class SetScreenSize(val w: Float, val h: Float) : BuilderEvent
    object Undo : BuilderEvent
    object Redo : BuilderEvent
    object ResetViewport : BuilderEvent
    object Save : BuilderEvent
}

// ---------- состояние ----------

internal data class BuilderUiState(
    val comboId: String? = null,
    val name: String = "",
    val canvasW: Float = 1600f,
    val canvasH: Float = 900f,
    val background: BackgroundSpec = BackgroundSpec.Auto(),
    val items: List<ItemSnap> = emptyList(),
    val selectedItemId: String? = null,
    val mode: EditMode = EditMode.EDIT,
    val viewport: Viewport = Viewport(),
    val screenW: Float = 0f,
    val screenH: Float = 0f,
    val isDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val photos: Map<String, PhotoEntity> = emptyMap(),
    val libraryPhotos: List<PhotoEntity> = emptyList(),
    val snapGuides: List<SnapGuide> = emptyList(),
) {
    internal val canvasSize: Size get() = Size(canvasW, canvasH)
    internal val viewportSize: Size get() = Size(screenW.coerceAtLeast(1f), screenH.coerceAtLeast(1f))
    internal val canSave: Boolean
        get() {
            if (!loaded || saving) return false
            if (isDirty) return true
            val bounds = canvasSnapshot().cropBounds(photos)
            return bounds.width != canvasW || bounds.height != canvasH
        }

    internal fun canvasSnapshot(): ComboSnap = ComboSnap(canvasW, canvasH, background, items)
}
