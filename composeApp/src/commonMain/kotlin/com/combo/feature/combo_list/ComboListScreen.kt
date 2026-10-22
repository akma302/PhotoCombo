package com.combo.feature.combo_list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth >= 600.dp
                LazyColumn(
                    Modifier.widthIn(max = 1040.dp).fillMaxSize(),
                    contentPadding = PaddingValues(if (wide) PcTheme.spacing.xl else PcTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.md),
                ) {
                    if (combos.isEmpty()) {
                        item(key = "empty") {
                            PcSurface(Modifier.fillMaxWidth(), shape = PcTheme.shapes.group,
                                borderColor = PcTheme.colors.border, borderWidth = PcTheme.strokes.border) {
                                Column(Modifier.padding(PcTheme.spacing.xl),
                                    verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                                    PcText("No combos yet", style = PcTheme.typography.title)
                                    PcText("Create a combo to arrange your photos.", color = PcTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                    items(combos.size, key = { combos[it].combo.id }) { index ->
                        val combo = combos[index]
                        PcSurface(Modifier.fillMaxWidth().testTag("combo_card_${combo.combo.id}"),
                            shape = PcTheme.shapes.group, borderColor = PcTheme.colors.border,
                            borderWidth = PcTheme.strokes.border) {
                            if (wide) {
                                Row(Modifier.padding(PcTheme.spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    ComboPreview(combo, Modifier.width(260.dp), preview)
                                    ComboDetails(combo, onOpenBuilder, onDelete, Modifier.weight(1f))
                                }
                            } else {
                                Column(Modifier.padding(PcTheme.spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.md)) {
                                    ComboPreview(combo, Modifier.fillMaxWidth(), preview)
                                    ComboDetails(combo, onOpenBuilder, onDelete, Modifier.fillMaxWidth())
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
private fun ComboPreview(
    combo: ComboWithItems,
    modifier: Modifier,
    preview: @Composable (ComboWithItems, Modifier) -> Unit,
) {
    PcSurface(modifier.aspectRatio(
        combo.combo.canvasWidth.toFloat() / combo.combo.canvasHeight),
        color = PcTheme.colors.workspace, shape = PcTheme.shapes.small) {
        preview(combo, Modifier.fillMaxSize().clip(PcTheme.shapes.small))
    }
}

@Composable
private fun ComboDetails(
    combo: ComboWithItems,
    onOpenBuilder: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
        PcText(combo.combo.name, style = PcTheme.typography.title)
        PcText("${combo.combo.canvasWidth} × ${combo.combo.canvasHeight}",
            color = PcTheme.colors.textSecondary, style = PcTheme.typography.caption)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PcIconButton(PcIcons.Delete, "Delete", { onDelete(combo.combo.id) },
                Modifier.testTag("combo_delete_${combo.combo.id}"))
            PcIconButton(PcIcons.Edit, "Edit", { onOpenBuilder(combo.combo.id) },
                Modifier.testTag("combo_edit_${combo.combo.id}"))
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
