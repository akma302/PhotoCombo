package com.combo.feature.combo_list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    ComboListContent(state.combos, onOpenBuilder, onCreateCombo, vm::delete) { combo, modifier ->
        StaticPreview(vm, combo, modifier)
    }
}

/** Чистая композиция списка: загрузка ресурсов и отрисовка превью принадлежат экрану. */
@Composable
internal fun ComboListContent(
    combos: List<ComboWithItems>,
    onOpenBuilder: (String) -> Unit,
    onCreateCombo: () -> Unit,
    onDelete: (String) -> Unit,
    preview: @Composable (ComboWithItems, Modifier) -> Unit,
) {
    PcSurface(Modifier.fillMaxSize().testTag("combo_list"), color = PcTheme.colors.app) {
        Column(Modifier.fillMaxSize()) {
            PcSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(PcTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PcText("Combos", Modifier.weight(1f), style = PcTheme.typography.title)
                    PcButton("New combo", onCreateCombo, Modifier.testTag(NEW_COMBO_TAG),
                        variant = PcButtonVariant.Primary, icon = PcIcons.Add)
                }
            }
            PcDivider()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxSize()) {
                    val inset = if (maxWidth >= 600.dp) PcTheme.spacing.xl else PcTheme.spacing.lg
                    val gap = PcTheme.spacing.lg
                    val columns = ((maxWidth - inset * 2 + gap) / (176.dp + gap)).toInt().coerceIn(1, 3)
                    val cellWidth = (maxWidth - inset * 2 - gap * (columns - 1)) / columns
                    // Все превью в строке имеют высоту самого высокого холста при общей ширине.
                    val rowAspects = remember(combos, columns) {
                        combos.chunked(columns).map { row ->
                            row.maxOf { it.combo.canvasHeight.toFloat() / it.combo.canvasWidth }
                        }
                    }
                    if (combos.isEmpty()) {
                        Column(Modifier.padding(inset), verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                            PcText("No combos yet", style = PcTheme.typography.title)
                            PcText("Create a combo to arrange your photos.", color = PcTheme.colors.textSecondary)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize().testTag("combo_grid"),
                            contentPadding = PaddingValues(inset),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.xl),
                        ) {
                            itemsIndexed(combos, key = { _, combo -> combo.combo.id }) { index, combo ->
                                Column(Modifier.fillMaxWidth().testTag("combo_card_${combo.combo.id}")) {
                                    PcSurface(
                                        Modifier.fillMaxWidth().height(cellWidth * rowAspects[index / columns])
                                            .clip(PcTheme.shapes.small)
                                            .clickable(role = Role.Button) { onOpenBuilder(combo.combo.id) }
                                            .semantics { contentDescription = "Edit ${combo.combo.name}" }
                                            .testTag("combo_preview_${combo.combo.id}"),
                                        color = PcTheme.colors.workspace,
                                    ) {
                                        preview(combo, Modifier.fillMaxSize())
                                    }
                                    ComboDetails(combo, onOpenBuilder, onDelete)
                                }
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
                PcIconButton(PcIcons.Delete, "Delete", { onDelete(combo.combo.id) },
                    Modifier.testTag("combo_delete_${combo.combo.id}"))
            }
        }
    }
}

@Composable
private fun StaticPreview(vm: ComboListViewModel, combo: ComboWithItems, modifier: Modifier) {
    val state by vm.state.collectAsState()
    LaunchedEffect(combo.combo.id, combo.combo.updatedAt) {
        vm.ensureAssetsFor(combo)
    }
    val frames = rememberFrames(vm, combo, state.images.size + state.photos.size)
    val canvasW = combo.combo.canvasWidth.toFloat()
    val canvasH = combo.combo.canvasHeight.toFloat()
    // Фон такой же, как в билдере. Ключи remember должны покрывать всё, от чего
    // зависит результат: у Auto-фона это ещё и положение с размером рамок, поэтому
    // одних `photos.size` и спецификации мало, и в ключ добавлен `updatedAt` (он
    // меняется при любой правке комбо).
    val bg = remember(combo.combo.updatedAt, combo.combo.background, state.photos.size) {
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

@Composable
private fun rememberFrames(
    vm: ComboListViewModel,
    combo: ComboWithItems,
    key: Int,
): List<ItemFrame> = remember(combo.combo.id, combo.combo.updatedAt, key) {
    vm.framesFor(combo)
}
