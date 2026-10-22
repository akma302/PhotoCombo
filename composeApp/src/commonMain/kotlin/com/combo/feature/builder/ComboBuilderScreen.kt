package com.combo.feature.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var pickMode by remember { mutableStateOf(BuilderPickMode.NONE) }

    fun requestExit() {
        if (vm.state.value.isDirty) showExitDialog = true else onBack()
    }
    LaunchedEffect(Unit) { launch { vm.saved.collect { onBack() } } }
    CollectMessages(vm.messages, snackbar)
    RegisterBackHandler(enabled = state.loaded) { requestExit() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= BUILDER_WIDE_MIN
        Scaffold(
            topBar = {
                Column {
                    BuilderTopBar(state, wide, ::requestExit, vm::onEvent)
                    HorizontalDivider()
                    BuilderCanvasToolbar(state, vm::onEvent) { pickMode = BuilderPickMode.ADD }
                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    HorizontalDivider()
                    BuilderViewControls(state, vm::onEvent)
                }
            },
        ) { padding ->
            if (!state.loaded) {
                Box(Modifier.fillMaxSize().padding(padding))
            } else {
                BuilderWorkspace(
                    wide = wide,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    canvas = { EditorViewport(vm, state, bitmaps) },
                    inspector = { BuilderInspector(state, wide, vm::onEvent) { pickMode = BuilderPickMode.REPLACE } },
                )
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
