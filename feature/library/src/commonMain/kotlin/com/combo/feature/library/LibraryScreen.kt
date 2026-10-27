package com.combo.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.combo.ui.common.GallerySizeControl
import com.combo.ui.common.GalleryScaleUiState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** testTag ячейки сетки: `PHOTO_CELL_TAG_PREFIX + photo.id`. */
const val PHOTO_CELL_TAG_PREFIX = "library_photo_"
const val LIB_SELECT_TAG = "library_action_select"
const val LIB_CANCEL_TAG = "library_action_cancel"
const val LIB_DELETE_TAG = "library_action_delete"
const val LIB_CREATE_COMBO_TAG = "library_action_create_combo"
const val LIB_IMPORT_TAG = "library_action_import"

@Composable
fun LibraryScreen(
    onOpenViewer: (String) -> Unit,
    onCreateCombo: (List<String>) -> Unit,
    vm: LibraryViewModel = koinViewModel(),
) {
    val galleryScale by vm.galleryScale.state.collectAsState()
    val photos by vm.photos.collectAsState()
    val selected by vm.selected.collectAsState()
    val selectionMode by vm.selectionMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectMessages(vm.messages, snackbar)
    RegisterBackHandler(enabled = selectionMode) { vm.exitSelectionMode() }

    val message = snackbar.currentSnackbarData
    // Очередь сообщений остаётся в SnackbarHostState; поверхность и время показа — свои.
    LaunchedEffect(message) {
        if (message != null && message.visuals.duration != SnackbarDuration.Indefinite) {
            delay(if (message.visuals.duration == SnackbarDuration.Long) 10_000L else 4_000L)
            message.dismiss()
        }
    }
    LibraryScreenContent(
        galleryScale = galleryScale,
        onScaleChange = vm.galleryScale::change,
        onScaleFinished = vm.galleryScale::commit,
        onScaleReset = vm.galleryScale::reset,
        photos = photos,
        selected = selected,
        selectionMode = selectionMode,
        onOpenViewer = onOpenViewer,
        onEnterSelection = vm::enterSelectionMode,
        onExitSelection = vm::exitSelectionMode,
        onToggle = vm::toggle,
        onDelete = vm::deleteSelected,
        onCreateCombo = { scope.launch { onCreateCombo(vm.photosForNewCombo()) } },
        onImport = vm::import,
        message = message?.visuals?.message,
        onDismissMessage = { message?.dismiss() },
    )
}

/** Чистая композиция экрана: фикстуры могут подменять только загрузку миниатюр. */
@Composable
internal fun LibraryScreenContent(
    photos: List<PhotoEntity>,
    selected: List<String>,
    selectionMode: Boolean,
    onOpenViewer: (String) -> Unit,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: () -> Unit,
    onCreateCombo: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    galleryScale: GalleryScaleUiState = GalleryScaleUiState(loaded = true),
    onScaleChange: (Float) -> Unit = {},
    onScaleFinished: () -> Unit = {},
    onScaleReset: () -> Unit = {},
    message: String? = null,
    onDismissMessage: () -> Unit = {},
    thumbnail: @Composable (PhotoEntity, Modifier) -> Unit = { photo, imageModifier ->
        ThumbImage(photo.id, imageModifier, placeholderColor = PcTheme.colors.workspace)
    },
) {
    PcSurface(modifier.fillMaxSize().testTag("library_screen"), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize()) {
            PcSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(PcTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                        if (selectionMode) PcIconButton(
                            PcIcons.Close, "Cancel selection", onExitSelection,
                            Modifier.testTag(LIB_CANCEL_TAG),
                        )
                        PcText(if (selectionMode) "Selected: ${selected.size}" else "Library", style = PcTheme.typography.title)
                    }
                    PcActionGroup(Modifier.fillMaxWidth()) {
                        if (selectionMode) {
                            if (selected.isNotEmpty()) {
                                PcIconButton(PcIcons.Delete, "Delete selected", onDelete, Modifier.testTag(LIB_DELETE_TAG))
                                PcButton("Create combo", onCreateCombo, Modifier.testTag(LIB_CREATE_COMBO_TAG), variant = PcButtonVariant.Primary)
                            }
                        } else {
                            PcButton("Select", onEnterSelection, Modifier.testTag(LIB_SELECT_TAG))
                        }
                        PcButton("Import", onImport, Modifier.testTag(LIB_IMPORT_TAG), icon = PcIcons.Add)
                    }
                    GallerySizeControl(galleryScale, onScaleChange, onScaleFinished, onScaleReset)
                }
            }
            PcDivider()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PhotoGrid(
                    photos = photos,
                    onCellClick = { if (selectionMode) onToggle(it.id) else onOpenViewer(it.id) },
                    cellTag = { PHOTO_CELL_TAG_PREFIX + it.id },
                    modifier = Modifier.fillMaxSize(),
                    referenceItemWidth = (240f * galleryScale.percent / 100f).dp,
                    verticalGap = 2.dp,
                    horizontalGap = 2.dp,
                    isSelected = { it.id in selected },
                    onCellLongClick = { onEnterSelection(); onToggle(it.id) },
                    thumbnail = thumbnail,
                )
                if (photos.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(PcTheme.spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        PcText("No photos yet", style = PcTheme.typography.title)
                        PcText("Import photos to start your library", color = PcTheme.colors.textSecondary)
                    }
                }
                if (message != null) PcSurface(
                    Modifier.align(Alignment.BottomCenter).padding(PcTheme.spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
                    shape = PcTheme.shapes.small, borderColor = PcTheme.colors.border, borderWidth = PcTheme.strokes.border,
                ) {
                    Column(Modifier.padding(PcTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                        PcText(message)
                        PcButton("Dismiss", onDismissMessage, variant = PcButtonVariant.Ghost)
                    }
                }
            }
        }
    }
}
