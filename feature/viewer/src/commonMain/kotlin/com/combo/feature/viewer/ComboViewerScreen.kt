package com.combo.feature.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.combo.ui.common.SavedComboPreview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComboViewerScreen(
    comboId: String,
    onBack: () -> Unit,
    vm: ComboViewerViewModel = koinViewModel(),
) {
    val combos by vm.combos.collectAsState()
    val photos by vm.photos.collectAsState()
    val images by vm.images.collectAsState()
    ViewerPager(
        initialId = comboId,
        pages = combos.orEmpty(),
        pageId = { it.combo.id },
        title = { it.combo.name },
        tag = "combo_viewer",
        onBack = onBack,
        onPageChanged = vm::ensureLoaded,
        emptyMessage = if (combos == null) "Loading combos…" else "No combos to view",
    ) { combo, modifier ->
        SavedComboPreview(combo, photos, images, modifier)
    }
}
