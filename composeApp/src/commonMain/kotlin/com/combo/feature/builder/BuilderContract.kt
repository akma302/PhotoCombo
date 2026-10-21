package com.combo.feature.builder

import androidx.compose.ui.geometry.Offset
import com.combo.core.background.BackgroundSpec
import com.combo.core.model.FramingMode
import com.combo.core.render.Edge
import com.combo.core.render.ItemDraft
import com.combo.core.render.Viewport
import com.combo.data.PhotoEntity
import com.combo.feature.builder.draft.FitPreset

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
const val TOOL_TAG_DEL = "builder_tool_del"
const val TOOL_TAG_REPLACE_PHOTO = "builder_tool_replace_photo"
const val TOOL_TAG_ADD_ITEM = "builder_tool_add_item"
const val TOOL_TAG_AUTOFIT = "builder_tool_autofit"
const val TOOL_TAG_ASPECT = "builder_tool_aspect"
const val TOOL_TAG_FIT_VIEW = "builder_tool_fit_view"
const val TOOL_TAG_SAVE = "builder_tool_save"

/** testTag'ы кнопок диалога выхода с несохранёнными изменениями. */
const val DIALOG_TAG_SAVE = "builder_exit_save"
const val DIALOG_TAG_DISCARD = "builder_exit_discard"
const val DIALOG_TAG_KEEP = "builder_exit_keep"

/** testTag кнопки подтверждения окна выбора фото для «+Frame». */
const val DIALOG_TAG_PICK_CONFIRM = "builder_pick_confirm"

fun addPhotoCellTag(photoId: String) = "builder_add_photo_$photoId"

// ---------- режимы и фазы ----------

enum class GesturePhase { START, CHANGE, END, CANCEL }

enum class EditMode { EDIT, BORDER }

/** Сообщение при попытке сохранить комбо без рамок (рамка создаётся выбором фото). */
const val MSG_NOTHING_TO_SAVE = "Add at least one photo"

// ---------- события ----------

sealed interface BuilderEvent {
    data class ItemTapped(val id: String) : BuilderEvent
    object CanvasTapped : BuilderEvent
    object ToggleMode : BuilderEvent
    data class ItemDrag(val id: String, val delta: Offset, val phase: GesturePhase) : BuilderEvent
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
    data class ReorderItem(val id: String, val forward: Boolean) : BuilderEvent
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

data class BuilderUiState(
    val revision: Long = 0,
    val comboId: String? = null,
    val name: String = "",
    val canvasW: Float = 1600f,
    val canvasH: Float = 900f,
    val background: BackgroundSpec = BackgroundSpec.Auto(),
    val items: List<ItemDraft> = emptyList(),
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
)
