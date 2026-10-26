package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.combo.ui.common.designsystem.primitives.PcControlLayout
import com.combo.ui.common.designsystem.primitives.PcIcon
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Доступное имя одновременно служит подсказкой; сам вектор остаётся декоративным. */
@Composable
fun PcIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(contentDescription.isNotBlank()) { "PcIconButton requires an accessible name" }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = PcTheme.colors
    val background = when {
        !enabled -> Color.Transparent
        pressed -> colors.pressed
        hovered -> colors.hover
        else -> Color.Transparent
    }
    PcTooltip(contentDescription, enabled = enabled) {
        PcControlLayout(
            modifier.pcControlSurface(source, background, enabled)
                .clickable(interactionSource = source, indication = null, enabled = enabled,
                    role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
            minimumWidth = maxOf(PcTheme.sizes.iconButton, PcTheme.minimumTarget),
        ) {
            PcIcon(imageVector)
        }
    }
}
