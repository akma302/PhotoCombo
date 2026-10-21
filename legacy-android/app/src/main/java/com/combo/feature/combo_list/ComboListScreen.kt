package com.combo.feature.combo_list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.combo.core.render.CanvasTransform
import com.combo.core.render.ItemFrame
import com.combo.core.render.comboBackgroundOf
import com.combo.core.render.drawCombo
import com.combo.data.ComboWithItems
import com.combo.data.backgroundFrames

/** testTag кнопки «New combo» — единственный вход в builder из секции Combos. */
const val NEW_COMBO_TAG = "combo_list_new_combo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboListScreen(
    onOpenBuilder: (String) -> Unit,
    onCreateCombo: () -> Unit,
    onPlay: (String) -> Unit,
    vm: ComboListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Combos") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateCombo,
                modifier = Modifier.testTag(NEW_COMBO_TAG),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New combo")
                Text("  New combo")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.combos.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No combos yet",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(count = state.combos.size, key = { state.combos[it].combo.id }) { i ->
                val cw = state.combos[i]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(cw.combo.name, style = MaterialTheme.typography.titleMedium)
                        StaticPreview(vm, cw, Modifier.fillMaxWidth().aspectRatio(
                            (cw.combo.canvasWidth.toFloat() / cw.combo.canvasHeight).coerceIn(0.4f, 2.5f)
                        ))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { vm.delete(cw.combo.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            IconButton(onClick = { onOpenBuilder(cw.combo.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onPlay(cw.combo.id) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticPreview(vm: ComboListViewModel, combo: ComboWithItems, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(combo.combo.id, combo.combo.updatedAt) {
        vm.ensureAssetsFor(combo)
    }
    val frames = rememberFrames(vm, combo, state.images.size + state.photos.size)
    val canvasW = combo.combo.canvasWidth.toFloat()
    val canvasH = combo.combo.canvasHeight.toFloat()
    // Фон такой же, как в билдере и плеере. Ключи remember должны покрывать всё, от чего
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
