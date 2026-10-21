package com.combo.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.combo.ui.common.RegisterBackHandler
import com.combo.ui.common.CollectMessages
import com.combo.ui.common.PhotoGrid
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** testTag ячейки сетки: `PHOTO_CELL_TAG_PREFIX + photo.id` — по нему UI-тест кликает по фото. */
const val PHOTO_CELL_TAG_PREFIX = "library_photo_"

/** testTag'ы действий мультивыбора в верхней панели. */
const val LIB_SELECT_TAG = "library_action_select"
const val LIB_CANCEL_TAG = "library_action_cancel"
const val LIB_DELETE_TAG = "library_action_delete"
const val LIB_CREATE_COMBO_TAG = "library_action_create_combo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenViewer: (String) -> Unit,
    onCreateCombo: (List<String>) -> Unit,
    vm: LibraryViewModel = koinViewModel(),
) {
    val photos by vm.photos.collectAsState()
    val selected by vm.selected.collectAsState()
    val selectionMode by vm.selectionMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectMessages(vm.messages, snackbar)

    // Режим выбора — состояние экрана и сам не выключается, даже когда выбор пуст
    // (см. LibraryViewModel). Поэтому «назад» в этом режиме выходит из него, а не уводит
    // с экрана: иначе после снятия последней галочки из режима нечем было бы выйти.
    RegisterBackHandler(enabled = selectionMode) { vm.exitSelectionMode() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // «Отмена» — в слоте навигации, а не внутри заголовка: заголовок с
                    // кнопкой занимал всю ширину и выдавливал действия за край экрана.
                    // Общая `BackNavIcon` здесь не годится: у отмены своя иконка, своё
                    // описание и свой testTag, и она занимает слот *вместо* стрелки.
                    if (selectionMode) {
                        IconButton(
                            modifier = Modifier.testTag(LIB_CANCEL_TAG),
                            onClick = { vm.exitSelectionMode() },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                title = {
                    Text(if (selectionMode) "Selected: ${selected.size}" else "Library")
                },
                actions = {
                    when {
                        selectionMode -> {
                            if (selected.isNotEmpty()) {
                                IconButton(
                                    modifier = Modifier.testTag(LIB_DELETE_TAG),
                                    onClick = { vm.deleteSelected() },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                                }
                                TextButton(
                                    enabled = selected.isNotEmpty(),
                                    modifier = Modifier.testTag(LIB_CREATE_COMBO_TAG),
                                    onClick = {
                                        // Список и предупреждение об обрезке — из VM:
                                        // на экране их собирать негде.
                                        scope.launch {
                                            onCreateCombo(vm.photosForNewCombo())
                                        }
                                    },
                                ) { Text("Create combo") }
                            }
                        }
                        else -> TextButton(
                            modifier = Modifier.testTag(LIB_SELECT_TAG),
                            onClick = { vm.enterSelectionMode() },
                        ) { Text("Select") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Диалог выбора файлов показывает сам ViewModel через ImagePicker: экран
            // больше не держит launcher, а импорт остаётся одним действием «Import».
            ExtendedFloatingActionButton(onClick = { vm.import() }) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Text("  Import")
            }
        },
    ) { padding ->
        // Три колонки и зазоры 2.dp — единственное, чем библиотека отличается от значений
        // общей сетки по умолчанию. `cellPadding` и `cellHeight` не задаются, поэтому ячейка
        // квадратная (по aspectRatio) и без полей.
        PhotoGrid(
            photos = photos,
            onCellClick = { photo ->
                if (selectionMode) vm.toggle(photo.id) else onOpenViewer(photo.id)
            },
            cellTag = { PHOTO_CELL_TAG_PREFIX + it.id },
            modifier = Modifier.fillMaxSize().padding(padding),
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            isSelected = { it.id in selected },
            // Долгое нажатие — вход в мультивыбор: обычный тап открывает просмотр,
            // и добраться до «выбрать несколько» иначе нельзя. Сначала включаем режим,
            // потом переключаем выбор — иначе `toggle` снял бы выделение обратно.
            onCellLongClick = { photo ->
                vm.enterSelectionMode()
                vm.toggle(photo.id)
            },
        )
    }
}
