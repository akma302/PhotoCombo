package com.combo.feature.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.combo.ui.common.BackNavIcon
import com.combo.ui.common.CollectMessages
import kotlinx.coroutines.launch

/**
 * Каркас редактора комбо: Scaffold, топбар, тулбар, snackbar, выход с подтверждением.
 *
 * Всё содержимое вынесено по файлам: [EditorViewport] — холст, [BuilderToolbar] — нижняя
 * полоса, [ExitDialog] — окно выхода, контракт (события, состояние, testTag'и) — в
 * [BuilderContract]. Здесь остаётся только то, что держит вместе экран: разметка, обработка
 * «назад» и решение, показывать ли диалог подтверждения.
 *
 * @param vm передаётся явно ради тестов (`createComposeRule` + фейковые репозитории);
 *   в проде берётся из Hilt по маршруту.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboBuilderScreen(
    onBack: () -> Unit,
    vm: ComboBuilderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bitmaps by vm.bitmaps.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showExitDialog by remember { mutableStateOf(false) }

    // Системная кнопка «назад» и стрелка в топбаре ведут в один и тот же обработчик,
    // иначе стрелка молча выходила бы, теряя правки, а «назад» спрашивал бы.
    fun requestExit() {
        if (vm.state.value.isDirty) showExitDialog = true else onBack()
    }

    LaunchedEffect(Unit) {
        launch { vm.saved.collect { onBack() } }
    }

    CollectMessages(vm.messages, snackbar)

    BackHandler(enabled = state.loaded) { requestExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackNavIcon { requestExit() } },
                title = {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { vm.onEvent(BuilderEvent.SetName(it)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                },
                actions = {
                    IconButton(enabled = state.canUndo, onClick = { vm.onEvent(BuilderEvent.Undo) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(enabled = state.canRedo, onClick = { vm.onEvent(BuilderEvent.Redo) }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    // Проверку «есть ли что сохранять» убрали в VM: иначе кнопка была бы
                    // мёртвой и сообщение «Add at least one photo» было бы негде показать.
                    TextButton(
                        enabled = state.loaded && !state.saving && state.isDirty,
                        onClick = { vm.onEvent(BuilderEvent.Save) },
                        modifier = Modifier.testTag(TOOL_TAG_SAVE),
                    ) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BuilderToolbar(vm, state) },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        EditorViewport(vm, state, bitmaps, Modifier.padding(padding))
    }

    if (showExitDialog) {
        ExitDialog(
            state = state,
            onSave = {
                // Выход произойдёт по сигналу `saved`; если сохранять нечего,
                // VM покажет сообщение и редактор останется открытым.
                vm.onEvent(BuilderEvent.Save)
                showExitDialog = false
            },
            onDiscard = {
                showExitDialog = false
                onBack()
            },
            onKeepEditing = { showExitDialog = false },
        )
    }
}