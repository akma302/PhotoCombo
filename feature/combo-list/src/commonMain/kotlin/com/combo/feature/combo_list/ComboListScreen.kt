package com.combo.feature.combo_list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.combo.ui.common.GalleryGrid
import com.combo.ui.common.GallerySizeControl
import com.combo.ui.common.GalleryScaleUiState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.core.render.CanvasTransform
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
import org.koin.compose.viewmodel.koinViewModel

/** testTag кнопки «New combo» — единственный вход в builder из секции Combos. */
const val NEW_COMBO_TAG = "combo_list_new_combo"

@Composable
fun ComboListScreen(
    onOpenBuilder: (String) -> Unit,
    onCreateCombo: () -> Unit,
    vm: ComboListViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val galleryScale by vm.galleryScale.state.collectAsState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(vm, gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
            .collect(vm::setVisibleCombos)
    }
    DisposableEffect(vm) {
        onDispose { vm.setVisibleCombos(emptySet()) }
    }
    ComboListContent(
        state.combos, onOpenBuilder, onCreateCombo, vm::delete, vm::export,
        galleryScale, vm.galleryScale::change, vm.galleryScale::commit, vm.galleryScale::reset,
        state.exportingComboId, state.exportMessage,
        gridState,
    ) { combo, modifier ->
        StaticPreview(state, combo, modifier)
    }
}

/** Чистая композиция списка: загрузка ресурсов и отрисовка превью принадлежат экрану. */
@Composable
internal fun ComboListContent(
    combos: List<ComboWithItems>,
    onOpenBuilder: (String) -> Unit,
    onCreateCombo: () -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    galleryScale: GalleryScaleUiState = GalleryScaleUiState(loaded = true),
    onScaleChange: (Float) -> Unit = {},
    onScaleFinished: () -> Unit = {},
    onScaleReset: () -> Unit = {},
    exportingComboId: String? = null,
    exportMessage: String? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    preview: @Composable (ComboWithItems, Modifier) -> Unit,
) {
    PcSurface(Modifier.fillMaxSize().testTag("combo_list"), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize()) {
            PcSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(PcTheme.spacing.lg)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PcText("Combos", Modifier.weight(1f), style = PcTheme.typography.title)
                        PcButton("New combo", onCreateCombo, Modifier.testTag(NEW_COMBO_TAG),
                            variant = PcButtonVariant.Primary, icon = PcIcons.Add)
                    }
                    GallerySizeControl(galleryScale, onScaleChange, onScaleFinished, onScaleReset)
                    val status = if (exportingComboId != null) "Exporting PNG…" else exportMessage
                    if (status != null) {
                        PcText(
                            status,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = PcTheme.typography.label,
                        )
                    }
                }
            }
            PcDivider()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val inset = if (maxWidth >= 600.dp) PcTheme.spacing.xl else PcTheme.spacing.lg
                    val gap = PcTheme.spacing.lg
                    if (combos.isEmpty()) {
                        Column(Modifier.padding(inset), verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                            PcText("No combos yet", style = PcTheme.typography.title)
                            PcText("Create a combo to arrange your photos.", color = PcTheme.colors.textSecondary)
                        }
                    } else {
                        GalleryGrid(
                            items = combos,
                            key = { it.combo.id },
                            referenceItemWidth = (240f * galleryScale.percent / 100f).dp,
                            previewAspectRatio = { it.combo.canvasWidth.toFloat() / it.combo.canvasHeight },
                            modifier = Modifier.fillMaxSize().testTag("combo_grid"),
                            state = gridState,
                            contentPadding = PaddingValues(inset),
                            horizontalGap = gap,
                            verticalGap = PcTheme.spacing.xl,
                        ) { combo, geometry ->
                            Column(Modifier.fillMaxWidth().testTag("combo_card_${combo.combo.id}")) {
                                PcSurface(
                                    Modifier.fillMaxWidth().height(geometry.previewHeight)
                                        .clip(PcTheme.shapes.small)
                                        .clickable(role = Role.Button) { onOpenBuilder(combo.combo.id) }
                                        .semantics { contentDescription = "Edit ${combo.combo.name}" }
                                        .testTag("combo_preview_${combo.combo.id}"),
                                    color = PcTheme.colors.workspace,
                                ) {
                                    preview(combo, Modifier.fillMaxSize())
                                }
                                ComboDetails(combo, onOpenBuilder, onDelete, onExport, exportingComboId)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboDetails(
    combo: ComboWithItems,
    onOpenBuilder: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    exportingComboId: String?,
) {
    // Подпись и инструменты могут занять две строки при крупном шрифте, оставаясь доступными.
    FlowRow(
        Modifier.fillMaxWidth().padding(top = PcTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PcText("${combo.combo.canvasWidth} × ${combo.combo.canvasHeight}",
            color = PcTheme.colors.textSecondary, style = PcTheme.typography.label)
        PcSurface(shape = PcTheme.shapes.control, borderColor = PcTheme.colors.border,
            borderWidth = PcTheme.strokes.border) {
            Row(Modifier.padding(1.dp)) {
                PcIconButton(PcIcons.Edit, "Edit", { onOpenBuilder(combo.combo.id) },
                    Modifier.testTag("combo_edit_${combo.combo.id}"))
                PcIconButton(PcIcons.Export, "Export PNG", { onExport(combo.combo.id) },
                    Modifier.testTag("combo_export_${combo.combo.id}"), enabled = exportingComboId == null)
                PcIconButton(PcIcons.Delete, "Delete", { onDelete(combo.combo.id) },
                    Modifier.testTag("combo_delete_${combo.combo.id}"), enabled = exportingComboId != combo.combo.id)
            }
        }
    }
}

@Composable
private fun StaticPreview(state: ComboListViewModel.State, combo: ComboWithItems, modifier: Modifier) {
    val frames = remember(combo.items, state.images, state.photos) { state.framesFor(combo) }
    val canvasW = combo.combo.canvasWidth.toFloat()
    val canvasH = combo.combo.canvasHeight.toFloat()
    val bg = remember(combo, state.photos) {
        comboBackgroundOf(
            combo.combo.canvasWidth,
            combo.combo.canvasHeight,
            combo.combo.background,
            combo.backgroundFrames(state.photos),
        )
    }
    Canvas(modifier) {
        val t = CanvasTransform.fitInto(canvasW, canvasH, size.width, size.height)
        // Тот же единый вызов, что в редакторе и плеере: вписывающий сдвиг считается
        // здесь, а фон и рамки рисует drawCombo. `bg` и `frames` пришли готовыми из
        // remember выше — их ключи не трогаем.
        drawCombo(t, canvasW, canvasH, bg, frames)
    }
}
