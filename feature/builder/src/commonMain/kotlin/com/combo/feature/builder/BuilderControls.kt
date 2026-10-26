package com.combo.feature.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.combo.ui.common.designsystem.components.PcIconButton

/** Подсказку, доступное имя и область ввода обеспечивает общий компонент. */
@Composable
internal fun BuilderIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PcIconButton(icon, label, onClick, modifier, enabled)
}
