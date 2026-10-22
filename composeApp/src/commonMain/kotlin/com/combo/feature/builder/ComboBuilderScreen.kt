package com.combo.feature.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.combo.ui.common.designsystem.primitives.PcDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.combo.ui.common.designsystem.primitives.PcSurface
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.components.PcIconButton
import com.combo.ui.common.designsystem.icons.PcIcons
import com.combo.ui.common.designsystem.theme.PcTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.combo.ui.common.CollectMessages
import com.combo.ui.common.RegisterBackHandler
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Адаптивный редактор: настройки холста сверху, инспектор рамки справа или снизу,
 * управление видом отдельно. Состояние диалогов живёт вне изменяемой разметки.
 */
@Composable
fun ComboBuilderScreen(
    onBack: () -> Unit,
    vm: ComboBuilderViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val bitmaps by vm.bitmaps.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }
    var inspectorExpanded by remember { mutableStateOf(true) }
    var pickMode by remember { mutableStateOf(BuilderPickMode.NONE) }

    fun requestExit() {
        if (vm.state.value.isDirty) showExitDialog = true else onBack()
    }
    LaunchedEffect(Unit) { launch { vm.saved.collect { onBack() } } }
    CollectMessages(vm.messages, snackbar)
    RegisterBackHandler(enabled = state.loaded) { requestExit() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= BUILDER_WIDE_MIN
        PcSurface(Modifier.fillMaxSize(), color = PcTheme.colors.app) {
            Column(Modifier.fillMaxSize()) {
                BuilderTopBar(state, wide, ::requestExit, vm::onEvent)
                PcDivider()
                BuilderCanvasToolbar(state, vm::onEvent) { pickMode = BuilderPickMode.ADD }
                PcDivider()
                Box(Modifier.weight(1f)) {
                    if (state.loaded) {
                        BuilderWorkspace(
                            wide = wide,
                            inspectorExpanded = inspectorExpanded,
                            modifier = Modifier.fillMaxSize(),
                            canvas = { EditorViewport(vm, state, bitmaps, Modifier.padding(if (wide) 32.dp else 20.dp)) },
                            inspector = { BuilderInspector(state, wide, vm::onEvent) { pickMode = BuilderPickMode.REPLACE } },
                        )
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { message ->
                        PcSurface(Modifier.padding(PcTheme.spacing.md), shape = PcTheme.shapes.control) {
                            Row(Modifier.padding(PcTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PcTheme.spacing.sm)) {
                                PcText(message.visuals.message, Modifier.weight(1f))
                                PcIconButton(PcIcons.Close, "Dismiss message", { message.dismiss() })
                            }
                        }
                    }
                }
                PcDivider()
                BuilderViewControls(state, vm::onEvent, if (wide) ({ inspectorExpanded = !inspectorExpanded }) else null, inspectorExpanded)
            }
        }
    }
    BuilderPhotoPicker(pickMode, state, vm::onEvent) { pickMode = BuilderPickMode.NONE }
    if (showExitDialog) {
        ExitDialog(
            state = state,
            onSave = { vm.onEvent(BuilderEvent.Save); showExitDialog = false },
            onDiscard = { showExitDialog = false; onBack() },
            onKeepEditing = { showExitDialog = false },
        )
    }
}
